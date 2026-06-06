package com.saasplatform.ingestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import com.saasplatform.context.RequestContextHolder;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileIngestionService {
    private final S3Client s3Client;

    @Value("${aws.s3.ingestion-bucket:saas-poc-ingestion}")
    private String ingestionBucket;

    @Value("${file.max-size-mb:100}")
    private int maxSizeMb;

    public String uploadFile(MultipartFile file) throws IOException {
        String tenantId = RequestContextHolder.getCurrentTenantId();

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > maxSizeMb * 1024 * 1024) {
            throw new IllegalArgumentException("File exceeds maximum size of " + maxSizeMb + "MB");
        }

        String fileId = UUID.randomUUID().toString();
        String key = String.format("%s/uploads/%s/%s", tenantId, fileId, file.getOriginalFilename());

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(ingestionBucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, software.amazon.awssdk.core.sync.RequestBody.fromInputStream(
                    file.getInputStream(), file.getSize()));

            log.info("File uploaded: tenant={}, fileId={}, key={}", tenantId, fileId, key);
            return fileId;
        } catch (Exception e) {
            log.error("Failed to upload file for tenant: {}", tenantId, e);
            throw new RuntimeException("Failed to upload file", e);
        }
    }
}
