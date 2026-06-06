# Testing Guide - SaaS Platform POC

## Test Structure

The project includes comprehensive integration tests using Testcontainers with LocalStack for AWS service emulation.

### Test Directories

```
analytics-service/
├── src/test/java/com/saasplatform/analytics/integration/
│   ├── LocalStackTestBase.java              # Base class with LocalStack setup
│   ├── AnalyticsControllerIntegrationTest.java  # REST API tests
│   ├── ReportServiceIntegrationTest.java    # S3 operations tests
│   └── EventPublisherIntegrationTest.java   # EventBridge tests

ingestion-service/
├── src/test/java/com/saasplatform/ingestion/integration/
│   ├── LocalStackTestBase.java              # Base class with LocalStack setup
│   ├── IngestionControllerIntegrationTest.java  # REST API tests
│   └── FileIngestionServiceIntegrationTest.java # S3 upload tests
```

---

## Test Coverage

### Analytics Service Tests (11 test cases)

#### AnalyticsControllerIntegrationTest
- ✅ Successfully process analytics query and return report
- ✅ Return 401 for invalid JWT token
- ✅ Return 401 for missing Authorization header
- ✅ Tenant isolation - report not accessible by cross-tenant
- ✅ Health check endpoint
- ✅ Multi-tenant context propagation

#### ReportServiceIntegrationTest
- ✅ Save report to S3 with tenant-scoped key
- ✅ Generate presigned URL for report
- ✅ Include tenant ID in S3 key path
- ✅ Return different presigned URLs for different reports

#### EventPublisherIntegrationTest
- ✅ Successfully publish query completed event
- ✅ Include tenant context in published event
- ✅ Handle multiple sequential event publications
- ✅ EventBridge bus availability
- ✅ Resilient error handling when bus unavailable

### Ingestion Service Tests (12 test cases)

#### IngestionControllerIntegrationTest
- ✅ Successfully upload file and return file ID
- ✅ Reject file upload without Authorization header
- ✅ Reject file upload with invalid JWT token
- ✅ Support different file formats (CSV, JSON)
- ✅ Return ingestion job status
- ✅ Enforce tenant isolation for ingestion jobs
- ✅ Health check endpoint
- ✅ Handle concurrent uploads from different tenants

#### FileIngestionServiceIntegrationTest
- ✅ Successfully upload file to S3
- ✅ Store file with tenant-scoped key
- ✅ Handle different file formats
- ✅ Generate unique file IDs for each upload
- ✅ Preserve original filename metadata
- ✅ Handle empty files
- ✅ Handle large files (1MB+)

---

## Running Tests

### All Tests
```bash
cd saas-platform-poc
mvn clean verify
```

### Analytics Service Only
```bash
cd analytics-service
mvn clean verify
```

### Ingestion Service Only
```bash
cd ingestion-service
mvn clean verify
```

### Single Test Class
```bash
mvn test -Dtest=AnalyticsControllerIntegrationTest
```

### Single Test Method
```bash
mvn test -Dtest=AnalyticsControllerIntegrationTest#testQueryInsights_Success
```

### With Coverage Report
```bash
mvn clean verify jacoco:report
# Report: target/site/jacoco/index.html
```

---

## Test Scenarios

### 1. Analytics Query Flow
**Test**: `testQueryInsights_Success`

Tests the complete analytics flow:
1. Client sends JWT token with tenant ID
2. TenantAwareFilter extracts tenant context
3. Controller receives request with tenant context
4. Service generates insights via Spring AI
5. Report saved to S3 with tenant-scoped key
6. Presigned URL generated
7. Event published to EventBridge

**Expected Result**: 200 OK with `reportId`, `reportUrl`, `tenantId`

### 2. Multi-Tenant Isolation
**Tests**: 
- `testMultiTenantContextPropagation` (Analytics)
- `testTenantIsolation_IngestionJobs` (Ingestion)

Verifies that:
- Tenant A's requests use tenant A context
- Tenant B's requests use tenant B context
- No data leakage between tenants
- S3 keys include tenant prefix

**Expected Result**: Each tenant sees only their own data

### 3. JWT Validation
**Tests**:
- `testQueryInsights_InvalidToken` (Analytics)
- `testUploadFile_InvalidToken` (Ingestion)

Verifies that:
- Invalid JWT tokens are rejected
- Missing Authorization header returns 401
- Token claims are properly extracted

**Expected Result**: 401 Unauthorized for invalid/missing tokens

### 4. File Upload with EventBridge
**Test**: `testUploadFile_Success`

Tests file ingestion flow:
1. Client uploads file with valid JWT
2. File stored in S3 with tenant-scoped key
3. File ID returned to client
4. Event published to EventBridge
5. Other services can react to upload event

**Expected Result**: 200 OK with `fileId`, `fileName`, `status: PENDING`

### 5. S3 Operations
**Tests**:
- `testSaveReport_Success` (Analytics)
- `testUploadFile_Success` (Ingestion)

Verifies:
- Files are stored in correct S3 buckets
- Tenant-scoped key paths are used
- Presigned URLs are generated correctly
- Files can be retrieved

**Expected Result**: S3 operations succeed without errors

### 6. EventBridge Publishing
**Tests**:
- `testPublishQueryCompleted_Success` (Analytics)
- Event publishing with tenant context

Verifies:
- Events are published to EventBridge
- Tenant context is included in event
- Multiple events can be published sequentially

**Expected Result**: Events published without blocking

---

## LocalStack Setup

Tests use **Testcontainers** with **LocalStack** to emulate AWS services:

```
Services Emulated:
├── S3 (object storage)
├── DynamoDB (NoSQL database)
└── EventBridge (event routing)
```

### LocalStack Initialization

```java
@Container
static LocalStackContainer localStack = new LocalStackContainer(
    DockerImageName.parse("localstack/localstack:latest")
).withServices(
    LocalStackContainer.Service.S3,
    LocalStackContainer.Service.DYNAMODB,
    LocalStackContainer.Service.EVENTBRIDGE
);
```

### Dynamic Properties

Spring Boot dynamically configures AWS endpoints to point to LocalStack:
```yaml
aws.endpoint: http://localhost:4566
aws.region: us-east-1
```

---

## JWT Token Generation in Tests

Tests generate JWT tokens manually for testing:

```java
String validToken = Jwts.builder()
    .setSubject(userId)
    .claim("tenant_id", tenantId)
    .claim("user_id", userId)
    .setIssuedAt(new Date())
    .setExpiration(new Date(System.currentTimeMillis() + 3600000))
    .signWith(SignatureAlgorithm.HS256, JWT_SECRET.getBytes())
    .compact();
```

This allows tests to:
- Create valid tokens with specific tenant IDs
- Create invalid tokens for negative testing
- Test token expiration scenarios

---

## Test Profiles

Tests use `test` Spring profile (`application-test.yml`):

```yaml
spring.profiles.active: test
server.port: 0  # Random port for isolation
logging.level: DEBUG  # Detailed logs
aws.region: us-east-1
```

Benefits:
- Each test class gets a fresh Spring context
- Uses LocalStack instead of real AWS
- Random ports prevent port conflicts
- Debug logging for troubleshooting

---

## Mocking Strategy

### What's Mocked
- Spring AI `ChatClient` (AI responses are mocked)
  - Reason: Testing service logic, not Claude API
  - Implementation: `@MockBean` in controller tests

### What's Real
- AWS S3 Client (uses LocalStack)
- AWS DynamoDB Client (uses LocalStack)
- AWS EventBridge Client (uses LocalStack)
- Spring Web Filter Chain (tests real HTTP flow)
- JWT token generation and validation

**Rationale**: Test actual integration with AWS services while mocking expensive/external AI API calls.

---

## Performance Characteristics

### Test Execution Time
- **Unit Tests**: ~2 seconds
- **Integration Tests**: ~30 seconds (LocalStack startup overhead)
- **Full Suite**: ~45 seconds total

### First Run
- LocalStack Docker image download: ~3-5 minutes
- Subsequent runs: ~45 seconds (image cached)

### Resource Usage
- **Memory**: ~500MB per test class
- **Disk**: ~2GB for Docker images
- **CPU**: ~2 cores (parallel test execution)

---

## CI/CD Integration

### GitHub Actions (.github/workflows/build-and-test.yml)

```yaml
- name: Run Tests
  run: mvn clean verify

- name: Upload Test Results
  if: always()
  uses: actions/upload-artifact@v2
  with:
    name: test-results
    path: '**/target/surefire-reports/'
```

Tests run on every commit and pull request.

---

## Troubleshooting

### LocalStack Container Won't Start
```bash
# Check Docker is running
docker ps

# Pull latest LocalStack image
docker pull localstack/localstack:latest

# Check disk space
docker system df
```

### Tests Timeout
```bash
# Increase test timeout
mvn test -DtestFailureIgnore=true -Dsurefire.reuse.forks=false

# Run single test class
mvn test -Dtest=AnalyticsControllerIntegrationTest -X
```

### S3 Bucket Not Found
```bash
# Ensure bucket creation in test setup
s3Client.createBucket(b -> b.bucket("bucket-name"));
```

### EventBridge Events Not Published
```bash
# Create EventBridge bus in test setup
eventBridgeClient.createEventBus(b -> b.name("default"));
```

---

## Next Steps

### To Add New Tests
1. Create test class extending `LocalStackTestBase`
2. Use `@AutoConfigureMockMvc` for controller tests
3. Mock external dependencies (ChatClient, etc.)
4. Use `assertDoesNotThrow()` for blocking operations
5. Verify side effects (S3 uploads, events published)

### To Extend Coverage
- Add contract tests (TASK-011)
- Add performance tests (TASK-012)
- Add end-to-end tests with real AWS
- Add chaos engineering tests

---

## Related Files
- ARCHITECTURE.md — Overall design
- LOCAL_DEV.md — Local development setup
- AWS_DEPLOYMENT.md — Deployment guide
- EVENTS.md — EventBridge schemas

**Last Updated**: 2026-06-06
