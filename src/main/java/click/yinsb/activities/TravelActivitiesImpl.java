package click.yinsb.activities;

import click.yinsb.dto.TravelRequest;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TravelActivitiesImpl implements TravelActivities {

    private final Tracer tracer;

    @Override
    public void bookFlight(TravelRequest travelRequest) {
        traceStep("bookFlight", travelRequest, () ->
                log.info("Flight booked for user: {} to destination: {} on date: {}",
                        travelRequest.getUserId(),
                        travelRequest.getDestination(),
                        travelRequest.getTravelDate()));
    }

    @Override
    public void cancelFlight(TravelRequest travelRequest) {
        traceStep("cancelFlight", travelRequest, () ->
                log.info("🛑 Cancelling flight for user {} because of failure",
                        travelRequest.getUserId()));
    }

    @Override
    public void bookHotel(TravelRequest travelRequest) {
        traceStep("bookHotel", travelRequest, () ->
                log.info("Hotel booked for user: {} at destination: {} on date: {}",
                        travelRequest.getUserId(),
                        travelRequest.getDestination(),
                        travelRequest.getTravelDate()));
    }

    @Override
    public void cancelHotel(TravelRequest travelRequest) {
        traceStep("cancelHotel", travelRequest, () ->
                log.info("🛑 Cancelling hotel for user {} because of failure",
                        travelRequest.getUserId()));
    }

    @Override
    public void arrangeTransport(TravelRequest travelRequest) {
        traceStep("arrangeTransport", travelRequest, () -> {
            log.info("Transport arranged for user: {} at destination: {} on date: {}",
                    travelRequest.getUserId(),
                    travelRequest.getDestination(),
                    travelRequest.getTravelDate());
            //simulate a failure to demonstrate compensation
            // throw new RuntimeException("Simulated transport arrangement failure!");
        });
    }

    @Override
    public void cancelTransport(TravelRequest travelRequest) {
        traceStep("cancelTransport", travelRequest, () ->
                log.info("🛑 Cancelling transport for user {}",
                        travelRequest.getUserId()));
    }

    @Override
    public void cancelBooking(TravelRequest travelRequest) {
        traceStep("cancelBooking", travelRequest, () ->
                log.info("Cancelling booking for user: {} at destination: {} on date: {}",
                        travelRequest.getUserId(),
                        travelRequest.getDestination(),
                        travelRequest.getTravelDate()));
    }

    @Override
    public void confirmBooking(TravelRequest travelRequest) {
        traceStep("confirmBooking", travelRequest, () ->
                log.info("Booking confirmed for user: {} at destination: {} on date: {}",
                        travelRequest.getUserId(),
                        travelRequest.getDestination(),
                        travelRequest.getTravelDate()));
    }

    private void traceStep(String spanName, TravelRequest request, Runnable action) {
        Span active = tracer.scopeManager().activeSpan();
        Tracer.SpanBuilder builder = tracer.buildSpan(spanName);
        if (active != null) {
            builder.asChildOf(active);
        }
        Span span = builder.start();
        span.setTag("travel.userId", request.getUserId());
        span.setTag("travel.destination", request.getDestination());
        span.setTag("travel.travelDate", request.getTravelDate());

        try (Scope ignored = tracer.scopeManager().activate(span)) {
            log.info("Tracing span [{}] started for user {}", spanName, request.getUserId());
            action.run();
        } catch (RuntimeException ex) {
            span.log("error: " + ex.getMessage());
            throw ex;
        } finally {
            span.finish();
        }
    }
}
