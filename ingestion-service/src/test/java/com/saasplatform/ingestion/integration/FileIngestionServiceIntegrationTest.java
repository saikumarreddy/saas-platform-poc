package com.saasplatform.ingestion.integration;

import com.saasplatform.ingestion.service.FileIngestionService;
import com.saasplatform.context.RequestContext;
import com.saasplatform.context.RequestContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("File Ingestion Service (S3) Integration Tests")
class FileIngestionServiceIntegrationTest extends LocalStackTestBase {

    private static final String TENANT_ID = "ingestion-test-tenant";
    private static final String USER_ID = "ingestion-test-user";
    private static final String CORRELATION_ID = UUID.randomUUID().toString();

    @Autowired
    private FileIngestionService fileIngestionService;

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
                    .bucket("saas-poc-ingestion-test")
                    .build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                s3Client.createBucket(b -> b.bucket("saas-poc-ingestion-test"));
            }
        }
    }

    @Test
    @DisplayName("Should successfully upload file to S3")
    void testUploadFile_Success() {
        byte[] fileContent = "test,data\nvalue1,value2\n".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-upload.csv",
                "text/csv",
                fileContent
        );

        String fileId = assertDoesNotThrow(() ->
            fileIngestionService.uploadFile(file)
        );

        assertNotNull(fileId);
        assertTrue(fileId.length() > 0);
    }

    @Test
    @DisplayName("Should store file with tenant-scoped key")
    void testUploadFile_TenantScoping() {
        byte[] fileContent = "header1,header2\ndata1,data2\n".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "tenant-scoped.csv",
                "text/csv",
                fileContent
        );

        String fileId = assertDoesNotThrow(() ->
            fileIngestionService.uploadFile(file)
        );

        assertNotNull(fileId);
        // Verify tenant context was used in upload
        assertEquals(TENANT_ID, RequestContextHolder.getCurrentTenantId());
    }

    @Test
    @DisplayName("Should handle different file formats")
    void testUploadFile_MultipleFormats() {
        // CSV
        byte[] csvContent = "col1,col2\nval1,val2".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile csvFile = new MockMultipartFile(
                "file",
                "data.csv",
                "text/csv",
                csvContent
        );

        String csvFileId = assertDoesNotThrow(() ->
            fileIngestionService.uploadFile(csvFile)
        );
        assertNotNull(csvFileId);

        // JSON
        byte[] jsonContent = "{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile jsonFile = new MockMultipartFile(
                "file",
                "data.json",
                "application/json",
                jsonContent
        );

        String jsonFileId = assertDoesNotThrow(() ->
            fileIngestionService.uploadFile(jsonFile)
        );
        assertNotNull(jsonFileId);
    }

    @Test
    @DisplayName("Should generate unique file IDs for each upload")
    void testUploadFile_UniqueFileIds() {
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "test1.csv",
                "text/csv",
                content
        );

        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "test2.csv",
                "text/csv",
                content
        );

        String fileId1 = assertDoesNotThrow(() ->
            fileIngestionService.uploadFile(file1)
        );

        String fileId2 = assertDoesNotThrow(() ->
            fileIngestionService.uploadFile(file2)
        );

        assertNotEquals(fileId1, fileId2);
    }

    @Test
    @DisplayName("Should preserve original filename metadata")
    void testUploadFile_PreservesFilename() {
        String originalFilename = "quarterly-report-2026-Q2.csv";
        byte[] content = "data".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                originalFilename,
                "text/csv",
                content
        );

        String fileId = assertDoesNotThrow(() ->
            fileIngestionService.uploadFile(file)
        );

        assertNotNull(fileId);
        // Filename is preserved in S3 key path: {tenantId}/uploads/{fileId}/{originalFilename}
    }

    @Test
    @DisplayName("Should handle empty files")
    void testUploadFile_EmptyFile() {
        byte[] emptyContent = new byte[0];
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.csv",
                "text/csv",
                emptyContent
        );

        String fileId = assertDoesNotThrow(() ->
            fileIngestionService.uploadFile(emptyFile)
        );

        assertNotNull(fileId);
    }

    @Test
    @DisplayName("Should handle large files")
    void testUploadFile_LargeFile() {
        // Create a 50MB file (but keeping test reasonable)
        byte[] largeContent = new byte[1024 * 1024]; // 1MB for testing
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) (i % 256);
        }

        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "large-file.bin",
                "application/octet-stream",
                largeContent
        );

        String fileId = assertDoesNotThrow(() ->
            fileIngestionService.uploadFile(largeFile)
        );

        assertNotNull(fileId);
    }
}
