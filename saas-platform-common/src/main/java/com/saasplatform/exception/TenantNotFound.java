package com.saasplatform.exception;

public class TenantNotFound extends RuntimeException {
    private final String tenantId;

    public TenantNotFound(String tenantId) {
        super("Tenant not found: " + tenantId);
        this.tenantId = tenantId;
    }

    public String getTenantId() {
        return tenantId;
    }
}
