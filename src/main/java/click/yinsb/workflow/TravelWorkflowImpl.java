package click.yinsb.workflow;

import click.yinsb.activities.TravelActivities;
import click.yinsb.dto.TravelRequest;
import com.uber.m3.tally.Scope;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Saga;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Service
@Slf4j
public class TravelWorkflowImpl implements TravelWorkflow {


    private boolean isUserConfirmed = false;

    @SignalMethod
    public void sendConfirmationSignal() {
        log.info("📩 Received user confirmation signal.");
        isUserConfirmed = true;
    }


    @Override
    public void bookTrip(TravelRequest travelRequest) {

        log.info("🚀 Starting travel booking for user: {}", travelRequest.getUserId());

        /*
         * Custom metric, we can use child scope and attach workflow_id as it's not attached by default
         * like task_queue ,workflow_type, etc
         */
        Scope scope =
                Workflow.getMetricsScope()
                        .tagged(Collections.singletonMap("workflow_id", Workflow.getInfo().getWorkflowId()));
        scope.counter("custom_metric").inc(1);

        TravelActivities activities = Workflow.newActivityStub(TravelActivities.class,
                ActivityOptions.newBuilder()
                        .setRetryOptions(RetryOptions.newBuilder()
                                .setMaximumAttempts(3)
                                .build())
                        .setStartToCloseTimeout(Duration.ofSeconds(10))
                        .build());

        Saga.Options sagaOptions = new Saga.Options.Builder()
                .setParallelCompensation(false)
                .build();

        Saga saga = new Saga(sagaOptions);

        try {

            activities.bookFlight(travelRequest);
            saga.addCompensation(() -> activities.cancelFlight(travelRequest));

            activities.bookHotel(travelRequest);
            saga.addCompensation(() -> activities.cancelHotel(travelRequest));

            activities.arrangeTransport(travelRequest);
            saga.addCompensation(() -> activities.cancelTransport(travelRequest));

            // 24 hours (1 day) -> wait for user confirmation if you won't
            // get any withing 24hr then cancel it

            log.info("⏳ Waiting for user confirmation for 2 min...");

            boolean isConfirmed = Workflow.await(
                    Duration.ofMinutes(2),
                    () -> isUserConfirmed
            );

            if (!isConfirmed) {
                log.info("🛑 User did not confirm within 2 minutes, cancelling the booking for user: {}", travelRequest.getUserId());
                //cancel the booking
                activities.cancelBooking(travelRequest);
            } else {
                log.info("✅ User confirmed the booking: {}", travelRequest.getUserId());
                //confirm the booking
                activities.confirmBooking(travelRequest);
            }


        } catch (Exception e) {
            log.error("❌ Error during travel booking for user: {}. Initiating compensation.", travelRequest.getUserId());
            saga.compensate();
        }

        log.info("✅ Travel booking completed for user: {}", travelRequest.getUserId());

    }
}
