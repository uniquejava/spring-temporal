package click.yinsb.config;

import click.yinsb.activities.TravelActivities;
import click.yinsb.workflow.TravelWorkflowImpl;
import com.google.common.collect.ImmutableMap;
import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import com.uber.m3.tally.StatsReporter;
import io.jaegertracing.Configuration;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.reporter.MicrometerClientStatsReporter;
import io.temporal.opentracing.OpenTracingClientInterceptor;
import io.temporal.opentracing.OpenTracingOptions;
import io.temporal.opentracing.OpenTracingWorkerInterceptor;
import io.temporal.opentracing.codec.TextMapCodec;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerFactoryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@org.springframework.context.annotation.Configuration
public class TemporalConfig {

    /**
     * Creates and configures a WorkerFactory for Temporal workflows.
     * Registers the TravelWorkflow and its activities to the specified task queue.
     *
     * @param serviceStubs Temporal service stubs for communication with the Temporal service
     * @return Configured WorkerFactory instance
     */
    @Bean
    public WorkerFactory workerFactory(
            WorkflowServiceStubs serviceStubs,
            OpenTracingClientInterceptor clientInterceptor,
            OpenTracingWorkerInterceptor workerInterceptor,
            TravelActivities travelActivities) {
        WorkflowClientOptions clientOptions =
                WorkflowClientOptions.newBuilder()
                        .setInterceptors(clientInterceptor)
                        .build();
        WorkflowClient client = WorkflowClient.newInstance(serviceStubs, clientOptions);

        WorkerFactoryOptions workerOptions = WorkerFactoryOptions.newBuilder()
                .setWorkerInterceptors(workerInterceptor)
                .build();
        WorkerFactory factory = WorkerFactory.newInstance(client, workerOptions);

        Worker worker = factory.newWorker("TRAVEL_TASK_QUEUE");
        worker.registerWorkflowImplementationTypes(TravelWorkflowImpl.class);
        worker.registerActivitiesImplementations(travelActivities);

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

    @Bean
    public Tracer jaegerTracer(
            @Value("${spring.application.name:spring-temporal}") String serviceName,
            @Value("${temporal.tracing.jaeger.endpoint:http://localhost:14268/api/traces}")
            String jaegerEndpoint,
            @Value("${temporal.tracing.jaeger.sampler:const}") String samplerType,
            @Value("${temporal.tracing.jaeger.samplerParam:1}") double samplerParam) {
        Configuration.SamplerConfiguration samplerConfig =
                Configuration.SamplerConfiguration.fromEnv().withType(samplerType).withParam(samplerParam);
        Configuration.SenderConfiguration senderConfig =
                Configuration.SenderConfiguration.fromEnv().withEndpoint(jaegerEndpoint);
        Configuration.ReporterConfiguration reporterConfig =
                Configuration.ReporterConfiguration.fromEnv()
                        .withSender(senderConfig)
                        .withLogSpans(true);

        Tracer tracer =
                new Configuration(serviceName)
                        .withSampler(samplerConfig)
                        .withReporter(reporterConfig)
                        .getTracer();

        GlobalTracer.registerIfAbsent(tracer);
        return tracer;
    }

    @Bean
    public OpenTracingOptions openTracingOptions(Tracer tracer) {
        return OpenTracingOptions.newBuilder()
                .setTracer(tracer)
                .setSpanContextCodec(TextMapCodec.INSTANCE)
                .build();
    }

    @Bean
    public OpenTracingClientInterceptor openTracingClientInterceptor(OpenTracingOptions options) {
        return new OpenTracingClientInterceptor(options);
    }

    @Bean
    public OpenTracingWorkerInterceptor openTracingWorkerInterceptor(OpenTracingOptions options) {
        return new OpenTracingWorkerInterceptor(options);
    }
}
