# TASKS.md - Project Task Tracker

Track new features, bug fixes, improvements, and maintenance tasks for SaaS Platform POC.

**How to Use:**
1. Add new tasks with status `pending`
2. Update to `in_progress` when starting work
3. Move to `completed` when done
4. Use `git commit` with task reference: `git commit -m "feat: task-001 add streaming endpoints"`

---

## Backlog

### Core Features

#### TASK-001: Add Spring AI Streaming Support
**Status**: `completed` ✅  
**Description**: Implement streaming chat responses via `Flux<String>` for real-time insights  
**Why**: Better UX for long-running AI operations  
**Effort**: 2 hours  
**Files**: `analytics-service/controller/AnalyticsController.java`, `analytics-service/service/InsightGenerationService.java`  
**Completed**: 2026-06-06
**Implementation**: 
  - POST /api/v1/analytics/query/stream endpoint (Server-Sent Events)
  - InsightGenerationService.streamInsights() with Flux<String>
  - StreamingIntegrationTest with 6 test cases
  - Updated LOCAL_DEV.md with streaming examples

---

#### TASK-002: Add Message Memory/Conversation History
**Status**: `pending`  
**Description**: Implement multi-turn conversation with `MessageChatMemoryAdvisor` via Spring AI  
**Why**: Users can reference previous queries  
**Effort**: 3 hours  
**Files**: `analytics-service/service/ConversationService.java` (new), `AnalyticsController.java`  
**Dependencies**: Spring AI MessageChatMemoryAdvisor, DynamoDB for persistence  
**Blocked By**: None  
**Notes**: Use DynamoDB for production (better than in-memory)

---

#### TASK-003: Lambda Event Handlers
**Status**: `pending`  
**Description**: Implement Lambda functions for EventBridge events (FileUploaded, QueryCompleted)  
**Why**: Decouple async processing from services  
**Effort**: 4 hours  
**Files**: `infrastructure/lambda/` (new), Terraform updates  
**Handlers**:
  - `document-processor`: Chunk, embed, extract metadata from uploaded files
  - `dead-letter-handler`: Handle failed events from DLQ
  - `analytics-indexer`: Index reports for full-text search  
**Blocked By**: None

---

#### TASK-004: Add Elasticsearch/OpenSearch Integration
**Status**: `pending`  
**Description**: Full-text search for reports and documents  
**Why**: Better discoverability of historical insights  
**Effort**: 5 hours  
**Files**: New `search-service` module, Lambda indexer  
**Endpoints**:
  - `GET /api/v1/search?q=keyword` - Search reports
  - `GET /api/v1/search/documents?tenant={id}` - Search by tenant  
**Blocked By**: TASK-003 (Lambda handlers)

---

#### TASK-005: Add Datadog Integration
**Status**: `pending`  
**Description**: Metrics, traces, logs, custom dashboards  
**Why**: Production monitoring and observability  
**Effort**: 3 hours  
**Files**: `pom.xml` (dd-java-agent), application.yml configs  
**What to Track**:
  - Request latency (p50, p95, p99)
  - Error rate by service
  - EventBridge lag
  - DynamoDB throttling
  - S3 upload/download duration  
**Blocked By**: None

---

### Infrastructure & DevOps

#### TASK-006: Add Kubernetes HPA (Horizontal Pod Autoscaler)
**Status**: `pending`  
**Description**: Auto-scale pods based on CPU/memory  
**Why**: Cost optimization, handle traffic spikes  
**Effort**: 1 hour  
**Files**: `infrastructure/k8s/hpa.yaml` (new)  
**Metrics**: CPU > 70% → scale up, CPU < 30% → scale down  
**Blocked By**: None

---

#### TASK-007: Setup Terraform Remote State
**Status**: `pending`  
**Description**: Configure S3 + DynamoDB for remote state management  
**Why**: Team collaboration, prevent concurrent applies  
**Effort**: 1 hour  
**Files**: `infrastructure/terraform/backend.tf` (new), `s3.tf` (new)  
**Blocked By**: None  
**Notes**: Already scaffolded in main.tf (commented)

---

#### TASK-008: Add AWS Secrets Manager Integration
**Status**: `pending`  
**Description**: Store API keys and DB passwords in AWS Secrets Manager  
**Why**: Rotate secrets without redeploying  
**Effort**: 2 hours  
**Files**: Terraform `secrets-manager.tf`, Spring Boot `application-aws.yml`  
**Secrets to Manage**:
  - ANTHROPIC_API_KEY
  - OPENAI_API_KEY
  - JWT_SECRET  
**Blocked By**: TASK-007 (remote state)

---

#### TASK-009: Add CloudWatch Dashboards & Alarms
**Status**: `pending`  
**Description**: Custom dashboards for each service, SNS alerts  
**Why**: Ops team visibility, incident response  
**Effort**: 2 hours  
**Files**: `infrastructure/terraform/cloudwatch.tf` (new)  
**Dashboards**:
  - Analytics Service (latency, errors, S3 ops)
  - Ingestion Service (upload rate, processing lag)
  - Infrastructure (EKS nodes, DynamoDB throttling)  
**Alarms**:
  - Error rate > 5% → SNS alert
  - Response time > 5s → SNS alert
  - DynamoDB throttled → SNS alert  
**Blocked By**: None

---

### Testing & Quality

#### TASK-010: Add Integration Tests with Testcontainers
**Status**: `completed` ✅  
**Description**: Full integration tests for analytics/ingestion services  
**Why**: Catch regressions, ensure multi-tenant isolation  
**Effort**: 4 hours  
**Files**: `analytics-service/src/test/java/com/saasplatform/analytics/integration/`, `ingestion-service/src/test/java/com/saasplatform/ingestion/integration/`  
**Test Scenarios**:
  - Analytics query → S3 save → EventBridge publish ✅
  - File upload → S3 store → EventBridge publish ✅
  - Multi-tenant isolation (tenant-a cannot access tenant-b data) ✅
  - JWT validation (invalid token → 401) ✅
**Completed**: 2026-06-06
**Test Coverage**: 23 integration test cases across 6 test classes using Testcontainers + LocalStack

---

#### TASK-011: Add Contract Tests (Spring Cloud Contract)
**Status**: `pending`  
**Description**: Consumer-driven contract tests between services  
**Why**: Prevent breaking API changes  
**Effort**: 3 hours  
**Files**: `analytics-service/src/test/resources/contracts/` (new)  
**Contracts**:
  - /api/v1/analytics/query request/response
  - /api/v1/ingestion/upload request/response  
**Blocked By**: None

---

#### TASK-012: Add Performance Tests (JMeter/Gatling)
**Status**: `pending`  
**Description**: Load testing under realistic traffic  
**Why**: Validate scalability assumptions  
**Effort**: 3 hours  
**Scenarios**:
  - 100 concurrent analytics queries
  - 50 concurrent file uploads (10MB each)
  - Sustained load for 5 min  
**Blocked By**: TASK-006 (HPA setup)

---

### Documentation & Knowledge

#### TASK-013: Add Architecture Decision Records (ADRs)
**Status**: `pending`  
**Description**: Document major technical decisions  
**Why**: Team alignment, future reference  
**Effort**: 2 hours  
**ADRs**:
  - ADR-001: Why EventBridge over SQS
  - ADR-002: Multi-module Maven vs monorepo
  - ADR-003: Spring AI ChatClient pattern
  - ADR-004: S3 key scoping for multi-tenant  
**Files**: `docs/adr/` (new)  
**Blocked By**: None

---

#### TASK-014: Add Runbooks for Common Operations
**Status**: `pending`  
**Description**: How to deploy, rollback, scale, debug  
**Why**: Ops team self-sufficiency  
**Effort**: 2 hours  
**Runbooks**:
  - RUNBOOK-001: Deploy new version to EKS
  - RUNBOOK-002: Rollback on failed deploy
  - RUNBOOK-003: Scale pods up/down manually
  - RUNBOOK-004: Debug slow queries
  - RUNBOOK-005: Handle EventBridge DLQ backlog  
**Files**: `docs/runbooks/` (new)  
**Blocked By**: None

---

#### TASK-015: Add Security Audit Checklist
**Status**: `pending`  
**Description**: Pre-deployment security verification  
**Why**: Prevent security regressions  
**Effort**: 1 hour  
**Checklist**:
  - JWT secrets not in logs ✓
  - S3 buckets not public ✓
  - DynamoDB encryption enabled ✓
  - IAM roles use least privilege ✓
  - Network policies restrict traffic ✓  
**Files**: `docs/SECURITY_CHECKLIST.md` (new)  
**Blocked By**: None

---

## Current Sprint

**None yet** (waiting for priorities)

---

## In Progress

**None yet**

---

## Completed ✅

### Initial Setup
- ✅ TASK-000: Create multi-module Maven project structure
- ✅ TASK-000: Implement TenantAwareFilter for multi-tenant context
- ✅ TASK-000: Add Spring AI ChatClient integration (Analytics Service)
- ✅ TASK-000: Add S3 integration (ReportService)
- ✅ TASK-000: Add EventBridge publishers (both services)
- ✅ TASK-000: Create Terraform infrastructure templates
- ✅ TASK-000: Create Kubernetes manifests
- ✅ TASK-000: Setup GitHub Actions CI/CD
- ✅ TASK-000: Write comprehensive documentation
- ✅ TASK-000: Create CLAUDE.md and session memory

### Sprint 1 (11 hours)
- ✅ TASK-010: Add Integration Tests with Testcontainers (4h, completed 2026-06-06)
  - 23 integration test cases
  - Testcontainers + LocalStack setup
  - Multi-tenant isolation verification
  - JWT validation tests
  - S3 and EventBridge integration tests
  - Created TESTING.md guide

---

## How to Add a New Task

```markdown
#### TASK-XXX: Task Title
**Status**: `pending`  
**Description**: What and why  
**Effort**: X hours  
**Files**: Which files to modify  
**Dependencies**: Other tasks  
**Blocked By**: Task blocking this  
**Notes**: Additional context
```

## Task States

- **pending** — Not started, waiting for work
- **in_progress** — Currently being worked on
- **completed** — Finished and tested
- **blocked** — Blocked by another task
- **on_hold** — Intentionally paused

## Updating Tasks

**Start work:**
```bash
# Change status to in_progress
git commit -m "work: TASK-001 start streaming support"
```

**Finish work:**
```bash
# Move to completed, run tests first
mvn verify
git commit -m "feat: TASK-001 add Spring AI streaming support"
```

## Priority Guide

1. **High** — Blocks other work, critical for next release
2. **Medium** — Nice to have, good for productivity
3. **Low** — Backlog, can wait

---

## Notes

- Tasks in **Backlog** are not prioritized yet
- Update this file when starting/finishing a task
- Reference task numbers in commit messages: `feat: TASK-001 add...`
- Dependencies help identify critical path
- Blocked tasks will be unblocked as dependencies complete

