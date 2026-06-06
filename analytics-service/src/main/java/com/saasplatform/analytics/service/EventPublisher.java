package com.saasplatform.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import com.saasplatform.context.RequestContextHolder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {
    private final EventBridgeClient eventBridgeClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.eventbridge.bus-name:default}")
    private String eventBusName;

    public void publishQueryCompleted(String reportId) {
        String tenantId = RequestContextHolder.getCurrentTenantId();
        String correlationId = RequestContextHolder.getCurrentCorrelationId();

        Map<String, Object> detail = new HashMap<>();
        detail.put("tenantId", tenantId);
        detail.put("reportId", reportId);
        detail.put("timestamp", LocalDateTime.now());
        detail.put("correlationId", correlationId);

        try {
            String detailJson = objectMapper.writeValueAsString(detail);

            PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                    .source("analytics-service")
                    .detailType("QueryCompleted")
                    .detail(detailJson)
                    .build();

            PutEventsRequest request = PutEventsRequest.builder()
                    .entries(entry)
                    .eventBusName(eventBusName)
                    .build();

            PutEventsResponse response = eventBridgeClient.putEvents(request);
            log.info("Event published: tenant={}, reportId={}, failedEntries={}", tenantId, reportId, response.failedEntryCount());
        } catch (Exception e) {
            log.error("Failed to publish event for tenant: {}", tenantId, e);
        }
    }
}
