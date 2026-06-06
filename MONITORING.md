# Monitoring & Observability Guide - Datadog

Comprehensive monitoring setup using Datadog APM, metrics, logs, and custom dashboards.

## Quick Start

### Local Development (No Datadog)

Metrics are collected locally but not sent to Datadog:

```bash
docker-compose up -d
# Metrics available at: http://localhost:8080/actuator/metrics
# http://localhost:8081/actuator/metrics
```

### Datadog Setup

1. **Create Datadog Account**: https://www.datadoghq.com/
2. **Get API Key**: Datadog UI → Settings → API Keys
3. **Get App Key**: Datadog UI → Settings → Application Keys

### Deploy to AWS with Datadog

Set environment variables:

```bash
export DD_API_KEY=<your-api-key>
export DD_APP_KEY=<your-app-key>
export DD_ENV=production
export DD_SERVICE_NAME=analytics-service  # or ingestion-service
export DD_METRICS_ENABLED=true
```

Then deploy:

```bash
# Using Kubernetes
kubectl set env deployment/analytics-service \
  DD_API_KEY=$DD_API_KEY \
  DD_ENV=production \
  DD_METRICS_ENABLED=true

# Or with Terraform
terraform apply -var="dd_api_key=$DD_API_KEY"
```

---

## Metrics Collected

### Counter Metrics

Track counts of specific events:

| Metric | Purpose | Tags |
|--------|---------|------|
| `analytics.queries.total` | Total analytics queries | `tenant_id`, `service` |
| `ingestion.uploads.total` | Total file uploads | `tenant_id`, `service` |
| `events.published.total` | Total EventBridge events | `event_type`, `tenant_id`, `service` |
| `errors.total` | Total errors | `error_type`, `tenant_id`, `service` |

### Timer Metrics (Percentiles)

Track latency with p50, p95, p99 percentiles:

| Metric | Purpose | Percentiles |
|--------|---------|-------------|
| `analytics.insight.generation.duration` | AI insight generation time | p50, p95, p99 |
| `ingestion.file.upload.duration` | S3 file upload time | p50, p95, p99 |
| `aws.s3.operation.duration` | All S3 operations | p50, p95, p99 |
| `eventbridge.publish.duration` | EventBridge publish latency | p50, p95, p99 |

### Gauge Metrics

Track current state:

| Metric | Purpose |
|--------|---------|
| `active.connections` | Current active connections |
| `queue.depth` | Queue backlog depth |

---

## Example Datadog Dashboard

### Analytics Service Dashboard

```
┌─────────────────────────────────────────────────────────────┐
│ Analytics Service - Production                              │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Queries/min       Avg Latency      Error Rate      Uptime   │
│  ▄▄▄▄▄▄           ▂▂▂▂▂▂▂           ▁▁▁▁▁▁▁        ██████  │
│  150 qps           245ms p95         0.2%           99.95%   │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ Insight Generation Latency (p99)                    │    │
│  │ ▁▂▂▃▃▄▄▄▄▅▅▅▅▆▆▆▇▇▇█████████████████▅▅▄▄▃▃▂▁   │    │
│  │ 0────────────────1min─────────────────────5min     │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ Error Rate by Type                                  │    │
│  │ query_error (0.15%)  |  stream_error (0.05%)       │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ Top Tenants by Query Volume                        │    │
│  │ tenant-a: 450 qps  | tenant-b: 320 qps             │    │
│  │ tenant-c: 280 qps  | tenant-d: 150 qps             │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

---

## Monitored Endpoints

### Analytics Service (:8080)

```
POST /api/v1/analytics/query
  - Tracks: insight generation latency, report saves, event publishes
  - Tags: tenant_id, service=analytics-service
  - Errors: query_error

POST /api/v1/analytics/query/stream
  - Tracks: streaming latency, completion time
  - Tags: tenant_id, service=analytics-service
  - Errors: stream_error

GET /api/v1/analytics/reports/{id}
  - Tracks: S3 presigned URL generation
  - Tags: tenant_id, service=analytics-service

GET /api/v1/analytics/health
  - Health check endpoint (no metrics)
```

### Ingestion Service (:8081)

```
POST /api/v1/ingestion/upload
  - Tracks: file upload latency to S3, event publishes
  - Tags: tenant_id, service=ingestion-service
  - Errors: upload_error

GET /api/v1/ingestion/status/{jobId}
  - Tracks: status query latency
  - Tags: tenant_id, service=ingestion-service

GET /api/v1/ingestion/health
  - Health check endpoint (no metrics)
```

---

## Alerts & Thresholds

### Recommended Alert Rules

```yaml
Analytics Service Alerts:
  - Error Rate > 5% for 5 minutes
    → Severity: Warning
    → Action: Notify ops-team

  - P99 Latency > 5 seconds for 10 minutes
    → Severity: Warning
    → Action: Review slow queries

  - P95 Latency > 3 seconds for 15 minutes
    → Severity: Info
    → Action: Check AI API rate limiting

Ingestion Service Alerts:
  - Upload Failure Rate > 10% for 5 minutes
    → Severity: Critical
    → Action: Notify ops-team, check S3

  - File Upload Latency > 2 seconds for 10 minutes
    → Severity: Warning
    → Action: Review file sizes, S3 performance

EventBridge Alerts:
  - Publish Latency > 1 second for 5 minutes
    → Severity: Warning
    → Action: Check EventBridge DLQ

Multi-Tenant Isolation Alerts:
  - Tenant data query time differs by >50% for same query
    → Severity: Warning
    → Action: Review tenant-specific bottlenecks
```

### Create Alert in Datadog

1. Go to Monitors → New Monitor
2. Select Metric
3. Define condition: `avg(analytics.insight.generation.duration) > 5000`
4. Set alert threshold: 10 minutes
5. Add notification: `@slack-alerts @opsteam`

---

## Logs Integration

### Log Pattern

All logs include tenant context via MDC:

```
2026-06-06T10:15:30.123 [pool-1-thread-5] INFO com.saasplatform.analytics.controller.AnalyticsController [tenant-a] - Analytics query received for tenant: tenant-a, title: Q2 Revenue Analysis
```

### Datadog Log Agent Setup

```yaml
# datadog-agent values.yaml
datadog:
  apiKey: <DD_API_KEY>
  site: datadoghq.com
  
logs:
  enabled: true
  containerCollectAll: true

apm:
  enabled: true
  
```

### Log Queries

```
# All errors by tenant
service:analytics-service severity:error @tenant_id:tenant-a

# Slow queries (>3s)
service:analytics-service duration:>3000

# EventBridge failures
service:* "EventBridge" "error"

# Multi-tenant access violations
@tenant_id:* "isolation" "violation"
```

---

## APM & Tracing

### Distributed Traces

When enabled with Datadog Java Agent:

```
POST /api/v1/analytics/query
  ├─ TenantAwareFilter (JWT extraction)
  ├─ AnalyticsController (query validation)
  ├─ InsightGenerationService (ChatClient call)
  ├─ ReportService (S3 putObject)
  ├─ EventPublisher (EventBridge putEvents)
  └─ Response serialization
  
Total trace time: 245ms
```

### Enable Java Agent

Add to JVM startup:

```bash
java -javaagent:/opt/dd-java-agent.jar \
  -Ddd.service=analytics-service \
  -Ddd.env=production \
  -Ddd.version=1.0.0 \
  -Ddd.trace.sample.rate=0.1 \
  -jar app.jar
```

---

## Custom Metrics

### Adding New Metrics

```java
// In your service
@RequiredArgsConstructor
public class MyService {
    private final MetricsService metricsService;

    public void doWork() {
        long start = System.currentTimeMillis();
        
        // Do work...
        
        metricsService.recordS3Operation(
            System.currentTimeMillis() - start, 
            "putObject"
        );
        metricsService.incrementFileUploads("tenant-a");
    }
}
```

### Available Metrics Methods

```java
// Counters
metricsService.incrementAnalyticsQueries(tenantId);
metricsService.incrementFileUploads(tenantId);
metricsService.incrementEventPublished(eventType, tenantId);
metricsService.incrementErrors(errorType, tenantId);

// Timers
metricsService.recordInsightGeneration(durationMs);
metricsService.recordFileUpload(durationMs);
metricsService.recordS3Operation(durationMs, operation);
metricsService.recordEventPublish(durationMs);

// Gauges
metricsService.setActiveConnections(count);
metricsService.setQueueDepth(queueName, depth);
```

---

## Local Development

### View Metrics Locally

```bash
# Analytics Service metrics
curl http://localhost:8080/actuator/metrics

# Filter by metric name
curl http://localhost:8080/actuator/metrics/analytics.queries.total

# Get metric details
curl http://localhost:8080/actuator/metrics/analytics.insight.generation.duration
```

### Response Format

```json
{
  "name": "analytics.queries.total",
  "description": "Total number of analytics queries",
  "baseUnit": "1",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 42
    }
  ],
  "availableTags": [
    {
      "tag": "tenant_id",
      "values": ["tenant-a", "tenant-b", "tenant-c"]
    }
  ]
}
```

---

## Production Checklist

Before deploying to production with Datadog:

- [ ] Datadog API key configured
- [ ] DD_ENV set to `production`
- [ ] DD_SERVICE_NAME configured per service
- [ ] APM sampling rate tuned (0.1 = 10%)
- [ ] Alert rules created and tested
- [ ] Dashboard imported and customized
- [ ] Team Slack/PagerDuty notifications configured
- [ ] Log retention policy set (default 30 days)
- [ ] Cost estimation reviewed (check Datadog billing)

---

## Troubleshooting

### Metrics Not Appearing in Datadog

1. Verify API key: `curl -H "DD-API-KEY: $DD_API_KEY" https://api.datadoghq.com/api/v1/validate`
2. Check network: `curl -v https://api.datadoghq.com/api/v1/series`
3. Verify configuration: `kubectl get configmap app-config` | grep DD_
4. Check logs: `kubectl logs -f deployment/analytics-service | grep Datadog`

### High APM Sampling

If too many traces are being sent:

```bash
# Reduce sample rate to 1%
DD_TRACE_SAMPLE_RATE=0.01
```

### Memory Usage Spike

The Java agent adds ~50MB per instance. If constrained:

```bash
# Disable unused features
DD_TRACE_ENABLED=true
DD_PROFILING_ENABLED=false
DD_LOGS_INJECTION=true
```

---

## Related Documentation

- ARCHITECTURE.md — System design and layers
- LOCAL_DEV.md — Local development setup
- AWS_DEPLOYMENT.md — Production deployment
- TESTING.md — Test strategy with metrics

**Last Updated**: 2026-06-06
