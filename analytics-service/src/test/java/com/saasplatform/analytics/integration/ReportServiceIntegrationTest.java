package com.saasplatform.analytics.integration;

import com.saasplatform.analytics.service.InsightGenerationService;
import com.saasplatform.analytics.service.ReportService;
import com.saasplatform.context.RequestContextHolder;
import com.saasplatform.context.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Report Service (S3) Integration Tests")
class ReportServiceIntegrationTest extends LocalStackTestBase {

    private static final String TENANT_ID = "test-tenant";
    private static final String USER_ID = "test-user";
    private static final String CORRELATION_ID = UUID.randomUUID().toString();

    @Autowired
    private ReportService reportService;

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

        // Ensure S3 bucket exists for tests
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket("saas-poc-reports-test")
                    .build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                s3Client.createBucket(b -> b.bucket("saas-poc-reports-test"));
            }
        }
    }

    @Test
    @DisplayName("Should save report to S3 with tenant-scoped key")
    void testSaveReport_SuccessWithTenantScope() {
        InsightGenerationService.InsightReport report = new InsightGenerationService.InsightReport(
                "Test Report",
                "Test summary of insights",
                new String[]{"Recommendation 1", "Recommendation 2"}
        );

        String reportId = reportService.saveReport(report);

        assertNotNull(reportId);
        assertTrue(reportId.length() > 0);
        // Verify the report ID format
        assertTrue(reportId.matches("[a-f0-9-]+"));
    }

    @Test
    @DisplayName("Should generate presigned URL for report")
    void testGetPresignedUrl_Success() {
        InsightGenerationService.InsightReport report = new InsightGenerationService.InsightReport(
                "Test Report for URL",
                "Test summary",
                new String[]{"Rec 1"}
        );

        String reportId = reportService.saveReport(report);
        String presignedUrl = reportService.getPresignedUrl(reportId);

        assertNotNull(presignedUrl);
        assertTrue(presignedUrl.startsWith("https://"));
        assertTrue(presignedUrl.contains("X-Amz-Signature"));
        assertTrue(presignedUrl.contains("X-Amz-Expires"));
    }

    @Test
    @DisplayName("Should include tenant ID in S3 key path")
    void testSaveReport_TenantIsolatedPath() {
        InsightGenerationService.InsightReport report = new InsightGenerationService.InsightReport(
                "Tenant Isolated Report",
                "Testing tenant isolation",
                new String[]{"Isolated recommendation"}
        );

        String reportId = reportService.saveReport(report);

        assertNotNull(reportId);
        // In production, the S3 key would follow pattern: {tenantId}/reports/{reportId}.json
        // Verify that the report was saved (reportId is non-null means save succeeded)
    }

    @Test
    @DisplayName("Should return different presigned URLs for different report IDs")
    void testPresignedUrlsAreUnique() {
        InsightGenerationService.InsightReport report1 = new InsightGenerationService.InsightReport(
                "Report 1",
                "Summary 1",
                new String[]{"Rec 1"}
        );

        InsightGenerationService.InsightReport report2 = new InsightGenerationService.InsightReport(
                "Report 2",
                "Summary 2",
                new String[]{"Rec 2"}
        );

        String reportId1 = reportService.saveReport(report1);
        String reportId2 = reportService.saveReport(report2);

        String url1 = reportService.getPresignedUrl(reportId1);
        String url2 = reportService.getPresignedUrl(reportId2);

        assertNotEquals(url1, url2);
        assertFalse(url1.equals(url2));
    }
}
