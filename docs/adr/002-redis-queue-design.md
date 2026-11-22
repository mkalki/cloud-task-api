# ADR 002: Redis queue design: ZSET scheduled + ZSET ready + Lua atomicity

- **Status:** Accepted
- **Date:** 2026-11-22
- **Feature:** F2

## Context

Submitted jobs must start at their run time, in priority order, and several scheduler instances may run at once. PostgreSQL stays the source of truth (jobs are never lost if Redis is wiped). Redis only makes promotion fast. Correctness must not depend on a Redis lock or TTL.

Two things had to be decided: the Redis data structures, and how a Lua script learns a job's priority.

## Decision

Two sorted sets, moved by one Lua script:

- `jobs:scheduled`: member `priority:jobId`, score = run-at epoch ms. Due jobs are the members with score <= now.
- `jobs:ready`: member `jobId`, score = `(9 - priority) * 10^14 + dueMs`. The lowest score is served first, so priority 9 comes before 0, and jobs of equal priority are first in, first out. 9 * 10^14 is below 2^53, so the score is exact in a double.
- `jobs:processing`: member `jobId`, score = lease deadline. Written by workers from F3. F2 only reads it so that a job a worker holds is never put back in ready.

**Priority is encoded in the `jobs:scheduled` member** (`priority:jobId`). `promote_due.lua` splits the member and computes the ready score itself, in the same atomic step that removes the member from scheduled.

`promote_due.lua` takes the due members (limit N), adds each to ready with `ZADD NX`, and removes it from scheduled. Redis runs a script without interleaving other commands, so two schedulers calling it at the same time never receive the same job.

The scheduler then runs `UPDATE jobs SET status='QUEUED' ... WHERE id=? AND status='PENDING'` through `JobStateMachine.transitionIf`. Redis is not consulted for that decision.

A reconciler (at startup and every 60 s) reads PostgreSQL in pages. It writes every `PENDING` row to scheduled and every `QUEUED` row that is in neither ready nor processing to ready. Both writes are idempotent, so many instances can run it together.

## Alternatives considered

| Option | Pros | Cons |
|---|---|---|
| A (chosen): priority encoded in the scheduled member | One key per queue, no extra reads, nothing to keep in sync. Atomic by construction | A job's priority is fixed in its member, so a priority change needs a remove and re-add. Members need parsing |
| B: hash `job:meta:{id}` holding priority | Priority can change without touching the member | A second key per job to create, expire and clean up. A flushed or partly missing hash breaks promotion. Needs a missing-hash fallback |
| C: one ZSET with the priority score from the start | Simplest | Delayed jobs cannot be separated from runnable ones, so there is nothing to poll by time |
| D: Redis lock around promotion | Familiar | Violates the rule that correctness never relies on a Redis lock. A TTL lock can expire while the holder is paused |
| E: Postgres only (`FOR UPDATE SKIP LOCKED`) | One system less | Higher polling load on the database; Redis was chosen for the project goals (ADR scope, PRD 8.4) |

## Consequences

- **Dual write.** The row commits first, then Redis is written. If the process dies or Redis is down between the two, the reconciler repairs it within 60 s. A Redis failure never fails the HTTP request.
- **Crash during promotion.** If a scheduler dies after the Lua step but before the SQL update, the job is in ready while its row is still PENDING. The reconciler puts it back in scheduled. The next promote finds it already in ready (`ZADD NX` keeps its place) and the SQL update then succeeds.
- **Cancel race.** The row becomes CANCELLED first. A promotion that raced with the cancel updates no row, finds the row terminal, and removes the Redis entry. A cancelled row is never QUEUED.
- **Starvation is accepted.** A steady stream of high-priority jobs can keep low-priority jobs waiting indefinitely. Aging or weighted queues are possible later and are out of scope.
- **Ordering within a priority** uses the due time, not the submit time. A job delayed 60 s sorts by the moment it became due.
- **Reconciler cost** is two index scans per run (`idx_jobs_waiting_run_at`, V11), paged by id with a database-supplied cursor.
- Tested against real Redis and PostgreSQL: delay, 3 concurrent promoters, priority order, `FLUSHALL` recovery, cancel before due, Redis failure on submit and cancel, and the crash between the two promotion steps.
