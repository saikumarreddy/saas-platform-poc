# Security Guide - SaaS Platform POC

**Last Reviewed**: 2026-06-06  
**Status**: ✅ Secure (no exposed secrets, proper .gitignore)

## Quick Checklist

Before each commit/push:
- [ ] No hardcoded API keys or passwords in code
- [ ] All secrets use environment variables
- [ ] `.env` file is git-ignored
- [ ] Commit message doesn't reveal secrets
- [ ] No tokens in git remote URL

Before production deployment:
- [ ] Use AWS Secrets Manager (not env vars)
- [ ] Use Kubernetes Secrets (not env vars)
- [ ] Rotate all credentials
- [ ] Enable CloudWatch logs and alerts
- [ ] Run security audit (see SECURITY_CHECKLIST.md)

## Secret Files

**Never commit:**
```
.env                  # Your local environment variables
.env.local           # Personal overrides
*.key                # Private keys
credentials.json     # AWS/GCP service accounts
secrets/             # Secrets directory
```

**Always commit:**
```
.env.example         # Template showing structure (no values)
.gitignore          # Ensure secrets are ignored
SECURITY.md         # This file
```

## Environment Variables

### Local Development (.env)
Create `.env` file (not committed):
```bash
ANTHROPIC_API_KEY=sk-ant-your-key
OPENAI_API_KEY=sk-your-key
JWT_SECRET=local-development-secret
AWS_REGION=us-east-1
```

### Production (AWS Secrets Manager)
```bash
# Implemented in TASK-008
# Secrets stored securely in AWS Secrets Manager
# Retrieved at runtime by application
```

### Kubernetes (kubectl secrets)
```bash
kubectl create secret generic ai-credentials \
  --from-literal=anthropic-api-key=$ANTHROPIC_API_KEY \
  --from-literal=openai-api-key=$OPENAI_API_KEY
```

## Git Security

### Store Credentials Securely
```bash
# Use Git Credential Manager (not hardcoded tokens)
git config --global credential.helper manager-core

# Or use SSH keys instead of HTTPS tokens
# See: https://docs.github.com/en/authentication/connecting-to-github-with-ssh
```

### Check for Exposed Secrets
```bash
# Search code for common secret patterns
grep -r "sk-\|password=\|api_key=" src/

# Check git history
git log -p --all | grep -i "secret\|api.key\|password"
```

## Application Security

- **JWT Secrets**: Never logged or exposed
- **Password Validation**: Handled by Cognito (not custom code)
- **API Keys**: Validated at filter/controller level
- **Error Messages**: Don't reveal system details

## Infrastructure Security

### AWS
- ✅ S3 buckets have versioning (data recovery)
- ✅ DynamoDB encrypted at rest
- ✅ IAM roles use least privilege
- ✅ VPC isolates resources
- ✅ API Gateway validates JWT

### Kubernetes
- ✅ Secrets mounted as volumes (not env vars)
- ✅ Pod Security Contexts enforce non-root
- ✅ Network Policies restrict traffic
- ✅ Resource limits prevent DoS

## Monitoring & Alerts

Set up CloudWatch alarms for:
- ❌ Authentication failures (401s spike)
- ❌ Authorization failures (403s spike)
- ❌ Database throttling
- ❌ API errors (500s)
- ❌ High latency (p99 > 5s)

See TASKS.md for TASK-009 (CloudWatch Dashboards).

## Incident Response

If credentials are exposed:
1. **Revoke immediately** (GitHub, AWS, etc.)
2. **Rotate all credentials** (new tokens, keys)
3. **Review access logs** (S3, DynamoDB, API Gateway)
4. **Invalidate sessions** (force re-authentication)
5. **Document incident** (security log, postmortem)

## Compliance

This project follows:
- ✅ OWASP Top 10 security practices
- ✅ 12-factor app methodology (secrets via env vars)
- ✅ AWS Well-Architected Framework (security pillar)
- ✅ Kubernetes security best practices

## Further Reading

See `.claude/memory/SECURITY_CHECKLIST.md` for:
- Complete pre-deployment checklist
- Secret rotation schedule
- Tool recommendations
- Compliance guidelines

---

**Questions?** Check SECURITY_CHECKLIST.md or TASKS.md (TASK-008, TASK-009, TASK-015)
