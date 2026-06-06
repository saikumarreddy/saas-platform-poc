package com.saasplatform.ingestion.controller;

import com.saasplatform.ingestion.service.EventPublisher;
import com.saasplatform.ingestion.service.FileIngestionService;
import com.saasplatform.context.RequestContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/ingestion")
@RequiredArgsConstructor
public class IngestionController {
    private final FileIngestionService fileIngestionService;
    private final EventPublisher eventPublisher;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        String tenantId = RequestContextHolder.getCurrentTenantId();
        log.info("File upload requested for tenant: {}, fileName: {}", tenantId, file.getOriginalFilename());

        try {
            String fileId = fileIngestionService.uploadFile(file);
            eventPublisher.publishFileUploaded(fileId, file.getOriginalFilename());

            Map<String, String> response = new HashMap<>();
            response.put("fileId", fileId);
            response.put("fileName", file.getOriginalFilename());
            response.put("status", "PENDING");
            response.put("tenantId", tenantId);

            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            log.error("File upload failed for tenant: {}", tenantId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<Map<String, String>> getJobStatus(@PathVariable String jobId) {
        String tenantId = RequestContextHolder.getCurrentTenantId();
        log.info("Status request for tenant: {}, jobId: {}", tenantId, jobId);

        Map<String, String> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("status", "PROCESSING");
        response.put("tenantId", tenantId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "ingestion-service");
        return ResponseEntity.ok(response);
    }
}
