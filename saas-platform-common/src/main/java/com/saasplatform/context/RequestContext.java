package com.saasplatform.context;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record RequestContext(
    String tenantId,
    String userId,
    String correlationId,
    String traceId,
    LocalDateTime requestTime,
    Map<String, Object> metadata
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String tenantId;
        private String userId;
        private String correlationId = UUID.randomUUID().toString();
        private String traceId = UUID.randomUUID().toString();
        private LocalDateTime requestTime = LocalDateTime.now();
        private final Map<String, Object> metadata = new HashMap<>();

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public RequestContext build() {
            return new RequestContext(tenantId, userId, correlationId, traceId, requestTime, new HashMap<>(metadata));
        }
    }
}
