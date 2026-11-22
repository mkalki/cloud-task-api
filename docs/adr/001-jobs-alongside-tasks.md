# ADR 001: Jobs are added alongside Tasks (no rename)

- **Status:** Accepted
- **Date:** 2026-11-22
- **Feature:** F1

## Context

CloudTask v1 stores user tasks (to-do items) and executes nothing. v3 needs background jobs with types, payloads, retries and workers. A job looks like a task at first glance (title-less, owner, status, priority), so renaming `Task` to `Job` was one option.

The two concepts differ:

| | Task | Job |
|---|---|---|
| Meaning | Something a person must do | Work a machine runs |
| Id | `BIGINT` identity | `UUID`, safe to create in any process |
| Status values | TODO, IN_PROGRESS, COMPLETED, CANCELLED | 8 states with a transition table |
| Concurrency | Single-user edits | Many processes update one row, so it uses `@Version` |
| Delete | Soft delete | Never deleted by users; kept for the audit trail |

## Decision

Add `Job` and `JobAttempt` as new entities and tables (`jobs`, `job_attempts`) in a new `job` package. Leave `Task` and every `/tasks` endpoint unchanged.

Related choices made in F1:

- `JobStateMachine` is the only class that changes `Job.status`. `Job.setStatus` is package-private and a test checks it is not public.
- `Job` does not extend `BaseEntity`. `BaseEntity` uses `LocalDateTime` and soft delete, while jobs use `Instant` (UTC) with `timestamptz` columns.
- `audit_logs.resource_id` is a `BIGINT`, so job audit rows put the job UUID in the `changes` JSON and leave `resource_id` empty. Changing the column type would touch a v1 table for no gain.
- `jobs.workflow_id` has no foreign key until the `workflows` table exists in F5.
- Idempotency uses a partial unique index on `(owner_id, idempotency_key)`. A request that loses a race catches the unique violation and returns the winner's job.

## Alternatives considered

| Option | Pros | Cons |
|---|---|---|
| Add Job beside Task (chosen) | v1 API, tests and data stay untouched. Each model fits its purpose | Two similar-looking concepts to explain |
| Rename Task to Job | One concept | Breaks the v1 API and tests, and needs a risky data migration for a deployed system |
| Add a `type` column to Task | One table | Mixes human to-dos with machine work. Task ids and statuses do not suit job needs |

## Consequences

- Existing `/tasks` behavior and tests do not change.
- New job code lives in feature packages (`job` now; `queue`, `scheduler`, `worker` later), as in AGENTS.md.
- Job-related audit entries use new `JOB_SUBMITTED`, `JOB_CANCELLED` actions and a `JOB` resource type. Migration V10 extends the audit table constraints.
- Cancelling a job in F1 only updates PostgreSQL. F2 adds best-effort removal from Redis queues.
