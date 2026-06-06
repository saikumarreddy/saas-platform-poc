package com.saasplatform.model;

import java.time.LocalDateTime;

public record Tenant(
    String tenantId,
    String name,
    String awsAccountId,
    String region,
    LocalDateTime createdAt,
    boolean active
) {
    public static Tenant of(String tenantId, String name) {
        return new Tenant(tenantId, name, "", "us-east-1", LocalDateTime.now(), true);
    }
}
