package click.yinsb.config;

import click.yinsb.activities.TravelActivitiesImpl;
import click.yinsb.workflow.TravelWorkflowImpl;
import com.google.common.collect.ImmutableMap;
import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import com.uber.m3.tally.StatsReporter;
import io.micrometer.core.instrument.MeterRegistry;
import io.temporal.client.WorkflowClient;
import io.temporal.common.reporter.MicrometerClientStatsReporter;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {

    /**
     * Creates and configures a WorkerFactory for Temporal workflows.
     * Registers the TravelWorkflow and its activities to the specified task queue.
     *
     * @param serviceStubs Temporal service stubs for communication with the Temporal service
     * @return Configured WorkerFactory instance
     */
    @Bean
    public WorkerFactory workerFactory(WorkflowServiceStubs serviceStubs) {
        WorkflowClient client = WorkflowClient.newInstance(serviceStubs);
        WorkerFactory factory = WorkerFactory.newInstance(client);

        Worker worker = factory.newWorker("TRAVEL_TASK_QUEUE");
        worker.registerWorkflowImplementationTypes(TravelWorkflowImpl.class);
        worker.registerActivitiesImplementations(new TravelActivitiesImpl());

        factory.start();
        return factory;
    }

    /**
     * Provides a WorkflowServiceStubs bean for connecting to the Temporal service.
     * Connect Temporal SDK metrics to Spring's global MeterRegistry so
     * counters recorded inside workflows/activities appear on /actuator/prometheus.
     *
     * @return WorkflowServiceStubs instance
     */
    @Bean
    public WorkflowServiceStubs serviceStubs(MeterRegistry registry) {

        // see the Micrometer documentation for configuration details on other supported monitoring systems.
        // in this example shows how to set up Prometheus registry and stats reported.
        StatsReporter reporter = new MicrometerClientStatsReporter(registry);
        // set up a new scope, report every 10 seconds
        Scope scope = new RootScopeBuilder()
                .tags(
                        ImmutableMap.of(
                                "source",
                                "spring-temporal"))
                .reporter(reporter)
                .reportEvery(com.uber.m3.util.Duration.ofSeconds(10));
        // for Prometheus collection, expose a scrape endpoint.
        //...
        // add metrics scope to WorkflowServiceStub options
        WorkflowServiceStubsOptions stubOptions =
                WorkflowServiceStubsOptions.newBuilder().setMetricsScope(scope).build();

        return WorkflowServiceStubs.newServiceStubs(stubOptions);
    }
}
