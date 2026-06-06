# CloudWatch Dashboards and Alarms for SaaS Platform POC

# SNS Topic for Alerts
resource "aws_sns_topic" "saas_alerts" {
  name              = "saas-platform-alerts-${var.environment}"
  display_name      = "SaaS Platform Alerts"
  kms_master_key_id = "alias/aws/sns"

  tags = {
    Environment = var.environment
    Service     = "monitoring"
  }
}

resource "aws_sns_topic_subscription" "saas_alerts_email" {
  topic_arn = aws_sns_topic.saas_alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email != null ? var.alert_email : "ops-team@example.com"
}

# ============================================
# Analytics Service Dashboard
# ============================================

resource "aws_cloudwatch_dashboard" "analytics_service" {
  dashboard_name = "saas-analytics-service-${var.environment}"

  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime", { stat = "Average" }],
            [".", "RequestCount", { stat = "Sum" }],
            [".", "HTTPCode_Target_2XX_Count", { stat = "Sum" }],
            [".", "HTTPCode_Target_5XX_Count", { stat = "Sum" }],
          ]
          period = 300
          stat   = "Average"
          region = var.aws_region
          title  = "Analytics Service - Response Time & Request Rate"
          yAxis = {
            left = {
              min = 0
            }
          }
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ServiceName", "analytics-service"],
            [".", "MemoryUtilization", "ServiceName", "analytics-service"],
          ]
          period = 60
          stat   = "Average"
          region = var.aws_region
          title  = "Analytics Service - CPU & Memory"
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/DynamoDB", "ConsumedReadCapacityUnits", "TableName", "saas-platform-tenants"],
            [".", "ConsumedWriteCapacityUnits", "TableName", "saas-platform-tenants"],
          ]
          period = 300
          stat   = "Sum"
          region = var.aws_region
          title  = "DynamoDB - Capacity Usage"
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/S3", "NumberOfObjects", "BucketName", "saas-poc-reports"],
            [".", "BucketSizeBytes", "BucketName", "saas-poc-reports"],
          ]
          period = 86400
          stat   = "Average"
          region = var.aws_region
          title  = "S3 Reports Bucket - Size & Objects"
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/Events", "Invocations", "RuleName", "ingestion-processor"],
            [".", "FailedInvocations", "RuleName", "ingestion-processor"],
          ]
          period = 300
          stat   = "Sum"
          region = var.aws_region
          title  = "EventBridge - Invocations"
        }
      },
    ]
  })
}

# ============================================
# Ingestion Service Dashboard
# ============================================

resource "aws_cloudwatch_dashboard" "ingestion_service" {
  dashboard_name = "saas-ingestion-service-${var.environment}"

  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime", { stat = "Average" }],
            [".", "RequestCount", { stat = "Sum" }],
            [".", "HTTPCode_Target_2XX_Count", { stat = "Sum" }],
            [".", "HTTPCode_Target_5XX_Count", { stat = "Sum" }],
          ]
          period = 300
          stat   = "Average"
          region = var.aws_region
          title  = "Ingestion Service - Response Time & Request Rate"
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ServiceName", "ingestion-service"],
            [".", "MemoryUtilization", "ServiceName", "ingestion-service"],
          ]
          period = 60
          stat   = "Average"
          region = var.aws_region
          title  = "Ingestion Service - CPU & Memory"
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/S3", "NumberOfObjects", "BucketName", "saas-poc-ingestion"],
            [".", "BucketSizeBytes", "BucketName", "saas-poc-ingestion"],
          ]
          period = 86400
          stat   = "Average"
          region = var.aws_region
          title  = "S3 Ingestion Bucket - Size & Objects"
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/Events", "DeadLetterInvocations", "RuleName", "file-uploader"],
          ]
          period = 300
          stat   = "Sum"
          region = var.aws_region
          title  = "EventBridge DLQ - Failed Invocations"
        }
      },
    ]
  })
}

# ============================================
# Infrastructure Dashboard
# ============================================

resource "aws_cloudwatch_dashboard" "infrastructure" {
  dashboard_name = "saas-infrastructure-${var.environment}"

  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/EKS", "node_cpu_utilization", "ClusterName", aws_eks_cluster.main.name],
            [".", "node_memory_utilization", "ClusterName", aws_eks_cluster.main.name],
          ]
          period = 60
          stat   = "Average"
          region = var.aws_region
          title  = "EKS - Node Utilization"
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/EC2", "CPUUtilization", "AutoScalingGroupName", aws_autoscaling_group.main.name],
            [".", "NetworkIn", "AutoScalingGroupName", aws_autoscaling_group.main.name],
            [".", "NetworkOut", "AutoScalingGroupName", aws_autoscaling_group.main.name],
          ]
          period = 300
          stat   = "Average"
          region = var.aws_region
          title  = "EC2 Instances - Network & CPU"
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/DynamoDB", "ConsumedReadCapacityUnits"],
            [".", "ConsumedWriteCapacityUnits"],
            [".", "UserErrors"],
          ]
          period = 300
          stat   = "Sum"
          region = var.aws_region
          title  = "DynamoDB - Overall Metrics"
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/Lambda", "Duration", "FunctionName", "error-handler"],
            [".", "Errors", "FunctionName", "error-handler"],
            [".", "Throttles", "FunctionName", "error-handler"],
          ]
          period = 300
          stat   = "Sum"
          region = var.aws_region
          title  = "Lambda - Error Handler Metrics"
        }
      },
    ]
  })
}

# ============================================
# CloudWatch Alarms - Analytics Service
# ============================================

# High Error Rate Alert
resource "aws_cloudwatch_metric_alarm" "analytics_high_error_rate" {
  alarm_name          = "saas-analytics-error-rate-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = "300"
  statistic           = "Sum"
  threshold           = "5"
  alarm_description   = "Alert when error rate exceeds 5% for analytics service"
  alarm_actions       = [aws_sns_topic.saas_alerts.arn]
  treat_missing_data  = "notBreaching"

  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
    TargetGroup  = aws_lb_target_group.analytics.arn_suffix
  }
}

# High Response Time Alert (p99 > 5 seconds)
resource "aws_cloudwatch_metric_alarm" "analytics_high_latency" {
  alarm_name          = "saas-analytics-latency-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "3"
  metric_name         = "TargetResponseTime"
  namespace           = "AWS/ApplicationELB"
  period              = "300"
  statistic           = "Average"
  threshold           = "5"
  alarm_description   = "Alert when response time exceeds 5 seconds"
  alarm_actions       = [aws_sns_topic.saas_alerts.arn]
  treat_missing_data  = "notBreaching"

  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
    TargetGroup  = aws_lb_target_group.analytics.arn_suffix
  }
}

# ============================================
# CloudWatch Alarms - Ingestion Service
# ============================================

# File Upload Failure Alert
resource "aws_cloudwatch_metric_alarm" "ingestion_upload_failures" {
  alarm_name          = "saas-ingestion-failures-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = "300"
  statistic           = "Sum"
  threshold           = "10"
  alarm_description   = "Alert when ingestion failures exceed threshold"
  alarm_actions       = [aws_sns_topic.saas_alerts.arn]
  treat_missing_data  = "notBreaching"

  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
    TargetGroup  = aws_lb_target_group.ingestion.arn_suffix
  }
}

# ============================================
# CloudWatch Alarms - DynamoDB
# ============================================

# DynamoDB Throttling Alert
resource "aws_cloudwatch_metric_alarm" "dynamodb_throttling" {
  alarm_name          = "saas-dynamodb-throttling-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "UserErrors"
  namespace           = "AWS/DynamoDB"
  period              = "60"
  statistic           = "Sum"
  threshold           = "1"
  alarm_description   = "Alert when DynamoDB experiences throttling"
  alarm_actions       = [aws_sns_topic.saas_alerts.arn]
  treat_missing_data  = "notBreaching"
}

# DynamoDB Write Capacity Alert
resource "aws_cloudwatch_metric_alarm" "dynamodb_write_throttle" {
  alarm_name          = "saas-dynamodb-write-throttle-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "ConsumedWriteCapacityUnits"
  namespace           = "AWS/DynamoDB"
  period              = "300"
  statistic           = "Sum"
  threshold           = "100"
  alarm_description   = "Alert when write capacity nears limit"
  alarm_actions       = [aws_sns_topic.saas_alerts.arn]
  treat_missing_data  = "notBreaching"
}

# ============================================
# CloudWatch Alarms - EKS
# ============================================

# EKS Node CPU Alert
resource "aws_cloudwatch_metric_alarm" "eks_node_cpu_high" {
  alarm_name          = "saas-eks-node-cpu-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "3"
  metric_name         = "node_cpu_utilization"
  namespace           = "AWS/EKS"
  period              = "300"
  statistic           = "Average"
  threshold           = "85"
  alarm_description   = "Alert when EKS node CPU exceeds 85%"
  alarm_actions       = [aws_sns_topic.saas_alerts.arn]
  treat_missing_data  = "notBreaching"

  dimensions = {
    ClusterName = aws_eks_cluster.main.name
  }
}

# EKS Node Memory Alert
resource "aws_cloudwatch_metric_alarm" "eks_node_memory_high" {
  alarm_name          = "saas-eks-node-memory-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "3"
  metric_name         = "node_memory_utilization"
  namespace           = "AWS/EKS"
  period              = "300"
  statistic           = "Average"
  threshold           = "85"
  alarm_description   = "Alert when EKS node memory exceeds 85%"
  alarm_actions       = [aws_sns_topic.saas_alerts.arn]
  treat_missing_data  = "notBreaching"

  dimensions = {
    ClusterName = aws_eks_cluster.main.name
  }
}

# ============================================
# CloudWatch Alarms - EventBridge
# ============================================

# EventBridge DLQ Alert
resource "aws_cloudwatch_metric_alarm" "eventbridge_dlq" {
  alarm_name          = "saas-eventbridge-dlq-${var.environment}"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "1"
  metric_name         = "DeadLetterInvocations"
  namespace           = "AWS/Events"
  period              = "300"
  statistic           = "Sum"
  threshold           = "1"
  alarm_description   = "Alert when events are sent to DLQ"
  alarm_actions       = [aws_sns_topic.saas_alerts.arn]
  treat_missing_data  = "notBreaching"
}

# ============================================
# Outputs
# ============================================

output "sns_topic_arn" {
  description = "SNS topic ARN for alerts"
  value       = aws_sns_topic.saas_alerts.arn
}

output "analytics_dashboard_url" {
  description = "URL to analytics service dashboard"
  value       = "https://console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=${aws_cloudwatch_dashboard.analytics_service.dashboard_name}"
}

output "ingestion_dashboard_url" {
  description = "URL to ingestion service dashboard"
  value       = "https://console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=${aws_cloudwatch_dashboard.ingestion_service.dashboard_name}"
}

output "infrastructure_dashboard_url" {
  description = "URL to infrastructure dashboard"
  value       = "https://console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=${aws_cloudwatch_dashboard.infrastructure.dashboard_name}"
}
