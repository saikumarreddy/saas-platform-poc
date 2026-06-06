# CloudWatch Dashboards & Alerts Guide

Comprehensive operational visibility with CloudWatch dashboards and automated alerts.

## Quick Start

### Deploy with Alerts

```bash
cd infrastructure/terraform

terraform apply \
  -var="alert_email=ops-team@company.com"
```

The SNS topic will send a confirmation email. Click the link to confirm subscription.

### View Dashboards

After deployment, dashboards are available at:

```
Analytics Service:
https://console.aws.amazon.com/cloudwatch/home?region=us-east-1#dashboards:name=saas-analytics-service-poc

Ingestion Service:
https://console.aws.amazon.com/cloudwatch/home?region=us-east-1#dashboards:name=saas-ingestion-service-poc

Infrastructure:
https://console.aws.amazon.com/cloudwatch/home?region=us-east-1#dashboards:name=saas-infrastructure-poc
```

---

## Dashboards Overview

### 1. Analytics Service Dashboard

Monitors AI-powered analytics platform health and performance.

**Widgets:**
- **Response Time & Request Rate**: ALB metrics showing average response time and total requests
- **CPU & Memory Utilization**: ECS service resource usage
- **DynamoDB Capacity**: Read/write capacity consumption for tenant state
- **S3 Bucket Stats**: Reports bucket size and object count
- **EventBridge Invocations**: Successful and failed event processing

**Key Metrics:**
- Response time: 100-300ms (p50), 500ms (p95), 1s (p99)
- Request rate: 100-200 requests/min during peak
- Error rate: < 0.2%
- S3 reports: growing with usage

### 2. Ingestion Service Dashboard

Monitors file upload pipeline and data ingestion health.

**Widgets:**
- **Response Time & Request Rate**: Upload API performance
- **CPU & Memory Utilization**: Service resource usage
- **S3 Ingestion Bucket**: Upload bucket size and object count
- **EventBridge DLQ**: Failed event handling

**Key Metrics:**
- Response time: 500ms-2s (p95), 5s (p99)
- Upload success rate: > 99%
- S3 bucket growth: tracking with quota
- DLQ messages: should be 0 (failures indicate issues)

### 3. Infrastructure Dashboard

Overall system health across all components.

**Widgets:**
- **EKS Node Utilization**: CPU and memory per node
- **EC2 Instance Metrics**: Network I/O and CPU
- **DynamoDB Overall**: Read/write capacity, user errors
- **Lambda Error Handler**: Function duration, errors, throttles

**Key Metrics:**
- Node CPU: 30-60% normal, alert > 85%
- Node memory: 40-70% normal, alert > 85%
- DynamoDB errors: < 1% of requests
- Lambda success: > 99.9%

---

## CloudWatch Alarms

### Alert Hierarchy

```
Critical (Immediate Action)
├─ DynamoDB throttling
└─ EventBridge DLQ messages

Warning (Investigate within 15 min)
├─ Analytics error rate > 5%
├─ Ingestion failures > 10
├─ Response time > 5s
└─ EKS node resources > 85%

Info (Monitor trend)
├─ Capacity trending
└─ Cost overruns
```

### Configured Alarms

| Alarm | Metric | Threshold | Duration | Action |
|-------|--------|-----------|----------|--------|
| **Error Rate High** | HTTPCode_5XX | > 5 events/5min | 10 min | SNS → Email |
| **Latency High** | Response Time | > 5 sec | 15 min | SNS → Email |
| **DynamoDB Throttle** | UserErrors | > 1 | 1 min | SNS → Email |
| **DynamoDB Capacity** | WriteCapacity | > 100 CUs/5min | 10 min | SNS → Email |
| **EventBridge DLQ** | DeadLetterInvocations | ≥ 1 | 5 min | SNS → Email |
| **EKS CPU High** | node_cpu_utilization | > 85% | 15 min | SNS → Email |
| **EKS Memory High** | node_memory_utilization | > 85% | 15 min | SNS → Email |

---

## Alert Response Playbooks

### Error Rate High

**Alert**: Analytics error rate > 5%

**Detection**: 
- ALB returning 5xx responses
- Threshold: > 5 errors per 5 minutes
- Duration: 2 periods (10 minutes)

**Response**:
1. Check analytics service logs: `kubectl logs -f deployment/analytics-service`
2. Check if upstream service is down (Datadog, S3, EventBridge)
3. Check recent deployments: `git log --oneline -5`
4. If not obvious, check metrics:
   - S3 latency (AWS console)
   - DynamoDB errors (AWS console)
   - EventBridge DLQ (AWS console)
5. If service hung, restart: `kubectl rollout restart deployment/analytics-service`

### DynamoDB Throttling

**Alert**: UserErrors > 1

**Detection**:
- DynamoDB returning `ProvisionedThroughputExceededException`
- Threshold: any user error (indicates throttling)
- Duration: 1 minute

**Response**:
1. **Immediate**: Increase DynamoDB capacity via AWS console
   - Go to Tables → saas-platform-tenants
   - Update Settings → Billing mode
   - Switch to `PAY_PER_REQUEST` for on-demand capacity
2. **Monitor**: Watch error rate drop
3. **Investigation**: Check what caused spike
   - Single tenant running heavy queries?
   - Batch import in progress?
4. **Prevention**: 
   - Adjust DynamoDB billing mode in Terraform
   - Set up reserved capacity if traffic is predictable

### EventBridge DLQ

**Alert**: DeadLetterInvocations ≥ 1

**Detection**:
- An event failed to route to target
- Threshold: any DLQ message
- Duration: 5 minutes

**Response**:
1. **Check**: What events are in DLQ?
   ```bash
   aws sqs receive-message \
     --queue-url https://sqs.<region>.amazonaws.com/<account>/saas-platform-dlq
   ```
2. **Investigate**: 
   - Is Lambda function running? `aws lambda get-function --function-name error-handler`
   - Are targets healthy? Check ALB target health
3. **Retry**: Re-drive messages from DLQ
   ```bash
   aws lambda invoke \
     --function-name error-handler \
     --payload '{"action":"retry"}' /tmp/output
   ```
4. **Fix**: Deploy fix to target Lambda/service, then retry

---

## Using Dashboards

### Real-time Monitoring

1. Open Analytics Service dashboard
2. Set time range to "1 hour"
3. Set refresh to "5 minutes"
4. Watch for sudden spikes in:
   - Error rate
   - Response time
   - CPU/memory

### Capacity Planning

1. Open Infrastructure dashboard
2. Set time range to "1 week"
3. Look for trends in:
   - Node CPU (trending up?)
   - Node memory (trending up?)
   - DynamoDB capacity usage
4. If trending > 70%, consider scaling

### Post-Incident Review

1. Open Analytics Service dashboard
2. Set time range to incident window
3. Document:
   - When error rate spiked
   - What was response time
   - Were there any correlation with other metrics
4. Check logs for root cause

---

## Custom Alarms

### Adding New Alarm

```hcl
# In infrastructure/terraform/cloudwatch.tf

resource "aws_cloudwatch_metric_alarm" "my_alarm" {
  alarm_name          = "my-alarm-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "MyMetric"
  namespace           = "AWS/MyService"
  period              = "300"
  statistic           = "Average"
  threshold           = "100"
  alarm_description   = "Alert when my metric exceeds threshold"
  alarm_actions       = [aws_sns_topic.saas_alerts.arn]
}
```

### Common Metrics

```
AWS/ApplicationELB:
  - TargetResponseTime (milliseconds)
  - RequestCount (requests)
  - HTTPCode_Target_2XX_Count
  - HTTPCode_Target_5XX_Count
  - HealthyHostCount
  - UnHealthyHostCount

AWS/DynamoDB:
  - ConsumedReadCapacityUnits
  - ConsumedWriteCapacityUnits
  - UserErrors
  - SystemErrors

AWS/EKS:
  - node_cpu_utilization (percent)
  - node_memory_utilization (percent)
  - pod_cpu_utilization
  - pod_memory_utilization

AWS/S3:
  - BucketSizeBytes
  - NumberOfObjects
```

---

## Alert Notification

### SNS Subscriptions

Alerts are sent via SNS topic: `saas-platform-alerts-poc`

**Current subscriptions:**
- Email: ops-team@company.com

### Adding Slack Notifications

```bash
# Create Slack webhook in Slack Workspace
# Settings → Apps & Integrations → Incoming Webhooks

# Create Lambda function to forward SNS → Slack
aws lambda create-function \
  --function-name sns-to-slack \
  --runtime python3.11 \
  --handler index.handler \
  --zip-file fileb://slack_forwarder.zip
```

Lambda code:
```python
import json
import boto3
import urllib3

http = urllib3.PoolManager()

def handler(event, context):
    message = json.loads(event['Records'][0]['Sns']['Message'])
    
    slack_message = {
        'text': f"🚨 {message['AlarmName']}: {message['StateChangeReason']}"
    }
    
    http.request('POST', SLACK_WEBHOOK_URL,
        body=json.dumps(slack_message),
        headers={'Content-Type': 'application/json'})
    
    return {'statusCode': 200}
```

---

## Troubleshooting

### Dashboard Not Showing Data

1. Wait 2-3 minutes for metrics to appear
2. Verify service is running: `kubectl get pods -n saas-platform-poc`
3. Check CloudWatch agent: `kubectl logs daemonset/cloudwatch-agent`
4. Verify IAM role has permissions: `AmazonCloudWatchAgentServerPolicy`

### Alarms Stuck in INSUFFICIENT_DATA

1. Metric may not have enough data points
2. Check metric exists: AWS Console → CloudWatch → Metrics
3. If metric missing, check source:
   - Is ALB configured correctly?
   - Are target groups healthy?
4. Manually trigger alarm: `aws cloudwatch set-alarm-state --alarm-name my-alarm --state-value ALARM`

### Too Many Alerts

1. Increase thresholds in `cloudwatch.tf`
2. Increase evaluation periods (wait longer before alerting)
3. Add missing data behavior: `treat_missing_data = "notBreaching"`

---

## Cost Estimation

### CloudWatch Costs

```
Custom Metrics:       $0.30 per metric per month
Dashboard:            $3.00 per dashboard per month
Alarms (SNS-enabled): $0.10 per alarm per month
API Calls:            $0.01 per 1,000 requests

For this setup (~50 metrics, 3 dashboards, 8 alarms):
Monthly cost ≈ $20-50
```

### Optimize Costs

1. Use DetailedMonitoring sparingly
2. Aggregate metrics at higher intervals (5min instead of 1min)
3. Archive old dashboards
4. Use metric math to combine metrics

---

## Related Documentation

- MONITORING.md — Datadog integration and metrics
- AWS_DEPLOYMENT.md — Infrastructure deployment
- ARCHITECTURE.md — System design
- LOCAL_DEV.md — Development setup

**Last Updated**: 2026-06-06
