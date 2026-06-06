package com.saasplatform.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import com.saasplatform.context.RequestContextHolder;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.reports-bucket:saas-poc-reports}")
    private String reportsBucket;

    public String saveReport(Object report) {
        String tenantId = RequestContextHolder.getCurrentTenantId();
        String reportId = UUID.randomUUID().toString();

        try {
            String key = String.format("%s/reports/%s.json", tenantId, reportId);
            String jsonContent = objectMapper.writeValueAsString(report);

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(reportsBucket)
                    .key(key)
                    .build();

            s3Client.putObject(request, software.amazon.awssdk.core.sync.RequestBody.fromString(jsonContent));
            log.info("Report saved: s3://{}/{}", reportsBucket, key);

            return reportId;
        } catch (Exception e) {
            log.error("Failed to save report for tenant: {}", tenantId, e);
            throw new RuntimeException("Failed to save report", e);
        }
    }

    public String getPresignedUrl(String reportId) {
        String tenantId = RequestContextHolder.getCurrentTenantId();
        String key = String.format("%s/reports/%s.json", tenantId, reportId);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(reportsBucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }
}
