package com.saasplatform.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {
    private final MeterRegistry meterRegistry;

    // Counters
    private Counter analyticsQueriesCounter;
    private Counter fileUploadsCounter;
    private Counter eventPublishedCounter;
    private Counter errorCounter;

    // Timers
    private Timer insightGenerationTimer;
    private Timer fileUploadTimer;
    private Timer s3OperationTimer;
    private Timer eventPublishTimer;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }

    private void initializeMetrics() {
        // Counters
        analyticsQueriesCounter = Counter.builder("analytics.queries.total")
                .description("Total number of analytics queries")
                .register(meterRegistry);

        fileUploadsCounter = Counter.builder("ingestion.uploads.total")
                .description("Total number of file uploads")
                .register(meterRegistry);

        eventPublishedCounter = Counter.builder("events.published.total")
                .description("Total number of events published to EventBridge")
                .register(meterRegistry);

        errorCounter = Counter.builder("errors.total")
                .description("Total number of errors")
                .register(meterRegistry);

        // Timers
        insightGenerationTimer = Timer.builder("analytics.insight.generation.duration")
                .description("Time to generate insights via AI")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        fileUploadTimer = Timer.builder("ingestion.file.upload.duration")
                .description("Time to upload file to S3")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        s3OperationTimer = Timer.builder("aws.s3.operation.duration")
                .description("Duration of S3 operations")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        eventPublishTimer = Timer.builder("eventbridge.publish.duration")
                .description("Time to publish event to EventBridge")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        log.info("Datadog metrics initialized");
    }

    // Counters
    public void incrementAnalyticsQueries(String tenantId) {
        analyticsQueriesCounter.increment();
        meterRegistry.counter("analytics.queries.by_tenant",
                "tenant_id", tenantId).increment();
    }

    public void incrementFileUploads(String tenantId) {
        fileUploadsCounter.increment();
        meterRegistry.counter("ingestion.uploads.by_tenant",
                "tenant_id", tenantId).increment();
    }

    public void incrementEventPublished(String eventType, String tenantId) {
        eventPublishedCounter.increment();
        meterRegistry.counter("events.published.by_type",
                "event_type", eventType,
                "tenant_id", tenantId).increment();
    }

    public void incrementErrors(String errorType, String tenantId) {
        errorCounter.increment();
        meterRegistry.counter("errors.by_type",
                "error_type", errorType,
                "tenant_id", tenantId).increment();
    }

    // Timers
    public void recordInsightGeneration(long durationMs) {
        insightGenerationTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordFileUpload(long durationMs) {
        fileUploadTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordS3Operation(long durationMs, String operation) {
        s3OperationTimer.record(durationMs, TimeUnit.MILLISECONDS);
        meterRegistry.timer("aws.s3.operation.by_type",
                "operation", operation).record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordEventPublish(long durationMs) {
        eventPublishTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    // Gauges (for current state metrics)
    public void setActiveConnections(int count) {
        meterRegistry.gauge("active.connections", count);
    }

    public void setQueueDepth(String queueName, int depth) {
        meterRegistry.gauge("queue.depth", "queue_name", queueName, depth);
    }
}
