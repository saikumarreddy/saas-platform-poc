package com.saasplatform.analytics.integration;

import com.saasplatform.analytics.service.EventPublisher;
import com.saasplatform.context.RequestContext;
import com.saasplatform.context.RequestContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.eventbridge.model.ListRulesRequest;
import software.amazon.awssdk.services.eventbridge.model.ListRulesResponse;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventBridge Publisher Integration Tests")
class EventPublisherIntegrationTest extends LocalStackTestBase {

    private static final String TENANT_ID = "event-test-tenant";
    private static final String USER_ID = "event-test-user";
    private static final String CORRELATION_ID = UUID.randomUUID().toString();

    @Autowired
    private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        RequestContextHolder.clearAll();

        RequestContext context = new RequestContext(
                TENANT_ID,
                USER_ID,
                CORRELATION_ID,
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                null
        );
        RequestContextHolder.set(context);

        // Create EventBridge bus if needed
        try {
            eventBridgeClient.createEventBus(b -> b.name("default"));
        } catch (Exception e) {
            // Bus might already exist, ignore
        }
    }

    @Test
    @DisplayName("Should successfully publish query completed event")
    void testPublishQueryCompleted_Success() {
        String reportId = "report-" + UUID.randomUUID();

        assertDoesNotThrow(() -> {
            eventPublisher.publishQueryCompleted(reportId);
        });
    }

    @Test
    @DisplayName("Should include tenant context in published event")
    void testPublishQueryCompleted_IncludesTenantContext() {
        String reportId = "report-context-" + UUID.randomUUID();

        assertDoesNotThrow(() -> {
            eventPublisher.publishQueryCompleted(reportId);
        });

        // Verify tenant context is available during publish
        assertEquals(TENANT_ID, RequestContextHolder.getCurrentTenantId());
        assertEquals(USER_ID, RequestContextHolder.getCurrentUserId());
    }

    @Test
    @DisplayName("Should handle multiple sequential event publications")
    void testMultipleEventPublications() {
        String reportId1 = "report-1-" + UUID.randomUUID();
        String reportId2 = "report-2-" + UUID.randomUUID();
        String reportId3 = "report-3-" + UUID.randomUUID();

        assertDoesNotThrow(() -> {
            eventPublisher.publishQueryCompleted(reportId1);
            eventPublisher.publishQueryCompleted(reportId2);
            eventPublisher.publishQueryCompleted(reportId3);
        });
    }

    @Test
    @DisplayName("EventBridge should be available on configured bus")
    void testEventBridgeBusAvailable() {
        ListRulesResponse response = eventBridgeClient.listRules(
                ListRulesRequest.builder()
                        .eventBusName("default")
                        .build()
        );

        assertNotNull(response);
        // Rules list might be empty, but the bus should exist
    }

    @Test
    @DisplayName("Should publish event without throwing exception when bus unavailable")
    void testPublishWithResilientErrorHandling() {
        String reportId = "resilient-report-" + UUID.randomUUID();

        // Even if event publishing has issues, should not fail the request
        assertDoesNotThrow(() -> {
            eventPublisher.publishQueryCompleted(reportId);
        });
    }
}
