# Local Development Guide

Get the SaaS Platform POC running locally using Docker Compose and LocalStack.

## Prerequisites

- **Docker & Docker Compose** (v20.10+)
- **Maven** (3.9+)
- **Java 21** (Eclipse Temurin or OpenJDK)
- **curl** or **Postman** for API testing
- **Git**

## Environment Setup

### 1. Clone the Repository

```bash
cd d:/JAVA
git clone <repo-url> saas-platform-poc
cd saas-platform-poc
```

### 2. Set Environment Variables

```bash
# Copy the example environment file
cp .env.example .env

# Edit .env with your actual keys
export ANTHROPIC_API_KEY=sk-ant-your-key
export OPENAI_API_KEY=sk-your-key
```

Or source it:
```bash
source .env  # on Linux/macOS
set -a && source .env && set +a  # bash on Windows Git Bash
```

## Starting the Stack

### Option A: Docker Compose (Recommended for Local Dev)

Starts both services + LocalStack in containers:

```bash
docker-compose up -d

# Wait for services to be healthy (~30s)
docker-compose logs -f

# Check health
curl http://localhost:8080/api/v1/analytics/health
curl http://localhost:8081/api/v1/ingestion/health
```

**Stopping**:
```bash
docker-compose down

# Clean up volumes
docker-compose down -v
```

### Option B: Manual - Start Services Locally

#### 1. Start LocalStack

```bash
docker run -d \
  --name localstack-poc \
  -p 4566:4566 \
  -e SERVICES=s3,dynamodb,eventbridge,sqs,sns \
  -e DEBUG=1 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  localstack/localstack:latest

# Wait for LocalStack to be ready
sleep 10
```

#### 2. Build the Project

```bash
cd d:/JAVA/saas-platform-poc
mvn clean install -DskipTests
```

#### 3. Start Analytics Service

```bash
cd analytics-service
SPRING_PROFILES_ACTIVE=local \
AWS_ENDPOINT_OVERRIDE=http://localhost:4566 \
ANTHROPIC_API_KEY=sk-ant-test \
OPENAI_API_KEY=sk-test \
mvn spring-boot:run
```

Service starts on `http://localhost:8080`

#### 4. Start Ingestion Service (in another terminal)

```bash
cd ingestion-service
SPRING_PROFILES_ACTIVE=local \
AWS_ENDPOINT_OVERRIDE=http://localhost:4566 \
ANTHROPIC_API_KEY=sk-ant-test \
mvn spring-boot:run
```

Service starts on `http://localhost:8081`

## Testing the APIs

### Generate a Test JWT Token

For local testing, we use a simple JWT (unsigned for ease):

```bash
# This is a test token with tenant_id="tenant-a", sub="user123"
export TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwidGVuYW50X2lkIjoidGVuYW50LWEiLCJpYXQiOjE2NzA0NzE2MjN9.test"

# Or use a real JWT generation script:
cat > generate_jwt.sh <<'EOF'
#!/bin/bash
SECRET="test-secret-key-that-is-long-enough-for-testing-purposes"
TENANT_ID="${1:-tenant-a}"
USER_ID="${2:-user123}"

# Install jq if needed: apt-get install jq
# Note: This uses a simple base64 encoding; real JWTs require proper signing

HEADER=$(echo -n '{"alg":"HS256","typ":"JWT"}' | base64 | tr -d '=' | tr '+/' '-_')
PAYLOAD=$(echo -n "{\"sub\":\"$USER_ID\",\"tenant_id\":\"$TENANT_ID\",\"iat\":$(date +%s)}" | base64 | tr -d '=' | tr '+/' '-_')

# For testing, we'll just use the mock token above
echo "Mock JWT (for local testing): $TOKEN"
EOF

chmod +x generate_jwt.sh
./generate_jwt.sh tenant-a user123
```

### 1. Test Analytics Service

#### Generate AI Insights

```bash
curl -X POST http://localhost:8080/api/v1/analytics/query \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwidGVuYW50X2lkIjoidGVuYW50LWEifQ.test" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Monthly Revenue Analysis",
    "metrics": "revenue: $125K, growth: +15%, churn: 2%",
    "dateRange": "2024-01-01 to 2024-01-31"
  }'
```

Response:
```json
{
  "reportId": "abc-123-def-456",
  "reportUrl": "https://s3.amazonaws.com/saas-poc-reports/tenant-a/reports/abc-123.json?...",
  "tenantId": "tenant-a"
}
```

#### Retrieve a Report

```bash
REPORT_ID="abc-123-def-456"

curl -X GET http://localhost:8080/api/v1/analytics/reports/$REPORT_ID \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwidGVuYW50X2lkIjoidGVuYW50LWEifQ.test"
```

#### Health Check

```bash
curl http://localhost:8080/api/v1/analytics/health
```

### 2. Test Ingestion Service

#### Upload a File

```bash
# Create a test CSV file
cat > test_data.csv <<'EOF'
user_id,event_type,timestamp,value
user_1,click,2024-01-01T10:00:00Z,1
user_2,click,2024-01-01T10:01:00Z,1
user_3,purchase,2024-01-01T10:02:00Z,99.99
EOF

# Upload
curl -X POST http://localhost:8081/api/v1/ingestion/upload \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwidGVuYW50X2lkIjoidGVuYW50LWEifQ.test" \
  -F "file=@test_data.csv"
```

Response:
```json
{
  "fileId": "file-123",
  "fileName": "test_data.csv",
  "status": "PENDING",
  "tenantId": "tenant-a"
}
```

#### Check Ingestion Status

```bash
curl -X GET http://localhost:8081/api/v1/ingestion/status/file-123 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwidGVuYW50X2lkIjoidGVuYW50LWEifQ.test"
```

#### Health Check

```bash
curl http://localhost:8081/api/v1/ingestion/health
```

## Viewing LocalStack Services

### AWS CLI with LocalStack

```bash
# List S3 buckets
aws s3 ls --endpoint-url=http://localhost:4566

# List DynamoDB tables
aws dynamodb list-tables --endpoint-url=http://localhost:4566

# View S3 objects
aws s3 ls s3://saas-poc-reports --endpoint-url=http://localhost:4566 --recursive
```

### LocalStack Web UI

Open http://localhost:4571 in your browser to view LocalStack events.

## Debugging

### View Service Logs

```bash
# Analytics Service logs
docker-compose logs -f analytics-service

# Ingestion Service logs
docker-compose logs -f ingestion-service

# LocalStack logs
docker-compose logs -f localstack
```

### Enable DEBUG Logging

Edit `analytics-service/src/main/resources/application.yml`:

```yaml
logging:
  level:
    com.saasplatform: DEBUG
    org.springframework: DEBUG
```

### Test Multi-Tenant Isolation

Try accessing Tenant B's data as Tenant A:

```bash
# Token for tenant-a
TENANT_A_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwidGVuYW50X2lkIjoidGVuYW50LWEifQ.test"

# Try to access with this token, but request as if from tenant-b
# This should fail or be isolated
curl http://localhost:8080/api/v1/analytics/health \
  -H "Authorization: Bearer $TENANT_A_TOKEN" \
  -H "X-Tenant-ID: tenant-b"  # (Not used in actual auth, just for testing)
```

## Troubleshooting

### Services won't start

```bash
# Check if ports are in use
lsof -i :8080
lsof -i :8081
lsof -i :4566

# Kill the process if needed
kill -9 <PID>
```

### LocalStack not responding

```bash
# Check if LocalStack is running
docker ps | grep localstack

# Restart
docker-compose down -v
docker-compose up -d localstack
```

### JWT Validation Fails

Ensure the token is in the correct format:

```bash
# Token should be: header.payload.signature
# For local testing, the signature validation is lenient; just ensure format is valid
```

### Out of Memory

Increase Docker memory:

```bash
# In Docker Desktop settings, increase memory to 4GB+
docker system prune -a  # Clean up
```

## Code Organization for Local Dev

```
analytics-service/
├── src/main/resources/application.yml  ← Change logging level here
├── src/test/java/...                   ← Add integration tests
└── src/main/java/com/saasplatform/analytics/
    ├── config/AnalyticsAiConfig.java
    ├── service/InsightGenerationService.java
    └── controller/AnalyticsController.java
```

## Next Steps

1. **Run tests locally**: `mvn verify`
2. **Add more endpoints**: Create new controllers/services in each module
3. **Deploy to AWS**: Follow [AWS_DEPLOYMENT.md](./AWS_DEPLOYMENT.md)
4. **Monitor**: Set up Datadog or CloudWatch integration
