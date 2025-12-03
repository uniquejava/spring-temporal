# spring-temporal
Trip Booking system demo 

## metrics
From Prometheus:

```shell
{source="spring-temporal"}
count by(__name__) ({source="spring-temporal"})
```

To surface workflow metrics in Actuator:

- Expose one shared Micrometer registry as a Spring bean, e.g.

```java
  @Bean
  PrometheusMeterRegistry prometheusRegistry() {
      return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
  }
```

  Spring Boot will automatically publish this registry at /actuator/prometheus.
- Inject that registry into TemporalConfig and hand it to MicrometerClientStatsReporter instead of building a registry locally. Every Temporal counter/gauge recorded through Workflow.getMetricsScope() or
  Activity.getMetricsScope() (after tagging) will then appear on the Actuator scrape endpoint.

If you want Spring-side custom metrics (not Temporal), inject the same MeterRegistry into your components and register counters/timers:

```java
@Service
class BookingMetrics {
private final Counter customMetric;

      BookingMetrics(MeterRegistry registry) {
          this.customMetric = Counter.builder("custom_metric")
              .description("Number of booking workflows started")
              .tag("workflow", "travel")
              .register(registry);
      }

      void recordStart() {
          customMetric.increment();
      }
}
```

Call `recordStart()` whenever the workflow kicks off. Because this counter uses the Actuator registry, it will show up immediately at /actuator/prometheus.

For Temporal-specific custom metrics (what https://docs.temporal.io/develop/java/observability describes):

1. Ensure your WorkflowServiceStubsOptions receives a Scope built from a stats reporter that your observability stack understands (Micrometer, OpenTelemetry, StatsD, etc.). The existing RootScopeBuilder plus
   MicrometerClientStatsReporter in TemporalConfig already does this.
2. Inside workflows use Workflow.getMetricsScope(); inside activities use Activity.getMetricsScope(). Example to add a gauge with tags:

   ```java
   Scope scope = Workflow.getMetricsScope()
   .tagged(Collections.singletonMap("workflow_id", Workflow.getInfo().getWorkflowId()));
   scope.timer("booking_duration").record(Duration.ofSeconds(5));
   ```
3. Temporal emits SDK/client metrics such as worker throughput automatically. Your custom counters/ timers piggyback on the same pipeline and will be exported by whatever reporter you configured.

So, wire the Prometheus registry as a Spring bean, share it with Temporal’s reporter, and use Micrometer MeterRegistry for any purely Spring metrics. Once everything points to the same registry, custom_metric (and any other
Temporal metrics) will be visible via Actuator.

## References

1. https://github.com/temporalio/samples-java/blob/637c2e66fd2dab43d9f3f39e5fd9c55e4f3884f0/core/src/main/java/io/temporal/samples/metrics/MetricsWorker.java
2. https://github.com/temporalio/samples-java/blob/637c2e66fd2dab43d9f3f39e5fd9c55e4f3884f0/core/src/main/java/io/temporal/samples/metrics/workflow/MetricsWorkflowImpl.java


