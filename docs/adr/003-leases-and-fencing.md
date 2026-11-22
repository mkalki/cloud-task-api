# ADR 003: Leases + fencing, not Redis locks

- **Status:** Accepted
- **Date:** 2026-11-22
- **Feature:** F3a

## Context

Several workers on several machines take jobs from the same queue. Two things must hold:

1. A job is run by one worker at a time.
2. If a worker stops (crash, long GC pause, network cut), the job is not lost, and the stopped worker cannot later overwrite the result of whoever took over.

Delivery is at-least-once (golden rule 2). A job may run twice after a crash. What must never happen is two results being accepted for the same run.

## Decision

A claim is a **lease**: a job held for a limited time that the worker must keep renewing. Correctness comes from a **fence** in PostgreSQL. Redis only makes claiming fast.

**Claim** (two steps):

1. `claim.lua` pops the best id from `jobs:ready` and adds it to `jobs:processing` with score = lease deadline, in one atomic script.
2. One conditional update: `UPDATE jobs SET status=RUNNING, attempt=attempt+1, locked_by=?, lease_expires_at=?, started_at=?, version=version+1 WHERE id=? AND status=QUEUED`. It lives in `JobStateMachine.claim` next to `transitionIf`. 0 rows means the job was cancelled or the promoter has not finished its SQL step, so the worker removes the id from `jobs:processing` and moves on.

**The fence** is the pair `(attempt, locked_by)`. `attempt` rises with every claim, so a run is identified by its number and its worker. Every later write for the run repeats both:

- Result: `UPDATE ... WHERE id=? AND status=RUNNING AND attempt=? AND locked_by=?` (`JobStateMachine.transitionIfOwned`).
- Lease renewal: same condition (`JobRepository.extendLeaseIfOwned`).

If a write matches no row, the run no longer owns the job. The result is dropped, an attempt row with outcome `FENCED` is stored, and the log says "fenced result ignored".

**Renewal** runs every `lease-ms / 3`. It extends the deadline in Redis (`renew_lease.lua`, which returns 1 or 0 and never adds a missing member) and in the database. If either says the job is not ours, the handler is interrupted and no result is reported.

## Alternatives considered

| Option | Pros | Cons |
|---|---|---|
| A (chosen): lease + fence in PostgreSQL | Correct even if Redis loses data or a worker pauses. Redis only affects speed. One mechanism for results, renewal and cancel | One extra conditional update per claim and per result. Needs the attempt counter |
| B: Redis lock (`SET NX PX`) around the job | Familiar, one round trip | A TTL lock can expire while the holder is paused (GC, VM freeze). The holder then still believes it owns the job and writes its result. Redis cannot stop that write. Breaks the rule that correctness never depends on a Redis lock or TTL |
| C: Redlock across several Redis nodes | Fewer single points of failure | More moving parts and the same pause problem: no lock service can stop a stale client from writing. A fencing token checked at the resource is still needed |
| D: `SELECT ... FOR UPDATE SKIP LOCKED` and hold the transaction while the handler runs | No Redis at all | Holds a database connection and row lock for the whole job. Long jobs exhaust the pool. A crashed worker's lock is released only when the connection drops |
| E: exactly-once delivery | Nice to have | Not possible across a network boundary. The honest claim is at-least-once plus idempotent handlers |

## Consequences

- **Worker pause or lost lease.** The job keeps its RUNNING row. Another run (F4's reaper hands the job to a new worker with a higher `attempt`) becomes the owner. The old run's late result matches no row and is recorded as FENCED.
- **Cancel while running.** The row becomes CANCELLED. The worker's next renewal finds no RUNNING row, so it interrupts the handler; if the handler finishes first, its result is fenced. In both cases the status stays CANCELLED and an attempt row marks the run FENCED.
- **Interrupts only.** A timeout or lost lease calls `Future.cancel(true)`. Java cannot kill a thread, so a handler that ignores interrupts keeps running. The fence still protects the data, and the lease expires because the worker stops renewing. Handlers must be idempotent (the F3b handlers are written that way).
- **Worker stop.** Running jobs get a short grace period (5 s in F3a; F3c adds `shutdown-grace`). The rest are interrupted and report nothing. Their rows stay RUNNING until the reaper (F4) sees the lease expire.
- **Not recovered by F3a:** a RUNNING row whose worker died. The lease expires, but nothing reads it yet. F4 adds the reaper, retries and backoff. Until then any failure moves a job to DEAD.
- **Redis stays disposable.** If `jobs:processing` is wiped, running handlers notice at the next renewal (Redis answers 0) and stop. No state in Redis decides an outcome.
- **Clock use.** Lease deadlines use the worker's clock. Skew of a few seconds only moves expiry; the fence does not use time.
- **Tested** against real PostgreSQL and Redis: concurrent claims return each id once, 100 jobs on 3 workers each get exactly one SUCCEEDED attempt with no overlapping runs, a late result after takeover is FENCED, cancel while running stays CANCELLED, a lost lease interrupts the handler, and a claim of a row still PENDING is dropped and later recovered by the reconciler.
