# AWS Deployment Guide

Deploy the SaaS Platform POC to a real AWS environment with Cognito, EKS, DynamoDB, S3, and EventBridge.

## Prerequisites

- **AWS Account** with sufficient permissions (EC2, EKS, IAM, DynamoDB, S3, EventBridge, Cognito)
- **AWS CLI** (v2.x)
- **Terraform** (v1.0+)
- **kubectl** (v1.29+)
- **Docker** (for building and pushing images to ECR)
- **GitHub Personal Access Token** (for CI/CD)

## Step 1: Prepare AWS Credentials

### Configure AWS CLI

```bash
aws configure

# Enter:
# AWS Access Key ID: [your-access-key]
# AWS Secret Access Key: [your-secret-key]
# Default region: us-east-1
# Default output format: json
```

Or set environment variables:

```bash
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_DEFAULT_REGION=us-east-1
```

### Verify Access

```bash
aws sts get-caller-identity
# Should return your account ID and ARN
```

## Step 2: Create S3 Bucket for Terraform State (Optional but Recommended)

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
BUCKET_NAME="saas-platform-terraform-state-${ACCOUNT_ID}"

aws s3 mb s3://${BUCKET_NAME} --region us-east-1
aws s3api put-bucket-versioning \
  --bucket ${BUCKET_NAME} \
  --versioning-configuration Status=Enabled
aws s3api put-bucket-encryption \
  --bucket ${BUCKET_NAME} \
  --server-side-encryption-configuration '{
    "Rules": [{
      "ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}
    }]
  }'

# Create DynamoDB table for state locking
aws dynamodb create-table \
  --table-name terraform-locks \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 \
  --region us-east-1
```

## Step 3: Configure Terraform

### Edit `infrastructure/terraform/main.tf`

Uncomment the backend configuration:

```hcl
terraform {
  # ... existing config ...
  backend "s3" {
    bucket         = "saas-platform-terraform-state-123456789"
    key            = "prod/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "terraform-locks"
  }
}
```

Replace `123456789` with your AWS account ID.

### Create `terraform.tfvars` (Optional)

```hcl
aws_region           = "us-east-1"
environment          = "prod"
vpc_cidr             = "10.0.0.0/16"
kubernetes_version   = "1.29"
node_desired_size    = 2
node_min_size        = 1
node_max_size        = 4
dynamodb_billing_mode = "PAY_PER_REQUEST"
```

## Step 4: Deploy Infrastructure with Terraform

```bash
cd infrastructure/terraform

# Initialize Terraform
terraform init

# Plan (review what will be created)
terraform plan -out=tfplan

# Apply (create AWS resources)
terraform apply tfplan

# This takes ~15-20 minutes. Terraform creates:
# - VPC with 2 public + 2 private subnets
# - EKS cluster with 2 t3.medium nodes
# - DynamoDB tables (tenants, ingestion_jobs)
# - S3 buckets (ingestion, reports)
# - EventBridge event bus
# - Cognito user pool
# - ALB with security groups

# Save outputs
terraform output > ../outputs.json
```

### Terraform Outputs

After `terraform apply`, you'll get:

```bash
Outputs:

eks_cluster_endpoint = "https://abc123.eks.us-east-1.amazonaws.com"
eks_cluster_name = "saas-platform-poc"
alb_dns_name = "saas-platform-alb-123456.us-east-1.elb.amazonaws.com"
cognito_user_pool_id = "us-east-1_abc123def"
s3_ingestion_bucket = "saas-platform-poc-ingestion-123456789"
s3_reports_bucket = "saas-platform-poc-reports-123456789"
```

## Step 5: Configure kubectl

```bash
# Update kubeconfig to connect to the EKS cluster
aws eks update-kubeconfig \
  --region us-east-1 \
  --name saas-platform-poc

# Verify connection
kubectl cluster-info
kubectl get nodes
```

## Step 6: Create Kubernetes Namespace and Secrets

```bash
# Create namespace
kubectl apply -f infrastructure/k8s/namespace.yaml

# Create secrets for API keys
kubectl create secret generic ai-credentials \
  --from-literal=anthropic-api-key="sk-ant-your-key" \
  --from-literal=openai-api-key="sk-your-key" \
  -n saas-platform-poc

# Verify secrets
kubectl get secrets -n saas-platform-poc
```

## Step 7: Create ECR Repositories

```bash
# Create repositories for both services
aws ecr create-repository \
  --repository-name saas-platform-poc/analytics-service \
  --region us-east-1

aws ecr create-repository \
  --repository-name saas-platform-poc/ingestion-service \
  --region us-east-1

# Get login token and login to ECR
aws ecr get-login-password --region us-east-1 | docker login \
  --username AWS \
  --password-stdin $(aws sts get-caller-identity --query Account --output text).dkr.ecr.us-east-1.amazonaws.com
```

## Step 8: Build and Push Docker Images

```bash
cd /path/to/saas-platform-poc

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGISTRY="${ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com"

# Build analytics-service
docker build -t ${REGISTRY}/saas-platform-poc/analytics-service:latest \
  --build-arg SERVICE_NAME=analytics-service .

docker push ${REGISTRY}/saas-platform-poc/analytics-service:latest

# Build ingestion-service
docker build -t ${REGISTRY}/saas-platform-poc/ingestion-service:latest \
  --build-arg SERVICE_NAME=ingestion-service .

docker push ${REGISTRY}/saas-platform-poc/ingestion-service:latest

# List images in ECR
aws ecr describe-images --repository-name saas-platform-poc/analytics-service
```

## Step 9: Deploy Kubernetes Manifests

Update image URIs in manifests:

```bash
# Update analytics-deployment.yaml
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
sed -i "s|image: analytics-service:latest|image: ${ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com/saas-platform-poc/analytics-service:latest|g" \
  infrastructure/k8s/analytics-deployment.yaml

# Update ingestion-deployment.yaml
sed -i "s|image: ingestion-service:latest|image: ${ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com/saas-platform-poc/ingestion-service:latest|g" \
  infrastructure/k8s/ingestion-deployment.yaml

# Apply manifests
kubectl apply -f infrastructure/k8s/
```

## Step 10: Verify Deployment

```bash
# Check if pods are running
kubectl get pods -n saas-platform-poc

# Wait for pods to be ready (2-3 minutes)
kubectl wait --for=condition=Ready pod \
  -l app=analytics-service \
  -n saas-platform-poc \
  --timeout=300s

# View logs
kubectl logs -f deployment/analytics-service -n saas-platform-poc
kubectl logs -f deployment/ingestion-service -n saas-platform-poc

# Check service endpoints
kubectl get svc -n saas-platform-poc

# Get ALB endpoint
kubectl get svc -n saas-platform-poc -o jsonpath='{.items[*].status.loadBalancer.ingress[*].hostname}'
```

## Step 11: Set Up DNS (Optional)

If you have a custom domain:

```bash
# Get ALB DNS name
ALB_DNS=$(terraform output -raw alb_dns_name)

# Create CNAME record in Route 53 pointing to ALB
# api.yourdomain.com → saas-platform-alb-xxx.us-east-1.elb.amazonaws.com
```

## Step 12: Configure CI/CD (GitHub Actions)

### Create IAM Role for GitHub Actions

```bash
cat > trust-policy.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": "repo:your-org/saas-platform-poc:ref:refs/heads/main"
        }
      }
    }
  ]
}
EOF

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
sed -i "s/ACCOUNT_ID/$ACCOUNT_ID/g" trust-policy.json

# Create role
aws iam create-role \
  --role-name github-actions-saas-platform \
  --assume-role-policy-document file://trust-policy.json

# Attach policy for ECR and EKS access
aws iam attach-role-policy \
  --role-name github-actions-saas-platform \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryPowerUser

aws iam attach-role-policy \
  --role-name github-actions-saas-platform \
  --policy-arn arn:aws:iam::aws:policy/AmazonEKSFullAccess
```

### Add Secret to GitHub Repository

In GitHub repository settings → Secrets:

```
AWS_ROLE_ARN=arn:aws:iam::ACCOUNT_ID:role/github-actions-saas-platform
```

## Step 13: Test Deployment

### Create Cognito Test User

```bash
USER_POOL_ID=$(terraform output -raw cognito_user_pool_id)

aws cognito-idp admin-create-user \
  --user-pool-id $USER_POOL_ID \
  --username testuser@example.com \
  --message-action SUPPRESS \
  --temporary-password TempPassword123! \
  --region us-east-1

# Set permanent password
aws cognito-idp admin-set-user-password \
  --user-pool-id $USER_POOL_ID \
  --username testuser@example.com \
  --password Password123! \
  --permanent \
  --region us-east-1
```

### Get JWT Token

```bash
USER_POOL_ID=$(terraform output -raw cognito_user_pool_id)
CLIENT_ID=$(aws cognito-idp list-user-pool-clients \
  --user-pool-id $USER_POOL_ID \
  --region us-east-1 \
  --query 'UserPoolClients[0].ClientId' \
  --output text)

# Authenticate and get token
TOKEN=$(aws cognito-idp admin-initiate-auth \
  --user-pool-id $USER_POOL_ID \
  --client-id $CLIENT_ID \
  --auth-flow ADMIN_NO_SRP_AUTH \
  --auth-parameters USERNAME=testuser@example.com,PASSWORD=Password123! \
  --region us-east-1 \
  --query 'AuthenticationResult.IdToken' \
  --output text)

echo "Token: $TOKEN"
```

### Test Analytics Endpoint

```bash
ALB_DNS=$(kubectl get svc -n saas-platform-poc -o jsonpath='{.items[0].status.loadBalancer.ingress[0].hostname}')

curl -X POST http://${ALB_DNS}/api/v1/analytics/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Production Test Query",
    "metrics": "revenue: $250K, growth: +20%",
    "dateRange": "2024-01-01 to 2024-01-31"
  }'
```

## Step 14: Monitor and Logs

### CloudWatch Logs

```bash
# View EKS cluster logs
aws logs describe-log-groups --region us-east-1 | grep saas-platform

# Stream logs
aws logs tail /aws/eks/saas-platform-poc/cluster --follow
```

### CloudWatch Metrics

1. Go to AWS Console → CloudWatch → Dashboards
2. Create dashboard with:
   - EKS node CPU/memory
   - EKS pod count
   - DynamoDB read/write capacity
   - S3 request count
   - EventBridge message count

### Set Up Alarms

```bash
# Example: Alert if error rate > 5%
aws cloudwatch put-metric-alarm \
  --alarm-name saas-platform-error-rate \
  --alarm-description "Alert when error rate exceeds 5%" \
  --metric-name ErrorRate \
  --namespace SaasPlatform \
  --statistic Average \
  --period 300 \
  --threshold 5 \
  --comparison-operator GreaterThanThreshold
```

## Troubleshooting

### Pods not starting

```bash
kubectl describe pod <pod-name> -n saas-platform-poc
kubectl logs <pod-name> -n saas-platform-poc
```

### ECR image pull errors

```bash
# Check ECR credentials secret
kubectl get secret -n saas-platform-poc

# Create imagePullSecret if needed
kubectl create secret docker-registry ecr-secret \
  --docker-server=ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com \
  --docker-username=AWS \
  --docker-password=$(aws ecr get-login-password) \
  -n saas-platform-poc
```

### DynamoDB throttling

Increase capacity:

```bash
aws dynamodb update-table \
  --table-name saas-platform-tenants \
  --provisioned-throughput ReadCapacityUnits=10,WriteCapacityUnits=10 \
  --region us-east-1
```

Or use on-demand billing:

```bash
aws dynamodb update-billing-mode \
  --table-name saas-platform-tenants \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1
```

## Cleanup

When you're done, delete everything:

```bash
# Delete Kubernetes resources
kubectl delete namespace saas-platform-poc

# Wait for ALB/NLB to be deleted
sleep 60

# Destroy Terraform resources
cd infrastructure/terraform
terraform destroy

# Delete S3 bucket for Terraform state
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
aws s3 rm s3://saas-platform-terraform-state-${ACCOUNT_ID} --recursive
aws s3 rb s3://saas-platform-terraform-state-${ACCOUNT_ID}

# Delete ECR repositories
aws ecr delete-repository \
  --repository-name saas-platform-poc/analytics-service \
  --force --region us-east-1
aws ecr delete-repository \
  --repository-name saas-platform-poc/ingestion-service \
  --force --region us-east-1
```

## Cost Estimation

Rough monthly costs (us-east-1):

- **EKS Cluster**: $73 (control plane) + ~$40/month per node (t3.medium)
- **DynamoDB**: ~$1/month (on-demand, low traffic)
- **S3**: ~$0.50/month (storage) + data transfer costs
- **EventBridge**: ~$0.35/million events
- **NAT Gateway**: ~$32/month
- **Data Transfer**: $0.02/GB out

**Total**: ~$150-200/month for a small POC cluster.
