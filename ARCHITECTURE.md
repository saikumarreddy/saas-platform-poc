# Architecture: SaaS Platform POC

## Layer-by-Layer Responsibilities

This document maps the AWS infrastructure layers to their responsibilities, based on the "cheat sheet" from the project requirements.

### 1. DNS (Route 53)
**Responsibility**: Public hostnames  
**What it does NOT do**: Auth, business logic

Maps `api.saasplatform.com` to the API Gateway endpoint. Handles DNS failover for multi-region setups.

### 2. API Gateway
**Responsibility**: Public surface, routing, WAF, JWT validation  
**What it does NOT do**: Business authorization, idempotency enforcement

- Exposes REST endpoints at `api.saasplatform.com/api/v1/*`
- Validates JWT tokens from Cognito
- Rate limiting and DDoS protection (AWS WAF)
- Routes to ALB based on path (`/api/v1/analytics/*` → Analytics ALB, `/api/v1/ingestion/*` → Ingestion ALB)

### 3. Cognito
**Responsibility**: Token issuance + validation, scopes, client identity  
**What it does NOT do**: Per-resource authorization

- Issues JWT tokens with `tenant_id` and `sub` (user ID) claims
- Validates token signatures
- Manages user identity and MFA
- Scopes define what clients can do (read, write, admin)

### 4. ALB (Application Load Balancer)
**Responsibility**: Distribute traffic to healthy pods, canary weighting  
**What it does NOT do**: Auth (behind API Gateway)

- Routes requests to EKS pods for analytics-service and ingestion-service
- Health checks ensure pods are ready (`GET /health`)
- Supports canary deployments: gradual traffic shift to new versions

### 5. EKS / Kubernetes
**Responsibility**: Pod scheduling, autoscaling, restart on failure, rolling deploys  
**What it does NOT do**: App logic

- Runs two Spring Boot microservices (analytics-service, ingestion-service)
- Auto-restarts failed pods
- Rolling updates with zero downtime (maxUnavailable=0, maxSurge=1)
- HPA (Horizontal Pod Autoscaler) scales replicas based on CPU/memory

### 6. Spring/JAX-RS Filters (TenantAwareFilter)
**Responsibility**: Build RequestContext, populate MDC, propagate trace IDs  
**What it does NOT do**: State changes

- Extracts tenant ID from JWT token claim
- Sets MDC (Mapped Diagnostic Context) for structured logging
- Builds RequestContext with tenant_id, user_id, correlation_id, trace_id
- Stores in ThreadLocal via RequestContextHolder for downstream access

### 7. Resource Layer (HTTP DTO ↔ Domain Mapping)
**Responsibility**: HTTP DTO ↔ domain mapping, status code shaping  
**What it does NOT do**: Business rules

Controllers map HTTP requests/responses to domain objects.
- Request: `{ title, metrics, dateRange }` → `Query` record
- Response: `InsightReport` → `{ reportId, reportUrl }`

### 8. Delegate Layer
**Responsibility**: Orchestration, validate header, build command, call use case, format response  
**What it does NOT do**: Domain rules

Controllers delegate to services:
- AnalyticsController validates request, calls InsightGenerationService, publishes event, formats response
- IngestionController validates file, calls FileIngestionService, publishes event

### 9. Use Case (Service Layer)
**Responsibility**: Transactional unit of work: load → validate → mutate → save → publish  
**What it does NOT do**: I/O details

- `InsightGenerationService`: Load query → validate → call Claude → save report → publish event
- `FileIngestionService`: Load file → validate → save to S3 → publish event

### 10. State Machine (Domain Model)
**Responsibility**: Flow-specific transition rules  
**What it does NOT do**: Repository details

Domain rules enforce state transitions:
- Ingestion job: PENDING → PROCESSING → COMPLETED or FAILED
- Tenant: ACTIVE or INACTIVE (cannot process requests if INACTIVE)

### 11. Repository Port (DynamoDB)
**Responsibility**: Atomic, conditional persistence  
**What it does NOT do**: What status transitions mean

DynamoDB stores:
- Tenant table: tenant_id → {name, active, region}
- IngestionJobs table: job_id → {status, tenant_id, created_at, error}
- Uses conditional writes: only update if status is PENDING

### 12. Event Publisher (EventBridge)
**Responsibility**: Map domain event to outbound payload, deliver to EventBridge/webhook  
**What it does NOT do**: Persistence

EventPublisher takes domain events and publishes to EventBridge:
- QueryCompleted: `{ tenantId, reportId, timestamp, correlationId }`
- FileUploaded: `{ tenantId, fileId, fileName, timestamp }`

### 13. DynamoDB / DynamoDB Streams
**Responsibility**: Store and conditionally update aggregates  
**What it does NOT do**: Indexes for ad-hoc queries, OpenSearch/Glue if needed

Stores state; streams track changes for event consumers.

### 14. EventBridge
**Responsibility**: Fan-out events to internal/external consumers  
**What it does NOT do**: Delivery guarantees beyond at-least-once

Routes events:
- QueryCompleted → Lambda (index to Elasticsearch), SNS (alert)
- FileUploaded → Lambda (document processor), Analytics service (auto-ingest)

### 15. Step Functions
**Responsibility**: Long-lived timers (expirations, multi-step orchestrations)  
**What it does NOT do**: Sub-second timing

Orchestrates complex workflows:
- FileUploaded → wait for processing → publish event
- QueryExpired → cancel, cleanup, notify

### 16. Datadog
**Responsibility**: Metrics, traces, logs, alerting  
**What it does NOT do**: Application config

Collects:
- Metrics: request latency, error rate, S3 upload duration
- Traces: distributed tracing across services
- Logs: structured logs with tenant_id, correlation_id
- Alerts: page on-call if error rate > 5%

### 17. Harness
**Responsibility**: CI/CD, canary orchestration, deploy gates  
**What it does NOT do**: Runtime behavior

GitHub Actions pipeline:
- Build: mvn clean install, run tests
- Test: integration tests with LocalStack
- Deploy: build Docker images, push to ECR, update EKS via kubectl

---

## Data Flow: Multi-Tenant Request Path

### Scenario: Generate Analytics Insights

```
1. Client → API Gateway (JWT header)
   └─ API GW validates token with Cognito
   └─ API GW routes to ALB

2. ALB → EKS Pod (analytics-service)
   └─ TenantAwareFilter extracts tenant_id from JWT
   └─ Sets MDC: tenant_id="tenant-a", correlation_id="abc-123"
   └─ Stores RequestContext in ThreadLocal

3. AnalyticsController.queryInsights()
   └─ Validates tenant_id matches request context
   └─ Calls InsightGenerationService

4. InsightGenerationService.generateInsights()
   └─ Calls ChatClient.prompt().user(query).call() → Claude
   └─ Claude returns InsightReport

5. ReportService.saveReport()
   └─ Serializes report to JSON
   └─ S3 upload: s3://saas-poc-reports/tenant-a/reports/{reportId}.json
   └─ Generates presigned URL (1-hour expiry)

6. EventPublisher.publishQueryCompleted()
   └─ Builds event: { tenantId, reportId, timestamp, correlationId }
   └─ Publishes to EventBridge bus: saas-platform-events
   └─ EventBridge routes to consumers (Lambda, SNS, etc.)

7. Response → Client
   └─ HTTP 200 { reportId, reportUrl }
```

### Tenant Isolation

Every operation validates tenant_id:
- RequestContext extracted from JWT claim: `tenant_id` in token
- S3 key includes tenant prefix: `tenant-a/reports/{reportId}.json`
- DynamoDB queries use tenant_id in partition key or GSI
- EventBridge events include tenantId for consumer filtering

**Threat Model**: If an attacker forges a token with `tenant_id="tenant-b"`, Cognito signature validation fails (invalid JWT). If they tamper with a valid token, JWT signature becomes invalid. Tenant A cannot see Tenant B's data because S3 keys and DynamoDB queries are scoped to the authenticated tenant_id.

---

## Event Flow: Async Processing

### File Ingestion Pipeline

```
1. POST /api/v1/ingestion/upload
   └─ FileIngestionService uploads to S3
   └─ EventPublisher publishes: ingestion.file.uploaded

2. EventBridge matches rule: source="ingestion-service", detailType="FileUploaded"
   └─ Triggers Lambda: document-processor
   └─ Lambda chunks PDF, embeds with OpenAI, stores metadata in DynamoDB

3. DocumentMetadata table updated
   └─ IngestionJob status: PROCESSING → COMPLETED
   └─ EventPublisher publishes: ingestion.completed

4. EventBridge matches rule: detailType="IngestionCompleted"
   └─ Triggers analytics-service webhook
   └─ Analytics service imports processed data for analysis

5. Optional: Step Functions waits 5 minutes
   └─ If no completion event, times out
   └─ Publishes: ingestion.timeout_dlq
   └─ Lambda handles DLQ: logs failure, publishes SNS alert
```

---

## Security Layers

### Authentication
- **Cognito**: Validates email/password, issues JWT with tenant_id claim
- **API Gateway**: Validates JWT signature before routing

### Authorization
- **RequestContext Filter**: Extracts tenant_id from JWT
- **Service Layer**: All methods check `RequestContextHolder.getCurrentTenantId()` matches request data
- **S3 Key Scoping**: Tenant A's uploads go to `tenant-a/uploads/`, cannot access `tenant-b/`
- **DynamoDB Queries**: Partition key includes tenant_id; queries cannot see other tenants

### Data Protection
- **S3 Versioning**: Enabled; accidental deletions can be rolled back
- **Encryption**: S3 objects encrypted with KMS; DynamoDB tables encrypted at rest
- **Network**: Pods in private subnets; only ALB (in public subnet) is exposed
- **Logs**: Structured logs with MDC; sensitive data (API keys) excluded; shipped to Datadog

### Audit
- **RequestContext**: Every request has correlationId for tracing
- **MDC**: Logs include tenant_id, user_id, correlation_id for audit trails
- **EventBridge**: All events logged; can query event history for compliance

---

## Deployment Strategy

### Local Development (docker-compose)
- LocalStack emulates S3, DynamoDB, EventBridge
- Services connect to LocalStack endpoint: `http://localstack:4566`
- No AWS credentials needed; uses dummy values

### Staging (AWS)
- Real AWS services
- Single EKS node group (t3.medium, 1 node, no HA)
- Manual approval for prod deploy

### Production (AWS)
- Multi-AZ EKS cluster (2+ nodes)
- DynamoDB auto-scaling
- CloudFront caching for reports
- S3 cross-region replication

---

## Performance Considerations

- **Analytics Query**: Synchronous (blocks until Claude returns). ~5-30s depending on query complexity.
- **File Ingestion**: Asynchronous. Returns job_id immediately; processing happens in Lambda.
- **Caching**: Presigned S3 URLs cached for 1 hour; CloudFront can cache reports with short TTL.
- **Scaling**: EKS HPA scales based on CPU; Lambda scales automatically.
