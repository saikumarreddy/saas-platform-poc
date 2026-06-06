---
name: Task Management Guide
description: How to track, update, and manage tasks for the SaaS Platform POC
type: reference
---

## Task Management System

The project uses **TASKS.md** (in project root) to track all work items.

## File Structure

```
TASKS.md (project root)
├── Backlog (15+ items with effort estimates)
├── Current Sprint (active tasks)
├── In Progress (currently being worked on)
└── Completed (done tasks with ✅)
```

## Task Format

Each task includes:
- **TASK-XXX**: Unique identifier
- **Status**: pending | in_progress | completed | blocked | on_hold
- **Description**: What and why
- **Effort**: Estimated hours
- **Files**: Which files to modify
- **Dependencies**: Prerequisite tasks
- **Blocked By**: What's blocking this
- **Notes**: Additional context

## Workflow

### 1. Starting a Task

```bash
# Edit TASKS.md
# Change status: pending → in_progress

# Make code changes
git add .
git commit -m "work: TASK-001 start streaming support"
```

### 2. During Development

- Keep TASKS.md updated if scope changes
- Reference task in commit messages: `feat: TASK-001 add...`
- Update dependencies if discovered
- Document blockers

### 3. Completing a Task

```bash
# Run tests first
mvn verify

# Edit TASKS.md
# Move task to Completed section, add ✅

# Commit
git commit -m "feat: TASK-001 add Spring AI streaming support"
```

## Current Backlog Summary

| Task | Category | Effort | Dependencies |
|------|----------|--------|--------------|
| TASK-001 | Features | 2h | None |
| TASK-002 | Features | 3h | None |
| TASK-003 | Features | 4h | None |
| TASK-004 | Features | 5h | TASK-003 |
| TASK-005 | Features | 3h | None |
| TASK-006 | DevOps | 1h | None |
| TASK-007 | DevOps | 1h | None |
| TASK-008 | DevOps | 2h | TASK-007 |
| TASK-009 | DevOps | 2h | None |
| TASK-010 | Testing | 4h | None |
| TASK-011 | Testing | 3h | None |
| TASK-012 | Testing | 3h | TASK-006 |
| TASK-013 | Docs | 2h | None |
| TASK-014 | Docs | 2h | None |
| TASK-015 | Docs | 1h | None |

## Recommended First Tasks (Next Steps)

1. **TASK-010** (Integration Tests) — Ensure code quality, 4h
2. **TASK-001** (Streaming) — Improve UX, 2h
3. **TASK-005** (Datadog) — Enable monitoring, 3h
4. **TASK-009** (CloudWatch) — Ops visibility, 2h

Total: ~11 hours for solid foundation.

## Tips for Future Sessions

- Review TASKS.md at start of session to see what's pending
- Check dependencies before starting (prerequisites completed?)
- Update task status when starting/completing work
- Use task numbers in commit messages for traceability
- Move completed tasks to "Completed ✅" section with date
- If adding new tasks, follow the template in TASKS.md

## Critical Dependencies

```
TASK-004 (Elasticsearch) ← TASK-003 (Lambda)
TASK-008 (Secrets Manager) ← TASK-007 (Remote State)
TASK-012 (Performance Tests) ← TASK-006 (HPA)
```

## Effort Breakdown by Category

- **Features**: 17h total (streaming, memory, Lambda, Elasticsearch, Datadog)
- **DevOps**: 7h total (HPA, remote state, secrets, dashboards)
- **Testing**: 10h total (integration, contracts, performance)
- **Docs**: 5h total (ADRs, runbooks, security checklist)

**Total Backlog**: ~39 hours of work
