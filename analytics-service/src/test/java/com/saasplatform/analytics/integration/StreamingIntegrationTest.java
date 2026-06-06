package com.saasplatform.analytics.integration;

import com.saasplatform.analytics.service.InsightGenerationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("Analytics Streaming Integration Tests")
class StreamingIntegrationTest extends LocalStackTestBase {

    private static final String JWT_SECRET = "test-secret-key-for-jwt-validation";
    private static final String TENANT_ID = "stream-test-tenant";
    private static final String USER_ID = "stream-test-user";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InsightGenerationService insightGenerationService;

    private String validToken;

    @BeforeEach
    void setUp() {
        validToken = generateJwtToken(TENANT_ID, USER_ID);
    }

    @Test
    @DisplayName("Should stream insights as Server-Sent Events")
    void testStreamInsights_Success() throws Exception {
        String query = """
                {
                    "title": "Real-time Revenue Analysis",
                    "metrics": "revenue, growth_rate",
                    "dateRange": "2026-06-01 to 2026-06-06"
                }
                """;

        Flux<String> mockStream = Flux.just(
                "Real-time ",
                "revenue ",
                "analysis ",
                "showing ",
                "15% ",
                "growth ",
                "this ",
                "quarter."
        );

        when(insightGenerationService.streamInsights(any())).thenReturn(mockStream);

        mockMvc.perform(post("/api/v1/analytics/query/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validToken)
                        .content(query))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    @Test
    @DisplayName("Should stream insights with proper tenant context")
    void testStreamInsights_TenantContext() throws Exception {
        String query = """
                {
                    "title": "Tenant-Isolated Stream",
                    "metrics": "test_metric",
                    "dateRange": "2026-01-01 to 2026-12-31"
                }
                """;

        Flux<String> mockStream = Flux.just("Streaming ", "with ", "tenant ", "context");

        when(insightGenerationService.streamInsights(any())).thenReturn(mockStream);

        mockMvc.perform(post("/api/v1/analytics/query/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validToken)
                        .content(query))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should reject streaming request without Authorization header")
    void testStreamInsights_NoAuthHeader() throws Exception {
        String query = """
                {
                    "title": "Unauthorized Stream",
                    "metrics": "test",
                    "dateRange": "2026-01-01 to 2026-12-31"
                }
                """;

        mockMvc.perform(post("/api/v1/analytics/query/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(query))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject streaming request with invalid JWT token")
    void testStreamInsights_InvalidToken() throws Exception {
        String invalidToken = "invalid.token.format";
        String query = """
                {
                    "title": "Invalid Token Stream",
                    "metrics": "test",
                    "dateRange": "2026-01-01 to 2026-12-31"
                }
                """;

        mockMvc.perform(post("/api/v1/analytics/query/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + invalidToken)
                        .content(query))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should handle empty stream gracefully")
    void testStreamInsights_EmptyStream() throws Exception {
        String query = """
                {
                    "title": "Empty Stream Test",
                    "metrics": "test",
                    "dateRange": "2026-01-01 to 2026-12-31"
                }
                """;

        Flux<String> emptyStream = Flux.empty();

        when(insightGenerationService.streamInsights(any())).thenReturn(emptyStream);

        mockMvc.perform(post("/api/v1/analytics/query/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validToken)
                        .content(query))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should produce text/event-stream media type")
    void testStreamInsights_ContentType() throws Exception {
        String query = """
                {
                    "title": "Content Type Test",
                    "metrics": "test",
                    "dateRange": "2026-01-01 to 2026-12-31"
                }
                """;

        Flux<String> mockStream = Flux.just("test");

        when(insightGenerationService.streamInsights(any())).thenReturn(mockStream);

        mockMvc.perform(post("/api/v1/analytics/query/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validToken)
                        .content(query))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    private String generateJwtToken(String tenantId, String userId) {
        return Jwts.builder()
                .setSubject(userId)
                .claim("tenant_id", tenantId)
                .claim("user_id", userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(SignatureAlgorithm.HS256, JWT_SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();
    }
}
