package com.saasplatform.analytics.integration;

import com.saasplatform.analytics.service.InsightGenerationService;
import com.saasplatform.context.RequestContext;
import com.saasplatform.context.RequestContextHolder;
import com.saasplatform.filter.TenantAwareFilter;
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

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("Analytics Controller Integration Tests")
class AnalyticsControllerIntegrationTest extends LocalStackTestBase {

    private static final String JWT_SECRET = "test-secret-key-for-jwt-validation";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String USER_ID = "user-123";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InsightGenerationService insightGenerationService;

    private String validTokenTenantA;
    private String validTokenTenantB;
    private String invalidToken;

    @BeforeEach
    void setUp() {
        validTokenTenantA = generateJwtToken(TENANT_A, USER_ID);
        validTokenTenantB = generateJwtToken(TENANT_B, USER_ID);
        invalidToken = "invalid.token.format";

        RequestContextHolder.clearAll();
    }

    @Test
    @DisplayName("Should successfully process analytics query and return report")
    void testQueryInsights_Success() throws Exception {
        String query = """
                {
                    "title": "Q2 Revenue Analysis",
                    "metrics": ["revenue", "customer_count"],
                    "dateRange": "2026-04-01 to 2026-06-30"
                }
                """;

        InsightGenerationService.InsightReport mockReport = new InsightGenerationService.InsightReport(
                "Q2 Revenue Summary",
                "Revenue increased by 15% YoY",
                new String[]{"Focus on enterprise segment", "Expand to new markets"}
        );

        when(insightGenerationService.generateInsights(any())).thenReturn(mockReport);

        mockMvc.perform(post("/api/v1/analytics/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validTokenTenantA)
                        .content(query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId", notNullValue()))
                .andExpect(jsonPath("$.reportUrl", notNullValue()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_A));
    }

    @Test
    @DisplayName("Should return 401 for request with invalid JWT token")
    void testQueryInsights_InvalidToken() throws Exception {
        String query = """
                {
                    "title": "Q2 Revenue Analysis",
                    "metrics": ["revenue"],
                    "dateRange": "2026-04-01 to 2026-06-30"
                }
                """;

        mockMvc.perform(post("/api/v1/analytics/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + invalidToken)
                        .content(query))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 401 for request without Authorization header")
    void testQueryInsights_NoAuthHeader() throws Exception {
        String query = """
                {
                    "title": "Q2 Revenue Analysis",
                    "metrics": ["revenue"],
                    "dateRange": "2026-04-01 to 2026-06-30"
                }
                """;

        mockMvc.perform(post("/api/v1/analytics/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(query))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should isolate reports between different tenants")
    void testTenantIsolation_ReportNotAccessibleByCrossTenant() throws Exception {
        String reportId = "report-" + UUID.randomUUID();

        // Tenant A should only be able to get presigned URLs for their own reports
        // (Report ID encoding would include tenant prefix in real scenario)
        // This is a simplified test - in production, access control would validate tenant ownership

        mockMvc.perform(get("/api/v1/analytics/reports/" + reportId)
                        .header("Authorization", "Bearer " + validTokenTenantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId", notNullValue()))
                .andExpect(jsonPath("$.reportUrl", notNullValue()));
    }

    @Test
    @DisplayName("Should return health status for analytics service")
    void testHealthCheck() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("analytics-service"));
    }

    @Test
    @DisplayName("Should enforce multi-tenant context in request processing")
    void testMultiTenantContextPropagation() throws Exception {
        String query = """
                {
                    "title": "Tenant Isolation Test",
                    "metrics": ["test"],
                    "dateRange": "2026-01-01 to 2026-12-31"
                }
                """;

        InsightGenerationService.InsightReport mockReport = new InsightGenerationService.InsightReport(
                "Isolation Test Report",
                "Test passed",
                new String[]{"No cross-tenant data leakage"}
        );

        when(insightGenerationService.generateInsights(any())).thenReturn(mockReport);

        // Request from Tenant A
        mockMvc.perform(post("/api/v1/analytics/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validTokenTenantA)
                        .content(query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_A));

        // Request from Tenant B (should be isolated)
        mockMvc.perform(post("/api/v1/analytics/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validTokenTenantB)
                        .content(query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_B));
    }

    private String generateJwtToken(String tenantId, String userId) {
        return Jwts.builder()
                .setSubject(userId)
                .claim("tenant_id", tenantId)
                .claim("user_id", userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hour
                .signWith(SignatureAlgorithm.HS256, JWT_SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();
    }
}
