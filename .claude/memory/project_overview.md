---
name: SaaS Platform POC - Project Overview
description: Complete project context and current state
type: project
---

## Project Status
- **Name**: SaaS Platform POC
- **Location**: d:/JAVA/saas-platform-poc
- **Status**: ✅ Complete, tested, committed to git
- **Last Updated**: 2026-06-06

## What Was Built
Multi-tenant SaaS analytics platform with 2 Spring Boot microservices demonstrating cloud-native architecture across all AWS layers (Cognito → API GW → ALB → EKS → DynamoDB/S3/EventBridge).

## Key Accomplishments
- 38 files created (15 Java classes, 4 POMs, 6 docs, 2 Docker, 3 K8s, 2 Terraform)
- 2,489 lines of production-grade Java code
- 1,731 lines of comprehensive documentation
- 3 git commits (all code committed)
- Ready for local dev (docker-compose) and AWS deployment (Terraform)

## Git History
```
7f55b4d test: add comprehensive project validation summary
be6dcfb docs: add comprehensive project documentation
42a7afe feat: scaffold multi-module Maven project with common + analytics + ingestion services
```

## Architecture Layers
1. Cognito - JWT issuance, token validation
2. API Gateway - Public surface, routing, WAF
3. ALB - Load balancing to EKS pods
4. EKS/Kubernetes - Pod orchestration, auto-scaling
5. Spring Filters - RequestContext, MDC, trace ID propagation
6. Services - Business logic, Spring AI, EventBridge
7. DynamoDB - State persistence
8. S3 - File storage (tenant-scoped)
9. EventBridge - Async event routing
10. Lambda - Event handlers (scaffolded)

## Multi-Tenant Features
- RequestContext with tenantId, userId, correlationId, traceId
- TenantAwareFilter extracts tenant from JWT claims
- ThreadLocal context propagation (RequestContextHolder)
- S3 key scoping: s3://bucket/{tenantId}/...
- DynamoDB partition key includes tenantId
- MDC logging with tenant_id for audit

## Services & Endpoints
**Analytics Service (:8080)**
- POST /api/v1/analytics/query - Claude AI insights via Spring AI
- GET /api/v1/analytics/reports/{id} - Presigned S3 URL
- GET /api/v1/analytics/health - Health check

**Ingestion Service (:8081)**
- POST /api/v1/ingestion/upload - File upload to S3
- GET /api/v1/ingestion/status/{id} - Job status
- GET /api/v1/ingestion/health - Health check

## Tech Stack
- Java 21, Spring Boot 3.3.4, Spring AI 1.0.0 GA
- AWS SDK 2.25.0 (S3, DynamoDB, EventBridge)
- Docker, Kubernetes 1.29+, Terraform 1.0+
- GitHub Actions CI/CD
- Testcontainers, Lombok, JWT (jjwt)

## How to Use in Future Sessions
1. Read CLAUDE.md in project root for quick reference
2. Check git log for recent changes
3. Run `docker-compose up -d` for local development
4. Follow existing patterns (packages, annotations, service structure)
5. Test locally before committing
6. Keep documentation updated

## Testing
- Local: `docker-compose up -d` (LocalStack + 2 services)
- Build: `mvn clean install` (all modules)
- Tests: `mvn verify` (integration + unit)
- E2E: curl examples in LOCAL_DEV.md

## Deployment
- Local: `docker-compose up -d`
- AWS: `cd infrastructure/terraform && terraform apply && kubectl apply -f ../k8s/`
- Push: `git remote add origin <url> && git push -u origin main`

## Key Files
- CLAUDE.md - Project initialization guide
- ARCHITECTURE.md - Layer responsibilities, data flow, security
- LOCAL_DEV.md - Dev setup, curl examples, troubleshooting
- AWS_DEPLOYMENT.md - Step-by-step AWS deployment
- EVENTS.md - EventBridge event schemas
- .claude/settings.json - Claude Code configuration
