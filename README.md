# SaaS Platform POC

A production-grade proof-of-concept demonstrating a multi-tenant SaaS platform on AWS with Spring Boot and Spring AI.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│ Cognito: Token issuance + validation                │
├─────────────────────────────────────────────────────┤
│ API Gateway: Public surface, WAF, JWT validation     │
├─────────────────────────────────────────────────────┤
│ ALB: Distribute traffic to EKS pods                 │
├─────────────────────────────────────────────────────┤
│ EKS: analytics-service, ingestion-service           │
├─────────────────────────────────────────────────────┤
│ DynamoDB: Tenant state, metrics, metadata           │
│ EventBridge: Async events (ingestion, analytics)    │
│ S3: Reports, data artifacts                         │
│ Lambda: Event handlers, DLQ processing              │
└─────────────────────────────────────────────────────┘
```

## Quick Start (Local Dev)

### Prerequisites
- Docker & Docker Compose
- Maven 3.9+
- Java 21
- ANTHROPIC_API_KEY and OPENAI_API_KEY environment variables

### Run Locally

```bash
# Start LocalStack + services
docker-compose up -d

# Build and install (optional if using docker-compose)
mvn clean install

# Both services start on :8080 and :8081 with health checks
# View logs: docker-compose logs -f analytics-service
```

### Example Requests (Local)

```bash
# Generate JWT token (for local testing)
export TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwidGVuYW50X2lkIjoidGVuYW50LWEifQ...."

# Query Analytics
curl -X POST http://localhost:8080/api/v1/analytics/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Monthly Revenue Analysis",
    "metrics": "revenue: $125K, growth: +15%, churn: 2%",
    "dateRange": "2024-01-01 to 2024-01-31"
  }'

# Upload File (Ingestion)
curl -X POST http://localhost:8081/api/v1/ingestion/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@data.csv"

# Health Checks
curl http://localhost:8080/api/v1/analytics/health
curl http://localhost:8081/api/v1/ingestion/health
```

## Project Structure

```
saas-platform-poc/
├── saas-platform-common/       # Shared library
│   ├── model/                  # Tenant, RequestContext
│   ├── context/                # RequestContextHolder
│   ├── filter/                 # TenantAwareFilter
│   └── exception/              # Custom exceptions
├── analytics-service/          # AI-powered analytics
│   ├── config/                 # Spring AI ChatClient
│   ├── service/                # Business logic
│   ├── controller/             # REST endpoints
│   └── resources/application.yml
├── ingestion-service/          # File upload & processing
│   ├── service/                # File handling, events
│   ├── controller/             # REST endpoints
│   └── resources/application.yml
├── infrastructure/
│   ├── terraform/              # AWS infrastructure (Cognito, EKS, DynamoDB, etc.)
│   └── k8s/                    # Kubernetes manifests
├── .github/workflows/          # CI/CD pipelines
├── Dockerfile                  # Multi-stage build
├── docker-compose.yml          # Local dev environment
└── pom.xml                     # Maven multi-module
```

## API Endpoints

### Analytics Service (8080)

- `POST /api/v1/analytics/query` - Generate AI insights
- `GET /api/v1/analytics/reports/{reportId}` - Retrieve report
- `GET /api/v1/analytics/health` - Health check

### Ingestion Service (8081)

- `POST /api/v1/ingestion/upload` - Upload file (multipart)
- `GET /api/v1/ingestion/status/{jobId}` - Check ingestion status
- `GET /api/v1/ingestion/health` - Health check

## Features

- **Multi-tenant Isolation**: Tenant context propagated via request filter
- **Spring AI Integration**: Claude 3.5 Sonnet for insights, OpenAI embeddings
- **Async Processing**: EventBridge for decoupled service communication
- **File Ingestion**: S3 storage with support for CSV, JSON, PDF
- **Production Patterns**: Error handling, logging, security, testing

## Testing

```bash
# Unit + Integration tests
mvn clean verify

# Single service
cd analytics-service && mvn test

# With coverage
mvn clean verify jacoco:report
```

## Deployment (AWS)

See `AWS_DEPLOYMENT.md` for full instructions:

```bash
cd infrastructure/terraform
terraform init
terraform apply

# Deploy Kubernetes manifests
kubectl apply -f ../k8s/
```

## Configuration

- **Local**: Set `spring.profiles.active=local` (docker-compose default)
- **AWS**: Set `spring.profiles.active=aws` and provide AWS credentials

### Environment Variables

```bash
# API Keys
export ANTHROPIC_API_KEY=sk-ant-...
export OPENAI_API_KEY=sk-...

# AWS (optional for local dev with LocalStack)
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
```

## Documentation

- [ARCHITECTURE.md](./ARCHITECTURE.md) - Layer-by-layer design
- [LOCAL_DEV.md](./LOCAL_DEV.md) - Detailed local setup
- [AWS_DEPLOYMENT.md](./AWS_DEPLOYMENT.md) - AWS deployment steps
- [EVENTS.md](./EVENTS.md) - EventBridge event schemas

## Technology Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.3.4, Spring AI 1.0.0 GA
- **AI**: Anthropic Claude 3.5 Sonnet
- **Cloud**: AWS (Cognito, API Gateway, ALB, EKS, DynamoDB, S3, EventBridge, Lambda)
- **Build**: Maven 3.9.9
- **Container**: Docker, Kubernetes
- **IaC**: Terraform

## License

MIT
