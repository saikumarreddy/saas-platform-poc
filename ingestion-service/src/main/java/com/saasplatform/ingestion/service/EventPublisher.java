package com.saasplatform.ingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
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

    public void publishFileUploaded(String fileId, String fileName) {
        String tenantId = RequestContextHolder.getCurrentTenantId();
        String correlationId = RequestContextHolder.getCurrentCorrelationId();

        Map<String, Object> detail = new HashMap<>();
        detail.put("tenantId", tenantId);
        detail.put("fileId", fileId);
        detail.put("fileName", fileName);
        detail.put("timestamp", LocalDateTime.now());
        detail.put("correlationId", correlationId);

        try {
            String detailJson = objectMapper.writeValueAsString(detail);

            PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                    .source("ingestion-service")
                    .detailType("FileUploaded")
                    .detail(detailJson)
                    .build();

            PutEventsRequest request = PutEventsRequest.builder()
                    .entries(entry)
                    .eventBusName(eventBusName)
                    .build();

            eventBridgeClient.putEvents(request);
            log.info("File uploaded event published: tenant={}, fileId={}", tenantId, fileId);
        } catch (Exception e) {
            log.error("Failed to publish event for tenant: {}", tenantId, e);
        }
    }
}
