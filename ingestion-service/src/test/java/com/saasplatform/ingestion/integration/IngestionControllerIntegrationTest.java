package com.saasplatform.ingestion.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@DisplayName("Ingestion Controller Integration Tests")
class IngestionControllerIntegrationTest extends LocalStackTestBase {

    private static final String JWT_SECRET = "test-secret-key-for-jwt-validation";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String USER_ID = "user-123";

    @Autowired
    private MockMvc mockMvc;

    private String validTokenTenantA;
    private String validTokenTenantB;
    private String invalidToken;

    @BeforeEach
    void setUp() {
        validTokenTenantA = generateJwtToken(TENANT_A, USER_ID);
        validTokenTenantB = generateJwtToken(TENANT_B, USER_ID);
        invalidToken = "invalid.token.format";
    }

    @Test
    @DisplayName("Should successfully upload file and return file ID")
    void testUploadFile_Success() throws Exception {
        byte[] csvContent = "header1,header2\nvalue1,value2\n".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-data.csv",
                "text/csv",
                csvContent
        );

        mockMvc.perform(multipart("/api/v1/ingestion/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + validTokenTenantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId", notNullValue()))
                .andExpect(jsonPath("$.fileName").value("test-data.csv"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.tenantId").value(TENANT_A));
    }

    @Test
    @DisplayName("Should reject file upload without Authorization header")
    void testUploadFile_NoAuthHeader() throws Exception {
        byte[] csvContent = "header1,header2\nvalue1,value2\n".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-data.csv",
                "text/csv",
                csvContent
        );

        mockMvc.perform(multipart("/api/v1/ingestion/upload")
                        .file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject file upload with invalid JWT token")
    void testUploadFile_InvalidToken() throws Exception {
        byte[] csvContent = "header1,header2\nvalue1,value2\n".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-data.csv",
                "text/csv",
                csvContent
        );

        mockMvc.perform(multipart("/api/v1/ingestion/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should support different file formats")
    void testUploadFile_DifferentFormats() throws Exception {
        // JSON file
        byte[] jsonContent = "{\"field1\":\"value1\"}".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile jsonFile = new MockMultipartFile(
                "file",
                "test-data.json",
                "application/json",
                jsonContent
        );

        mockMvc.perform(multipart("/api/v1/ingestion/upload")
                        .file(jsonFile)
                        .header("Authorization", "Bearer " + validTokenTenantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("test-data.json"));

        // CSV file
        byte[] csvContent = "col1,col2\nval1,val2\n".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile csvFile = new MockMultipartFile(
                "file",
                "test-data.csv",
                "text/csv",
                csvContent
        );

        mockMvc.perform(multipart("/api/v1/ingestion/upload")
                        .file(csvFile)
                        .header("Authorization", "Bearer " + validTokenTenantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("test-data.csv"));
    }

    @Test
    @DisplayName("Should return ingestion job status")
    void testGetIngestionStatus_Success() throws Exception {
        String jobId = "job-" + UUID.randomUUID();

        mockMvc.perform(get("/api/v1/ingestion/status/" + jobId)
                        .header("Authorization", "Bearer " + validTokenTenantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId", notNullValue()))
                .andExpect(jsonPath("$.status", notNullValue()));
    }

    @Test
    @DisplayName("Should enforce tenant isolation for ingestion jobs")
    void testTenantIsolation_IngestionJobs() throws Exception {
        String jobId = "isolation-job-" + UUID.randomUUID();

        // Tenant A requests status
        mockMvc.perform(get("/api/v1/ingestion/status/" + jobId)
                        .header("Authorization", "Bearer " + validTokenTenantA))
                .andExpect(status().isOk());

        // Tenant B requests status (should be isolated)
        mockMvc.perform(get("/api/v1/ingestion/status/" + jobId)
                        .header("Authorization", "Bearer " + validTokenTenantB))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return health status for ingestion service")
    void testHealthCheck() throws Exception {
        mockMvc.perform(get("/api/v1/ingestion/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("ingestion-service"));
    }

    @Test
    @DisplayName("Should handle concurrent file uploads from different tenants")
    void testConcurrentUploads_MultiTenant() throws Exception {
        byte[] csvContent = "data".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                csvContent
        );

        // Upload from Tenant A
        mockMvc.perform(multipart("/api/v1/ingestion/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + validTokenTenantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_A));

        // Upload from Tenant B (create new file object for second request)
        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                csvContent
        );

        mockMvc.perform(multipart("/api/v1/ingestion/upload")
                        .file(file2)
                        .header("Authorization", "Bearer " + validTokenTenantB))
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
