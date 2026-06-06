---
name: Security Checklist & Secret Management
description: How to properly manage secrets and maintain security
type: reference
---

## 🔒 Secrets Management

### What NOT to Do
❌ **Never commit secrets** (.env files, API keys, tokens, passwords)  
❌ **Never embed tokens in git remote URL** (use credential manager instead)  
❌ **Never log secrets** (check application logging)  
❌ **Never share tokens via chat/email** (use secure vaults)  
❌ **Never hardcode secrets in code** (use environment variables)

### What TO Do
✅ **Use environment variables** for all secrets  
✅ **Use .gitignore** for .env files (already configured)  
✅ **Use .env.example** showing structure (no values)  
✅ **Use credential managers** (git credential helper, GitHub token manager)  
✅ **Rotate tokens regularly** (90-day expiration)  
✅ **Use AWS Secrets Manager** (for production - TASK-008)  
✅ **Use Kubernetes Secrets** (for deployed apps)

---

## 📋 Pre-Deployment Security Checklist

Run this before every deployment:

### Code Secrets
- [ ] No API keys in `*.java` files (`grep -r "sk-" src/`)
- [ ] No passwords in configuration files
- [ ] All secrets use environment variables
- [ ] `.env` file is in `.gitignore`
- [ ] `application.yml` uses `${ENV_VAR}` syntax, not hardcoded values

### Git & Repository
- [ ] No secrets in git history (`git log -p | grep -i secret`)
- [ ] No tokens in `.git/config`
- [ ] `.gitignore` includes: `.env`, `*.key`, `secrets/`, `credentials.json`
- [ ] Remote URL has NO embedded credentials

### AWS Credentials
- [ ] No AWS keys in code or config
- [ ] IAM roles used (not root credentials)
- [ ] S3 buckets not public
- [ ] DynamoDB tables encrypted
- [ ] VPC properly configured (no public databases)

### Application Security
- [ ] JWT secrets not logged
- [ ] Password validation in place (Cognito)
- [ ] HTTPS enforced (API Gateway)
- [ ] CORS properly configured
- [ ] Rate limiting enabled

### Infrastructure Security
- [ ] Network policies restrict pod communication
- [ ] Pod security contexts set (runAsNonRoot, readOnlyRootFilesystem)
- [ ] Secrets mounted as volumes (not env vars in k8s)
- [ ] Resource limits/requests set
- [ ] Pod Disruption Budgets configured

### Monitoring & Logging
- [ ] Secrets excluded from logs
- [ ] Access logs enabled (ALB, API Gateway)
- [ ] CloudWatch alarms configured
- [ ] Audit logging enabled (CloudTrail)

---

## 🔑 Secret Management by Environment

### Local Development (.env)
```bash
# .env (in .gitignore - NOT committed)
ANTHROPIC_API_KEY=sk-ant-xxxxx
OPENAI_API_KEY=sk-xxxxx
JWT_SECRET=local-test-secret-do-not-use-in-prod
AWS_ENDPOINT_OVERRIDE=http://localstack:4566
```

**File structure:**
- `.env` — Actual secrets (git ignored)
- `.env.example` — Template with no values (committed)
- `.env.local` — Personal overrides (git ignored)

### Staging/Production (AWS)
```
Secrets Manager:
  /saas-platform/anthropic-api-key
  /saas-platform/openai-api-key
  /saas-platform/jwt-secret

Kubernetes Secrets:
  kubectl create secret generic ai-credentials \
    --from-literal=anthropic-api-key=$ANTHROPIC_API_KEY \
    --from-literal=openai-api-key=$OPENAI_API_KEY

Environment Variables (from Secrets):
  apiVersion: v1
  kind: Deployment
  spec:
    containers:
    - name: app
      env:
      - name: ANTHROPIC_API_KEY
        valueFrom:
          secretKeyRef:
            name: ai-credentials
            key: anthropic-api-key
```

---

## 🚨 If Secret Is Exposed

### Immediate Actions (within 5 min)
1. **Revoke the secret**
   - GitHub: https://github.com/settings/tokens → Revoke
   - AWS: AWS Console → Secrets Manager → Rotate

2. **Invalidate any cached credentials**
   - Kill existing sessions
   - Clear credential manager

3. **Generate new secret**
   - Create new token/key
   - Update in Secrets Manager/Kubernetes

### Investigation
1. Check git log for commit containing secret
2. Check if secret was used by attacker
3. Check CloudTrail for unauthorized access
4. Review access logs (S3, DynamoDB, API Gateway)

### Cleanup
1. Remove secret from git history (if committed):
   ```bash
   git filter-branch --tree-filter 'rm -f secrets.txt' HEAD
   git push --force origin main
   ```
2. Update all references to old secret
3. Document incident in security log

---

## 🛠️ Tools for Secret Management

### Local Development
- **Git Credential Manager** (stores creds securely)
  ```bash
  # Windows
  winget install Microsoft.Git.Credential.Manager
  
  # Mac
  brew install --cask git-credential-manager
  ```

### AWS Production
- **AWS Secrets Manager** (rotate, audit, encrypt)
  - Use TASK-008 to implement
  - Terraform: `aws_secretsmanager_secret`

- **AWS Systems Manager Parameter Store**
  - Free alternative for simpler secrets
  - Less features than Secrets Manager

### Kubernetes
- **Sealed Secrets** (encrypt secrets in git)
  - https://github.com/bitnami-labs/sealed-secrets
  - Can commit encrypted secrets safely

- **External Secrets Operator**
  - Sync from AWS Secrets Manager to K8s

---

## 📝 Current Status

✅ **Code**: No secrets exposed in git history  
✅ **Config**: `.env` properly in `.gitignore`  
✅ **Remote**: Token removed from git config  
✅ **Template**: `.env.example` shows structure  
⏳ **AWS**: Need to implement Secrets Manager (TASK-008)  
⏳ **K8s**: Need to use sealed secrets (future task)  

---

## 🔄 Rotation Schedule

| Secret | Type | Rotation | Next Review |
|--------|------|----------|------------|
| GitHub Token | PAT | 90 days | --- |
| JWT_SECRET | String | On key rotation | As needed |
| API Keys | String | Per provider policy | 180 days |
| DB Password | String | After deployment | 365 days |
| S3 Access Keys | AWS | 90 days | Per policy |

---

## References

- [OWASP: Secrets Management](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
- [AWS Secrets Manager Docs](https://docs.aws.amazon.com/secretsmanager/)
- [GitHub Token Security](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens)
- [Kubernetes Secrets](https://kubernetes.io/docs/concepts/configuration/secret/)
