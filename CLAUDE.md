# CLAUDE.md - SaaS Platform POC Project Guide

## Project Overview

**SaaS Platform POC** is a production-grade multi-tenant SaaS analytics platform demonstrating cloud-native architecture with Spring Boot 3.3.4, Spring AI 1.0.0, and AWS services.

**Location**: `d:/JAVA/saas-platform-poc`  
**Status**: Complete, tested, committed to git  
**Tech Stack**: Java 21, Spring Boot, Spring AI, AWS (Cognito, EKS, DynamoDB, S3, EventBridge), Terraform, Kubernetes

---

## Quick Commands

### Local Development
```bash
# Start everything locally with LocalStack
docker-compose up -d

# View logs
docker-compose logs -f analytics-service
docker-compose logs -f ingestion-service

# Test endpoints
curl http://localhost:8080/api/v1/analytics/health
curl http://localhost:8081/api/v1/ingestion/health

# Stop
docker-compose down
```

### Build & Test
```bash
# Build all modules (requires Maven 3.9+ and Java 21)
mvn clean install

# Run specific service tests
cd analytics-service && mvn test

# Full integration tests
mvn verify
```

### AWS Deployment
```bash
cd infrastructure/terraform
terraform init
terraform plan
terraform apply

# Deploy to EKS
kubectl apply -f ../k8s/
```

### Git Operations
```bash
# Check status
git status

# View commits
git log --oneline

# Push to remote
git remote add origin <repo-url>
git push -u origin main
```

---

## Project Structure

```
saas-platform-poc/
├── pom.xml                          # Root Maven POM (3 modules)
├── Dockerfile                       # Multi-stage build
├── docker-compose.yml               # LocalStack + services
├── README.md                        # Quick start guide
├── ARCHITECTURE.md                  # Layer-by-layer design
├── LOCAL_DEV.md                     # Dev setup details
├── AWS_DEPLOYMENT.md                # Terraform → EKS
├── EVENTS.md                        # EventBridge schemas
├── TEST_SUMMARY.md                  # Validation checklist
├── CLAUDE.md                        # This file
│
├── saas-platform-common/            # Shared multi-tenant library
│   ├── pom.xml
│   └── src/main/java/com/saasplatform/
│       ├── model/Tenant.java
│       ├── context/RequestContext.java
│       ├── context/RequestContextHolder.java
│       ├── filter/TenantAwareFilter.java
│       └── exception/TenantNotFound.java
│
├── analytics-service/               # AI-powered analytics
│   ├── pom.xml
│   ├── src/main/resources/application.yml
│   └── src/main/java/com/saasplatform/analytics/
│       ├── AnalyticsServiceApplication.java
│       ├── config/AnalyticsAiConfig.java (Spring AI ChatClient)
│       ├── service/InsightGenerationService.java (Claude integration)
│       ├── service/ReportService.java (S3 operations)
│       ├── service/EventPublisher.java (EventBridge)
│       └── controller/AnalyticsController.java (REST endpoints)
│
├── ingestion-service/               # File ingestion
│   ├── pom.xml
│   ├── src/main/resources/application.yml
│   └── src/main/java/com/saasplatform/ingestion/
│       ├── IngestionServiceApplication.java
│       ├── service/FileIngestionService.java (S3 upload)
│       ├── service/EventPublisher.java (EventBridge)
│       └── controller/IngestionController.java (REST endpoints)
│
└── infrastructure/
    ├── terraform/
    │   ├── main.tf (VPC, EKS, DynamoDB, S3, EventBridge, Cognito, ALB)
    │   └── variables.tf (Region, node counts, billing mode)
    └── k8s/
        ├── namespace.yaml (Secrets, network policies)
        ├── analytics-deployment.yaml (2 replicas, probes, PDB)
        └── ingestion-deployment.yaml (2 replicas, PDB, security)

└── .github/workflows/
    ├── build-and-test.yml (Maven build, tests, Docker image)
    └── deploy-to-eks.yml (ECR push, kubectl deploy, smoke tests)
```

---

## Core Architecture

### Layers (From Desktop Images)

1. **Cognito** → Token issuance, JWT validation
2. **API Gateway** → Public surface, routing, JWT validation
3. **ALB** → Load balancing to EKS pods
4. **EKS/Kubernetes** → Pod scheduling, auto-scaling, rolling deploys
5. **Spring Filters** → TenantAwareFilter (RequestContext, MDC, trace IDs)
6. **Controllers** → REST endpoints (/analytics/query, /ingestion/upload)
7. **Services** → Business logic (InsightGenerationService, FileIngestionService)
8. **DynamoDB** → Atomic state persistence
9. **S3** → File storage (tenant-scoped keys)
10. **EventBridge** → Async event routing (QueryCompleted, FileUploaded)

### Multi-Tenant Isolation

- **RequestContext**: `{ tenantId, userId, correlationId, traceId, metadata }`
- **ThreadLocal**: RequestContextHolder for downstream access
- **JWT Claims**: Tenant extracted from `tenant_id` claim
- **S3 Scoping**: `s3://bucket/{tenantId}/...`
- **DynamoDB**: Partition key includes tenantId
- **Logging**: MDC includes `tenant_id` for audit trails

### Key Services

#### Analytics Service (:8080)
- **POST /api/v1/analytics/query**: Input query → Claude (Spring AI) → InsightReport → S3 → presigned URL
- **GET /api/v1/analytics/reports/{id}**: Fetch presigned URL
- **GET /api/v1/analytics/health**: Health check
- **Spring AI**: ChatClient bean, BeanOutputConverter for structured output
- **EventBridge**: Publishes `QueryCompleted` events

#### Ingestion Service (:8081)
- **POST /api/v1/ingestion/upload**: Multipart file → S3 (tenant-scoped)
- **GET /api/v1/ingestion/status/{id}**: Check job status
- **GET /api/v1/ingestion/health**: Health check
- **EventBridge**: Publishes `FileUploaded` events

---

## Dependencies & Versions

```
Java:               21
Spring Boot:        3.3.4
Spring AI:          1.0.0 GA
AWS SDK:            2.25.0
Maven:              3.9.9
Kubernetes:         1.29+
Terraform:          1.0+
Docker:             20.10+
```

### Key Maven Dependencies

- `spring-boot-starter-web`, `spring-boot-starter-webflux`
- `spring-ai-starter-anthropic`, `spring-ai-starter-openai`
- `software.amazon.awssdk:s3`, `dynamodb`, `eventbridge`
- `io.jsonwebtoken:jjwt-api` (JWT handling)
- `org.testcontainers:testcontainers` (integration tests)
- `org.projectlombok:lombok` (boilerplate)

---

## Configuration

### Local Development (.env)
```bash
ANTHROPIC_API_KEY=sk-ant-your-key
OPENAI_API_KEY=sk-your-key
AWS_REGION=us-east-1
AWS_ENDPOINT_OVERRIDE=http://localstack:4566  # LocalStack
SPRING_PROFILES_ACTIVE=local
```

### AWS Deployment
- Create Cognito user pool
- Set up API Gateway with JWT authorizer
- Deploy EKS cluster via Terraform
- Configure IAM roles for pods
- Store secrets in Kubernetes (kubectl create secret)

### Spring Profiles
- **local**: Uses LocalStack, mock AWS services
- **aws**: Real AWS services (Cognito, EKS, DynamoDB, etc.)

---

## Development Workflow

### Making Changes

1. **Edit Java code** in `analytics-service/` or `ingestion-service/`
2. **Rebuild**: `mvn clean install` (or let IDE rebuild)
3. **Test locally**: `docker-compose down && docker-compose up -d`
4. **Test endpoints**: Use curl or Postman (see LOCAL_DEV.md for examples)
5. **Commit**: `git add . && git commit -m "feat: ..."`

### Adding New Endpoints

1. Create controller method in `AnalyticsController` or `IngestionController`
2. Add service method if needed
3. Update `application.yml` if new config
4. Test with curl (see LOCAL_DEV.md)
5. Update documentation (README, ARCHITECTURE)
6. Commit

### Adding New Services

1. Create new module: `other-service/`
2. Copy `pom.xml` template from existing service
3. Create `src/main/java/com/saasplatform/other/` structure
4. Add to root `pom.xml` `<modules>`
5. Update docker-compose.yml
6. Update Kubernetes manifests

---

## Testing & Quality

### Unit Tests
```bash
mvn test
```

### Integration Tests (with LocalStack)
```bash
mvn verify
```

### Local E2E (docker-compose)
```bash
docker-compose up -d
# Wait 30s for services to be healthy
curl http://localhost:8080/api/v1/analytics/health
```

### Code Quality
- No comments unless necessary (names are self-documenting)
- Spring annotations (@Component, @Service, @RestController)
- Records for immutable DTOs
- Lombok for boilerplate (@Slf4j, @RequiredArgsConstructor)
- Proper exception handling

---

## Deployment

### Local (docker-compose)
```bash
docker-compose up -d
```
Services on :8080 (analytics) and :8081 (ingestion). LocalStack handles S3, DynamoDB, EventBridge.

### AWS (Terraform + kubectl)
1. `cd infrastructure/terraform && terraform apply` (creates VPC, EKS, DynamoDB, S3, etc.)
2. Build Docker images: `docker build -t ... --build-arg SERVICE_NAME=analytics-service .`
3. Push to ECR: `aws ecr get-login-password | docker login ...`
4. Update image URIs in Kubernetes manifests
5. `kubectl apply -f infrastructure/k8s/`

See AWS_DEPLOYMENT.md for step-by-step instructions.

---

## Documentation

| File | Purpose |
|------|---------|
| README.md | Quick start, API overview |
| ARCHITECTURE.md | Layer responsibilities, data flow, security |
| LOCAL_DEV.md | Dev setup, curl examples, troubleshooting |
| AWS_DEPLOYMENT.md | Terraform → EKS deployment steps |
| EVENTS.md | EventBridge event schemas and consumers |
| TEST_SUMMARY.md | Project validation checklist |
| CLAUDE.md | This file (project initialization) |

---

## Common Tasks

### Generate Test JWT Token
```bash
# For local testing (unsigned for simplicity)
export TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwidGVuYW50X2lkIjoidGVuYW50LWEifQ.test"

# Then use in requests
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/analytics/health
```

### View Service Logs
```bash
docker-compose logs -f analytics-service
docker-compose logs -f ingestion-service
```

### Reset LocalStack
```bash
docker-compose down -v    # Remove volumes
docker-compose up -d      # Start fresh
```

### Check Git Status
```bash
git status        # Uncommitted changes
git log --oneline # Recent commits
git diff          # Specific changes
```

### Push to GitHub
```bash
git remote add origin https://github.com/user/saas-platform-poc
git push -u origin main
```

---

## Troubleshooting

### Services won't start
- Check ports: `lsof -i :8080`, `lsof -i :8081`, `lsof -i :4566`
- Check Docker: `docker ps`
- Check logs: `docker-compose logs`

### LocalStack not responding
- Restart: `docker-compose down -v && docker-compose up -d`
- Wait 30s for health checks to pass

### JWT validation fails
- Ensure token format: `header.payload.signature`
- Decode at jwt.io to inspect claims
- Check `tenant_id` claim is present

### Out of memory
- Increase Docker memory (Docker Desktop settings)
- Run: `docker system prune -a`

---

## Git Workflow

### Current State
- **Branch**: main
- **Commits**: 3 (feat: scaffold, docs: comprehensive, test: validation)
- **Status**: Clean (all changes committed)

### Making Commits
```bash
git add <files>
git commit -m "type: description

Longer explanation if needed."
```

Types: `feat:` (feature), `fix:` (bug), `docs:` (documentation), `test:` (testing), `refactor:`, `chore:`

---

## Contact & Next Steps

### For Claude in Future Sessions
1. Read CLAUDE.md (this file) first
2. Check git log for recent changes
3. Review documentation (ARCHITECTURE.md, LOCAL_DEV.md)
4. Follow project structure conventions
5. Run tests before committing

### To Extend the Project
1. **Add Spring AI features**: Streaming, memory, advisors (see medai patterns)
2. **Add Lambda handlers**: DLQ processing, document processor
3. **Add monitoring**: Datadog, CloudWatch integration
4. **Add tests**: Unit, integration, E2E with Testcontainers
5. **Add security**: OAuth2, RBAC, encryption at rest

---

## Related Projects

This project reuses patterns from:
- **medai** (Spring AI, pgvector, ChatClient, BeanOutputConverter)
- **Spring_AI_RAG** (Spring AI course patterns)
- **portfolio-website** (GitHub Actions, GitHub Pages)

See `d:/JAVA/medai/` for similar Spring AI setup.

---

**Last Updated**: 2026-06-06  
**Project Status**: ✅ Complete, tested, ready for local dev and AWS deployment
