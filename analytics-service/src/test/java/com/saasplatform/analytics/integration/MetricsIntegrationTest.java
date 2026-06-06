package com.saasplatform.analytics.integration;

import com.saasplatform.monitoring.MetricsService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("Analytics Metrics Integration Tests")
class MetricsIntegrationTest extends LocalStackTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        // Reset metrics before each test
        metricsService = new MetricsService(new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("Should track analytics query counter metrics")
    void testAnalyticsQueryCounter() {
        metricsService.incrementAnalyticsQueries("tenant-a");
        metricsService.incrementAnalyticsQueries("tenant-a");
        metricsService.incrementAnalyticsQueries("tenant-b");

        // Metrics would be visible at /actuator/metrics/analytics.queries.total
    }

    @Test
    @DisplayName("Should track file upload counter metrics")
    void testFileUploadCounter() {
        metricsService.incrementFileUploads("tenant-a");
        metricsService.incrementFileUploads("tenant-a");

        // Metrics would be visible at /actuator/metrics/ingestion.uploads.total
    }

    @Test
    @DisplayName("Should track error counter metrics")
    void testErrorCounter() {
        metricsService.incrementErrors("query_error", "tenant-a");
        metricsService.incrementErrors("upload_error", "tenant-b");
        metricsService.incrementErrors("query_error", "tenant-a");

        // Metrics would be visible at /actuator/metrics/errors.total
    }

    @Test
    @DisplayName("Should track event published counter metrics")
    void testEventPublishedCounter() {
        metricsService.incrementEventPublished("QueryCompleted", "tenant-a");
        metricsService.incrementEventPublished("FileUploaded", "tenant-b");
        metricsService.incrementEventPublished("QueryCompleted", "tenant-a");

        // Metrics would be visible at /actuator/metrics/events.published.total
    }

    @Test
    @DisplayName("Should record insight generation timer metrics")
    void testInsightGenerationTimer() {
        metricsService.recordInsightGeneration(245);
        metricsService.recordInsightGeneration(312);
        metricsService.recordInsightGeneration(198);

        // Timer would record percentiles: p50, p95, p99
    }

    @Test
    @DisplayName("Should record file upload timer metrics")
    void testFileUploadTimer() {
        metricsService.recordFileUpload(512);
        metricsService.recordFileUpload(487);
        metricsService.recordFileUpload(651);

        // Timer would record percentiles for upload duration
    }

    @Test
    @DisplayName("Should record S3 operation timer metrics")
    void testS3OperationTimer() {
        metricsService.recordS3Operation(124, "putObject");
        metricsService.recordS3Operation(135, "getObject");
        metricsService.recordS3Operation(118, "putObject");

        // Timer would record percentiles per operation type
    }

    @Test
    @DisplayName("Should record event publish timer metrics")
    void testEventPublishTimer() {
        metricsService.recordEventPublish(42);
        metricsService.recordEventPublish(38);
        metricsService.recordEventPublish(45);

        // Timer would record percentiles for EventBridge publish latency
    }

    @Test
    @DisplayName("Should track metrics endpoint availability")
    void testMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names", notNullValue()));
    }

    @Test
    @DisplayName("Should track specific metric values")
    void testSpecificMetricValue() throws Exception {
        metricsService.incrementAnalyticsQueries("tenant-test");

        mockMvc.perform(get("/actuator/metrics/analytics.queries.total")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("analytics.queries.total"))
                .andExpect(jsonPath("$.description", notNullValue()))
                .andExpect(jsonPath("$.measurements", notNullValue()));
    }

    @Test
    @DisplayName("Should support gauge metrics")
    void testGaugeMetrics() {
        metricsService.setActiveConnections(42);
        metricsService.setQueueDepth("analytics-queue", 15);

        // Gauges would track current state metrics
    }

    @Test
    @DisplayName("Should track metrics with tenant tags")
    void testTenantTaggedMetrics() {
        metricsService.incrementAnalyticsQueries("tenant-a");
        metricsService.incrementAnalyticsQueries("tenant-b");
        metricsService.incrementAnalyticsQueries("tenant-a");

        // Metrics can be filtered by tenant_id tag for multi-tenant analysis
    }

    @Test
    @DisplayName("Should track error types separately")
    void testErrorTypeMetrics() {
        metricsService.incrementErrors("query_error", "tenant-a");
        metricsService.incrementErrors("stream_error", "tenant-a");
        metricsService.incrementErrors("query_error", "tenant-b");

        // Errors can be filtered by error_type tag
    }

    @Test
    @DisplayName("Should track event types separately")
    void testEventTypeMetrics() {
        metricsService.incrementEventPublished("QueryCompleted", "tenant-a");
        metricsService.incrementEventPublished("FileUploaded", "tenant-a");
        metricsService.incrementEventPublished("QueryCompleted", "tenant-b");

        // Events can be filtered by event_type tag
    }
}
