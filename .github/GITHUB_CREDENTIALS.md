# GitHub Push Commands - Quick Reference

**After creating token and repository:**

```bash
# Navigate to project
cd d:/JAVA/saas-platform-poc

# Option 1: Add remote with embedded token (replace values)
git remote add origin https://YOUR_TOKEN@github.com/YOUR_USERNAME/saas-platform-poc.git

# Option 2: Add remote without token (git will prompt)
git remote add origin https://github.com/YOUR_USERNAME/saas-platform-poc.git

# Verify remote added
git remote -v

# Push all commits
git push -u origin main

# (When prompted for credentials if using Option 2)
# Username: YOUR_GITHUB_USERNAME
# Password: YOUR_PERSONAL_ACCESS_TOKEN
```

**Verify on GitHub:**
- Go to: https://github.com/YOUR_USERNAME/saas-platform-poc
- Should see 5 commits, all files, CLAUDE.md, TASKS.md, .claude/ directory

**Token Creation Link:**
https://github.com/settings/tokens/new

**Important:** Token expires after ~90 days. GitHub Actions will fail after expiration. Create new token before expiration.
