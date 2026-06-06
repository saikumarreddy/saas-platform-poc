package com.saasplatform.analytics.controller;

import com.saasplatform.analytics.service.EventPublisher;
import com.saasplatform.analytics.service.InsightGenerationService;
import com.saasplatform.analytics.service.InsightGenerationService.InsightReport;
import com.saasplatform.analytics.service.InsightGenerationService.Query;
import com.saasplatform.analytics.service.ReportService;
import com.saasplatform.context.RequestContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private final InsightGenerationService insightService;
    private final ReportService reportService;
    private final EventPublisher eventPublisher;

    @PostMapping("/query")
    public ResponseEntity<Map<String, String>> queryInsights(@RequestBody Query query) {
        String tenantId = RequestContextHolder.getCurrentTenantId();
        log.info("Analytics query received for tenant: {}, title: {}", tenantId, query.title());

        InsightReport report = insightService.generateInsights(query);
        String reportId = reportService.saveReport(report);
        String presignedUrl = reportService.getPresignedUrl(reportId);

        eventPublisher.publishQueryCompleted(reportId);

        Map<String, String> response = new HashMap<>();
        response.put("reportId", reportId);
        response.put("reportUrl", presignedUrl);
        response.put("tenantId", tenantId);

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamQueryInsights(@RequestBody Query query) {
        String tenantId = RequestContextHolder.getCurrentTenantId();
        log.info("Streaming analytics query received for tenant: {}, title: {}", tenantId, query.title());

        return insightService.streamInsights(query)
                .doOnNext(chunk -> log.debug("Streaming chunk for tenant: {}", tenantId))
                .doOnError(error -> log.error("Stream error for tenant: {}", tenantId, error));
    }

    @GetMapping("/reports/{reportId}")
    public ResponseEntity<Map<String, String>> getReport(@PathVariable String reportId) {
        String tenantId = RequestContextHolder.getCurrentTenantId();
        log.info("Report request for tenant: {}, reportId: {}", tenantId, reportId);

        String presignedUrl = reportService.getPresignedUrl(reportId);

        Map<String, String> response = new HashMap<>();
        response.put("reportId", reportId);
        response.put("reportUrl", presignedUrl);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "analytics-service");
        return ResponseEntity.ok(response);
    }
}
