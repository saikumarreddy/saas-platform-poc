## Project Memory Index

This directory contains session memories and project context for future Claude sessions.

### Quick Reference
- [project_overview.md](memory/project_overview.md) — Project status, accomplishments, how to use
- [implementation_patterns.md](memory/implementation_patterns.md) — Code patterns used (Spring AI, EventBridge, S3, K8s)
- [session_summary.md](memory/session_summary.md) — Complete build session transcript and decisions
- [task_management.md](memory/task_management.md) — How to track, update, and manage tasks
- [SECURITY_CHECKLIST.md](memory/SECURITY_CHECKLIST.md) — Secret management, security checklist, incident response

### How to Use These Files

When starting a new session:
1. Read **CLAUDE.md** in project root (quick reference guide)
2. Skim **project_overview.md** (2 min, understand current state)
3. Check **implementation_patterns.md** when writing new code (reference existing patterns)
4. Review **session_summary.md** if needing to recall design decisions

### Important Notes

- All code is committed to git (3 commits, clean tree)
- CLAUDE.md contains quick commands (docker-compose, mvn, git)
- Project is ready for local dev (docker-compose) and AWS deployment (Terraform)
- Key patterns: Spring AI ChatClient, EventBridge publishers, TenantAwareFilter, S3 presigned URLs

### File Descriptions

| File | Purpose | Read When |
|------|---------|-----------|
| project_overview.md | Status, accomplishments, architecture, endpoints, how to use | Starting a new session |
| implementation_patterns.md | Code patterns with examples (Spring AI, S3, EventBridge, K8s) | Adding new features |
| session_summary.md | Complete session history, decisions, timeline, feedback | Need detailed context |
| task_management.md | How to track tasks, current backlog, recommended next steps | Planning new work |
