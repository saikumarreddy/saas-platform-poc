# EventBridge Events Guide

Document of all events published and consumed by the SaaS Platform POC.

## Overview

EventBridge bus: `saas-platform-events`

All events follow the AWS EventBridge standard format:
```json
{
  "version": "0",
  "id": "uuid",
  "detail-type": "EventType",
  "source": "service-name",
  "account": "123456789",
  "time": "2024-01-15T12:34:56Z",
  "region": "us-east-1",
  "resources": [],
  "detail": { }
}
```

## Published Events

### 1. QueryCompleted

**Source**: `analytics-service`  
**Detail Type**: `QueryCompleted`  
**Trigger**: After user submits analytics query

#### Payload

```json
{
  "tenantId": "tenant-a",
  "reportId": "uuid",
  "timestamp": "2024-01-15T12:34:56Z",
  "correlationId": "correlation-abc-123"
}
```

#### Schema

| Field | Type | Description |
|-------|------|-------------|
| tenantId | string | Tenant making the query |
| reportId | string | UUID of generated report |
| timestamp | ISO8601 | When report was generated |
| correlationId | string | Trace ID for request tracking |

#### Consumers (Examples)

- **Lambda**: Index report metadata in Elasticsearch for full-text search
- **SNS**: Send notification email to tenant admins
- **Analytics Service**: Auto-ingest for trend analysis

#### Retry Policy

- Max retries: 2
- Retry delay: 60 seconds (exponential backoff)
- DLQ: `saas-platform-dlq` (SQS)

---

### 2. FileUploaded

**Source**: `ingestion-service`  
**Detail Type**: `FileUploaded`  
**Trigger**: After file is uploaded to S3

#### Payload

```json
{
  "tenantId": "tenant-a",
  "fileId": "file-uuid",
  "fileName": "customer_data.csv",
  "timestamp": "2024-01-15T12:34:56Z",
  "correlationId": "correlation-xyz-789"
}
```

#### Schema

| Field | Type | Description |
|-------|------|-------------|
| tenantId | string | Tenant uploading file |
| fileId | string | UUID of uploaded file |
| fileName | string | Original file name |
| timestamp | ISO8601 | When file was uploaded |
| correlationId | string | Trace ID for request tracking |

#### Consumers

- **Lambda**: Document processor (chunk, embed, extract metadata)
- **SQS**: Queue for long-running processing
- **Analytics Service**: Auto-import for real-time analytics

#### Retry Policy

- Max retries: 3
- Retry delay: 30 seconds
- DLQ: `saas-platform-dlq` (SQS)

---

### 3. IngestionCompleted

**Source**: `ingestion-service` (via Lambda)  
**Detail Type**: `IngestionCompleted`  
**Trigger**: After document processing is complete

#### Payload

```json
{
  "tenantId": "tenant-a",
  "jobId": "ingestion-job-uuid",
  "fileId": "file-uuid",
  "status": "COMPLETED",
  "recordsProcessed": 1500,
  "metadataUrl": "s3://saas-poc-ingestion/tenant-a/metadata/job-uuid.json",
  "timestamp": "2024-01-15T12:45:00Z",
  "correlationId": "correlation-xyz-789"
}
```

#### Schema

| Field | Type | Description |
|-------|------|-------------|
| tenantId | string | Tenant |
| jobId | string | Ingestion job ID |
| fileId | string | Original file ID |
| status | enum | COMPLETED, FAILED |
| recordsProcessed | integer | Rows/documents processed |
| metadataUrl | string | S3 URL to metadata JSON |
| timestamp | ISO8601 | Completion time |
| correlationId | string | Original request trace ID |

#### Consumers

- **Analytics Service**: Update dashboard with new data
- **SNS**: Email tenant when processing complete
- **DynamoDB**: Update IngestionJob status

---

### 4. IngestionFailed

**Source**: `ingestion-service` (via Lambda)  
**Detail Type**: `IngestionFailed`  
**Trigger**: After document processing fails

#### Payload

```json
{
  "tenantId": "tenant-a",
  "jobId": "ingestion-job-uuid",
  "fileId": "file-uuid",
  "status": "FAILED",
  "error": "Unsupported file format: .docx",
  "timestamp": "2024-01-15T12:45:00Z",
  "correlationId": "correlation-xyz-789"
}
```

#### Schema

| Field | Type | Description |
|-------|------|-------------|
| tenantId | string | Tenant |
| jobId | string | Ingestion job ID |
| fileId | string | Original file ID |
| status | enum | FAILED |
| error | string | Error message |
| timestamp | ISO8601 | Failure time |
| correlationId | string | Original request trace ID |

#### Consumers

- **SNS**: Alert tenant of ingestion failure
- **Datadog**: Track error metrics
- **DynamoDB**: Update IngestionJob with error details

---

## EventBridge Rules

### Rule 1: Process Uploaded Files

**Name**: `process-file-uploads`  
**Event Pattern**:
```json
{
  "source": ["ingestion-service"],
  "detail-type": ["FileUploaded"]
}
```

**Targets**:
- Lambda: `document-processor`
- SQS: `ingestion-processing-queue`

**DLQ**: `saas-platform-dlq`

---

### Rule 2: Index Query Reports

**Name**: `index-reports`  
**Event Pattern**:
```json
{
  "source": ["analytics-service"],
  "detail-type": ["QueryCompleted"]
}
```

**Targets**:
- Lambda: `index-elasticsearch`

---

### Rule 3: DLQ Handler

**Name**: `handle-dlq-events`  
**Event Pattern**:
```json
{
  "source": ["sqs"],
  "detail-type": ["SQS Message"]
}
```

**Targets**:
- Lambda: `dead-letter-handler`
- SNS: `alerts`

---

## DLQ (Dead Letter Queue) Format

When an event fails after max retries, it's sent to `saas-platform-dlq`:

```json
{
  "original_event": { /* full event that failed */ },
  "reason": "Max retries exceeded",
  "attempt_count": 3,
  "last_error": "Connection timeout to Lambda",
  "timestamp": "2024-01-15T12:46:00Z"
}
```

### Lambda: dead-letter-handler

Processes failed events:

```python
def lambda_handler(event, context):
    messages = event['Records']
    
    for msg in messages:
        body = json.loads(msg['body'])
        original_event = body['original_event']
        tenant_id = original_event['detail']['tenantId']
        
        # Log failure
        log_failure(tenant_id, original_event)
        
        # Publish alert
        publish_alert_event({
            'tenantId': tenant_id,
            'eventType': original_event['detail-type'],
            'error': body['last_error']
        })
        
        # Delete message from queue (success)
        sqs.delete_message(...)
```

---

## Local Testing with LocalStack

### Check Events in LocalStack Console

```bash
# View event buses
aws events list-event-buses --endpoint-url=http://localhost:4566

# View rules
aws events list-rules --event-bus-name saas-platform-events \
  --endpoint-url=http://localhost:4566

# View event targets
aws events list-targets-by-rule \
  --rule process-file-uploads \
  --event-bus-name saas-platform-events \
  --endpoint-url=http://localhost:4566
```

### Publish Test Event

```bash
aws events put-events \
  --entries '[{
    "Source": "custom-test",
    "DetailType": "TestEvent",
    "Detail": "{\"message\": \"test\"}",
    "EventBusName": "saas-platform-events"
  }]' \
  --endpoint-url=http://localhost:4566
```

### Monitor SQS DLQ

```bash
# List messages in DLQ
aws sqs receive-message \
  --queue-url http://localhost:4566/123456789/saas-platform-dlq \
  --endpoint-url=http://localhost:4566
```

---

## Monitoring Events

### CloudWatch Metrics

EventBridge publishes metrics:

- `Invocations`: Number of times rule was triggered
- `FailedInvocations`: Number of failed invocations
- `Triggers`: Number of targets triggered
- `ThrottledRules`: Rules that were throttled

View in CloudWatch Dashboard:

```bash
aws cloudwatch get-metric-statistics \
  --namespace AWS/Events \
  --metric-name Invocations \
  --dimensions Name=RuleName,Value=process-file-uploads \
  --start-time 2024-01-15T00:00:00Z \
  --end-time 2024-01-15T23:59:59Z \
  --period 300 \
  --statistics Sum,Average
```

### Sample Datadog Integration

```yaml
# datadog/events.yaml
integrations:
  events:
    sources:
      - eventbridge
    
    events:
      - type: QueryCompleted
        alert_priority: info
        dashboard: analytics_insights
        
      - type: FileUploaded
        alert_priority: info
        dashboard: ingestion_pipeline
        
      - type: IngestionFailed
        alert_priority: warning
        email_notify: ops@example.com
```

---

## Event Lifecycle

### Successful Processing

```
1. Service publishes event → EventBridge
2. EventBridge matches rule → routes to target (Lambda/SQS)
3. Target processes event → success
4. Event is marked as processed
5. (Optional) Service receives success callback
```

### Failed Processing

```
1. Service publishes event → EventBridge
2. EventBridge routes to target
3. Target fails (exception/timeout)
4. EventBridge retries (3 times, 60s delay)
5. All retries fail
6. Event sent to DLQ
7. DLQ handler processes failure
8. Alert published (SNS, email, Slack)
```

---

## Best Practices

1. **Always include `correlationId`** in event details for distributed tracing
2. **Validate tenant isolation** in event consumers (check tenantId matches context)
3. **Use DLQ for debugging** – examine failed events for root causes
4. **Monitor DLQ size** – alerts if > 100 messages (indicates systemic failure)
5. **Set reasonable timeouts** – Lambda timeouts should be 60s+
6. **Implement idempotency** – event consumers should handle duplicate processing
7. **Test async flows locally** – use LocalStack to verify event routing

---

## Future Enhancements

- **CQRS Pattern**: Separate read/write models via events
- **Saga Pattern**: Multi-step orchestrations (e.g., file → embed → index → notify)
- **Event Sourcing**: Store all events as audit trail
- **Real-time Dashboards**: Stream events to WebSocket clients
- **Cross-tenant Aggregations**: Fan-out events for analytics
