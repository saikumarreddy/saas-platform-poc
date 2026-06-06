---
name: Spring AI & AWS Integration Patterns
description: Patterns used in analytics/ingestion services for AI and cloud integration
type: reference
---

## Spring AI ChatClient Pattern (AnalyticsAiConfig.java)

**Bean Factory**:
```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder) {
    return builder
        .defaultSystem("You are an analytics expert...")
        .build();
}
```

**Usage** (InsightGenerationService.java):
```java
BeanOutputConverter<InsightReport> converter = new BeanOutputConverter<>(InsightReport.class);
String response = chatClient.prompt()
    .user(user -> user.text(prompt, ...))
    .call()
    .content();
InsightReport report = converter.convert(response);
```

**Why This Pattern**:
- Reused across all services
- System prompt configured once
- BeanOutputConverter forces structured output (no hallucinations)
- Records used for immutable DTOs

## EventBridge Publisher Pattern (EventPublisher.java)

**Pattern**:
```java
@Service
@RequiredArgsConstructor
public class EventPublisher {
    private final EventBridgeClient eventBridgeClient;
    private final ObjectMapper objectMapper;
    
    public void publishQueryCompleted(String reportId) {
        String tenantId = RequestContextHolder.getCurrentTenantId();
        Map<String, Object> detail = new HashMap<>();
        detail.put("tenantId", tenantId);
        detail.put("reportId", reportId);
        detail.put("timestamp", LocalDateTime.now());
        
        String detailJson = objectMapper.writeValueAsString(detail);
        PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
            .source("analytics-service")
            .detailType("QueryCompleted")
            .detail(detailJson)
            .build();
        
        eventBridgeClient.putEvents(
            PutEventsRequest.builder()
                .entries(entry)
                .eventBusName("saas-platform-events")
                .build()
        );
    }
}
```

**Why This Pattern**:
- Non-blocking (async)
- DLQ support for failures
- Event details include tenantId for filtering
- Correlation ID for distributed tracing

## TenantAwareFilter Pattern (TenantAwareFilter.java)

**Pattern**:
```java
@Component
public class TenantAwareFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        String authHeader = httpRequest.getHeader("Authorization");
        String tenantId = null;
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Jws<Claims> claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .build()
                .parseSignedClaims(token);
            tenantId = (String) claims.getPayload().get("tenant_id");
        }
        
        RequestContext ctx = RequestContext.builder()
            .tenantId(tenantId)
            .userId(userId)
            .correlationId(correlationId)
            .traceId(traceId)
            .build();
        
        RequestContextHolder.set(ctx);
        MDC.put("tenant_id", tenantId);
        
        try {
            chain.doFilter(request, response);
        } finally {
            RequestContextHolder.clear();
            MDC.clear();
        }
    }
}
```

**Why This Pattern**:
- Extracts tenant from JWT (no business logic needed)
- ThreadLocal context available to all downstream services
- MDC for structured logging (tenant_id in every log)
- Cleanup in finally block prevents memory leaks

## S3 Presigned URL Pattern (ReportService.java)

**Pattern**:
```java
@Service
@RequiredArgsConstructor
public class ReportService {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    
    public String saveReport(Object report) {
        String tenantId = RequestContextHolder.getCurrentTenantId();
        String reportId = UUID.randomUUID().toString();
        String key = String.format("%s/reports/%s.json", tenantId, reportId);
        
        s3Client.putObject(request, RequestBody.fromString(jsonContent));
        return reportId;
    }
    
    public String getPresignedUrl(String reportId) {
        String tenantId = RequestContextHolder.getCurrentTenantId();
        String key = String.format("%s/reports/%s.json", tenantId, reportId);
        
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build()
        );
        return presigned.url().toString();
    }
}
```

**Why This Pattern**:
- Tenant isolation via key prefix
- URL expires after 1 hour (security)
- No direct S3 access needed by clients
- Presigned URL allows browser download

## Multi-Module Maven Pattern (pom.xml)

**Root POM**:
- Parent: spring-boot-starter-parent (3.3.4)
- BOM imports: Spring AI (1.0.0), AWS SDK (2.25.0)
- Modules: saas-platform-common, analytics-service, ingestion-service
- Dependency management ensures consistent versions

**Module POMs**:
- Each inherits from root parent
- Explicit dependencies (no transitive surprises)
- Common library imported as dependency

**Why This Pattern**:
- BOM centralizes version management
- Parent POM avoids duplication
- Each service can be built independently
- Easy to add new services

## Local Development Pattern (docker-compose.yml)

**Pattern**:
- LocalStack container (S3, DynamoDB, EventBridge, SQS, SNS)
- Two service containers (analytics-service, ingestion-service)
- Health checks ensure services are ready
- All services networked together

**Why This Pattern**:
- No AWS account needed for local dev
- Exact same SDK calls as production
- Rapid iteration (30s startup)
- Reproducible environments

## Git Workflow Pattern

**Commits**:
- feat: new feature
- fix: bug fix
- docs: documentation only
- test: testing additions
- refactor: code reorganization
- chore: build, dependencies

**Why This Pattern**:
- Semantic commits enable automated changelogs
- Clear intent in history
- Easy to find related changes (git log --grep="feat:")

## Kubernetes Deployment Pattern

**Pattern**:
- Deployment with 2 replicas
- Liveness probe: /actuator/health/liveness (30s delay)
- Readiness probe: /actuator/health/readiness (10s delay)
- Pod Disruption Budget: minAvailable=1 (prevents simultaneous terminations)
- Resource requests/limits (500m/512Mi requests, 1000m/1Gi limits)
- Security context: runAsNonRoot, readOnlyRootFilesystem

**Why This Pattern**:
- HA (2 replicas survive pod eviction)
- Proper startup detection (readiness before traffic)
- Resource efficiency (requests for scheduling)
- Security best practices

## Configuration Management Pattern

**Local (.env)**:
- Environment variables for API keys
- AWS_ENDPOINT_OVERRIDE=http://localstack:4566

**AWS (application.yml with profiles)**:
- spring.profiles.active=local (LocalStack) or aws (real AWS)
- Separate configs per environment
- Secrets in Kubernetes (kubectl create secret)

**Why This Pattern**:
- 12-factor compliance
- No sensitive data in code
- Easy env switching (no code changes)
