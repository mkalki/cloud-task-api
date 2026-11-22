# PRD — CloudTask v3: Distributed Job Orchestrator

| | |
|---|---|
| Owner | Mohan R (`mkalki`) |
| Status | Draft v1 — written 22 Nov 2026 |
| Builds on | CloudTask v1 (Task API with JWT, rate limiting, audit, CI/CD, EC2, Grafana) |
| Timeline | 12 weeks, ~4 h/day on the project + 2 h/day DSA (see §11) |

---

## 1. Problem statement

Web requests must return in milliseconds, but real products also need work that is **slow, scheduled, repeated, or fragile**: send a digest email, call a partner webhook that is sometimes down, generate a report after data is cleaned, clean up old rows every night.

Doing this inside the request thread, or with a bare `@Scheduled` method, fails in predictable ways:

| Failure | What happens today |
|---|---|
| Server restarts mid-work | Work is silently lost |
| Two app instances run | Work runs twice |
| Downstream service is down | Work fails once and is forgotten |
| Step B needs step A's output | Nothing enforces the order |
| Something breaks at 3 AM | Nobody knows, nothing to inspect |

**CloudTask v3 is a job orchestrator**: users submit jobs (single or as dependency graphs); the system schedules them, runs them on a pool of workers, retries failures with backoff, quarantines hopeless jobs in a dead-letter queue, survives crashes, and shows everything live.

## 2. Goals and non-goals

**Goals**
- G1. Jobs are never lost (Postgres is the source of truth; Redis is rebuildable).
- G2. Jobs run **at least once**, with idempotency support so effects happen once.
- G3. Delayed and priority-ordered scheduling.
- G4. Horizontally scalable workers (more containers = more throughput).
- G5. Dependency-aware workflows (DAGs) with cycle rejection and failure propagation.
- G6. Automatic retry with exponential backoff + jitter; dead-letter queue with admin retry.
- G7. Survives worker crash, scheduler crash, and Redis flush, **proven by automated chaos tests**.
- G8. Live visibility (WebSocket) and metrics (Prometheus/Grafana).
- G9. Every claim in the README is backed by a test or a measured number.

**Non-goals**
- Competing with Airflow/Temporal/Sidekiq. This is a learning-grade, honestly scoped system.
- Exactly-once execution (impossible in general; we document why).
- Multi-region, Kubernetes, microservices, Kafka, multi-tenant billing, a polished SPA.
- Running arbitrary user code. Jobs run **built-in handlers only** (allowlisted types).

## 3. Users

| Persona | Needs |
|---|---|
| **API user** (authenticated) | Submit jobs/workflows, see status and history, cancel, watch live |
| **Admin** | Inspect/retry dead jobs, see worker and queue health |
| **Operator** (the owner, via Grafana) | Know queue depth, failure rate, worker count, latency |

## 4. Current state (v1)

A secured Task management REST API: register/login/refresh/logout with JWT, `USER`/`ADMIN`, ownership isolation, per-user Bucket4j rate limit, audit log, JPA Specifications filtering, soft delete, Flyway, Testcontainers tests, Docker Compose, GitHub Actions CI/CD to EC2, Prometheus + Grafana dashboard. It **stores** tasks; it **executes** nothing.

## 5. Target state (v3)

```
                        ┌───────────────────────────────────────────┐
 Client ── REST/WS ───► │ API profile                                │
                        │  JWT · rate limit · /jobs /workflows /admin│
                        │  STOMP WebSocket (JWT-auth, per-user)      │
                        └──────┬───────────────────────┬────────────┘
                               │ write                 │ subscribe
                               ▼                       ▲
                      ┌─────────────────┐      ┌───────┴────────┐
                      │ PostgreSQL      │      │ Redis          │
                      │ SOURCE OF TRUTH │◄────►│ queues + pubsub│
                      │ jobs, deps,     │ recon│ (rebuildable)  │
                      │ attempts, ...   │      └───┬────────┬───┘
                      └────────▲────────┘          │        │
                               │ conditional UPDATE │        │ claim (Lua)
        ┌──────────────────────┴──┐        ┌───────▼────────▼───────┐
        │ Scheduler profile       │        │ Worker profile  × N     │
        │ promote due · reaper ·  │        │ claim · lease · execute │
        │ reconciler · cron       │        │ retry/DLQ · events      │
        └─────────────────────────┘        └─────────────────────────┘
                 Prometheus scrapes API + every worker → Grafana
```

---

## 6. Functional requirements by feature

Each feature = one branch/PR (`AGENTS.md` §4). IDs map to `docs/AGENT_PROMPTS.md`.

Priority: **M** = must (core of the project), **S** = should, **C** = could.

### F0 — Repo prep and Redis infrastructure (M)
- FR-0.1 Redis 7 in `compose.yaml` with AOF persistence, volume, healthcheck; **not published on a public host port**.
- FR-0.2 `spring-boot-starter-data-redis`; config via env (`REDIS_HOST`, `REDIS_PORT`); `/actuator/health` reports Redis.
- FR-0.3 Profiles `api`, `scheduler`, `worker`; default (none) = `api,scheduler`.
- FR-0.4 Shared Testcontainers setup for Postgres + Redis (singleton, fast).
- FR-0.5 CD workflow still passes with Redis; README "Future Enhancements" updated to the real roadmap.
- **AC:** existing tests green; `docker compose up` healthy; Redis down ⇒ health shows DOWN for redis, app still serves `/tasks`.

### F1 — Job domain and submission API (M)
- FR-1.1 Tables (Flyway): `jobs`, `job_attempts` (see §8.2).
- FR-1.2 `POST /jobs` — body `{type, payload, priority(0-9, default 5), runAt|delaySeconds, maxAttempts(default 3, max 10), timeoutSeconds(default 300, max 3600)}`; optional header `Idempotency-Key`.
- FR-1.3 `GET /jobs` (paginated, filter by status/type), `GET /jobs/{id}` (includes attempts), `POST /jobs/{id}/cancel`.
- FR-1.4 Validation: `type` must be a registered handler; payload ≤ 64 KB valid JSON; `runAt` ≤ 1 year ahead.
- FR-1.5 Ownership isolation (404 for others' jobs, like v1 tasks); audit log entries for submit/cancel.
- FR-1.6 Idempotency: same `(owner, Idempotency-Key)` returns the original job (200) instead of a duplicate.
- FR-1.7 Per-user cap on active (non-terminal) jobs (default 1000) → `429`/`409` with clear error.
- **AC:** integration tests for create/get/list/cancel/ownership/idempotency/validation; jobs sit in `PENDING`.

### F2 — Redis scheduler: delayed + priority (M)
- FR-2.1 On submit (after DB commit) put the job in Redis `jobs:scheduled` (ZSET, score = `runAt` ms). Immediate jobs may go straight to `jobs:ready`.
- FR-2.2 **Promotion:** every `cloudtask.scheduler.poll-interval` (default 1 s) a Lua script atomically moves up to N due jobs from `scheduled` → `ready` and the scheduler sets DB status `PENDING → QUEUED` (conditional).
- FR-2.3 **Priority:** `ready` ZSET with score `(9 − priority) × 10^14 + enqueueEpochMs` ⇒ higher priority first, FIFO within a priority.
- FR-2.4 **Multiple scheduler instances are safe** (atomic Lua; no duplicate enqueue).
- FR-2.5 **Reconciler** (on startup and every 60 s): rebuilds Redis from Postgres (`PENDING` ⇒ scheduled; `QUEUED` not in ready/processing ⇒ ready). `FLUSHALL` on Redis loses nothing.
- FR-2.6 Cancel removes the job from Redis queues (best effort) and sets DB `CANCELLED` (authoritative).
- **AC:** tests — due job promoted once with 3 concurrent schedulers; priority order respected; Redis flushed ⇒ reconciler restores; cancelled job never runs.

### F3 — Worker runtime (M)
- FR-3.1 `worker` profile runs a bounded executor (`cloudtask.worker.concurrency`, default 4) that claims jobs continuously.
- FR-3.2 **Claim:** Lua pops best job from `ready` and adds it to `processing` ZSET with score = lease deadline (default 30 s). Then DB conditional update `QUEUED → RUNNING` incrementing `attempt`, setting `locked_by`, `lease_expires_at`, `started_at`. 0 rows ⇒ discard (cancelled/duplicate).
- FR-3.3 **Lease renewal** every 10 s while running (extends DB lease + processing score).
- FR-3.4 **Completion/failure** updates are fenced: `WHERE id=? AND status='RUNNING' AND attempt=? AND locked_by=?`. A stale worker's late result is ignored and logged.
- FR-3.5 **Handler registry**: `JobHandler{type(), handle(JobContext)}`; built-ins:
  - `demo.work` — sleeps `durationMs`, fails with probability `failProbability` (for demos/tests).
  - `http.webhook` — POSTs payload to a URL; 2xx = success; **SSRF-protected** (http/https only, block private/loopback/link-local/metadata IPs, connect+read timeouts, redirect limit).
  - `tasks.daily-digest` — builds a per-user summary from the existing Task data (counts by status/overdue) and sends via an `EmailSender` interface (log-only implementation).
  - `maintenance.cleanup` — hard-deletes soft-deleted tasks and old attempts older than N days.
- FR-3.6 **Timeouts:** per-job `timeoutSeconds` enforced cooperatively (interrupt); a handler ignoring interrupts is documented as a known limit; lease expiry still recovers the job.
- FR-3.7 **Graceful shutdown:** stop claiming, wait for in-flight (grace period configurable), leave the rest to the reaper.
- FR-3.8 Every attempt (success or failure) writes a `job_attempts` row (worker, start, end, outcome, error, duration).
- FR-3.9 Worker heartbeat key `worker:{id}:hb` (TTL 15 s) + worker registry for the dashboard.
- **AC:** 1 API + 2 workers: 100 `demo.work` jobs complete, each exactly one SUCCEEDED attempt, load split across workers; no job executed concurrently by two workers (test with a counter handler).

### F4 — Retries, DLQ, crash recovery (M)
- FR-4.1 Retry policy: delay = `min(cap, base × 2^(attempt−1))` with **full jitter** (`random(0, delay)`); base 2 s, cap 5 min; configurable.
- FR-4.2 `PermanentJobFailure` (e.g. HTTP 4xx, validation error) skips retries → `DEAD`.
- FR-4.3 Attempts exhausted ⇒ status `DEAD` (the DLQ **is** `status='DEAD'` in Postgres, with last error; Redis not needed for it).
- FR-4.4 `GET /admin/jobs?status=DEAD`, `POST /admin/jobs/{id}/retry` (resets attempts, → `PENDING`), `DELETE /admin/jobs/{id}`; `ROLE_ADMIN` only; audited.
- FR-4.5 **Reaper** (scheduler profile, every 5 s): finds expired leases in `processing`; if DB says `RUNNING` and lease expired ⇒ record attempt outcome `WORKER_LOST`, apply retry/dead policy; if DB says `QUEUED` (worker died between Redis claim and DB update) ⇒ push back to `ready`.
- FR-4.6 A late "success" from a presumed-dead worker is rejected by fencing (FR-3.4) — covered by a test.
- **AC:** `kill -9` a worker mid-job ⇒ job finishes on another worker within lease + reaper interval; backoff delays observed in attempts table; DEAD after N attempts; admin retry works.

### F5 — Workflows (DAGs) (M)
- FR-5.1 `POST /workflows` body: `{name, jobs:[{key,type,payload,priority,maxAttempts,timeoutSeconds}], dependencies:[{from,to}]}`; max 100 jobs, 500 edges.
- FR-5.2 Validation with **Kahn's algorithm**: reject cycles (422, message includes the offending job keys), unknown keys, self-edges, duplicates.
- FR-5.3 Storage: `workflows`, `job_dependencies(job_id, depends_on_job_id)`, and `jobs.pending_parents` counter. Roots start `PENDING`; others `BLOCKED`.
- FR-5.4 **Resolution:** on parent success, in the **same DB transaction** decrement children's `pending_parents`; children reaching 0 become `PENDING` (immediately enqueued after commit; reconciler covers a crash between commit and enqueue).
- FR-5.5 **Failure propagation:** parent `DEAD` ⇒ all transitive descendants `UPSTREAM_FAILED` (terminal). Workflow status derived: `RUNNING / SUCCEEDED / FAILED / CANCELLED`.
- FR-5.6 `GET /workflows/{id}` returns graph with per-job status; `POST /workflows/{id}/cancel` cancels all non-terminal jobs.
- FR-5.7 Admin retry of a `DEAD` job in a workflow re-opens its `UPSTREAM_FAILED` descendants to `BLOCKED`.
- **AC:** diamond DAG runs in the correct order with parallel branches; cycle rejected; dead parent cancels descendants; crash between parent commit and child enqueue still progresses (reconciler test).

### F6 — Real-time dashboard (S, but expected)
- FR-6.1 STOMP over WebSocket at `/ws`, **JWT validated on CONNECT**; reject unauthenticated.
- FR-6.2 Workers/scheduler publish job events to Redis Pub/Sub channel `job-events` (a worker is a different JVM from the API; Spring `ApplicationEvent` alone cannot cross processes).
- FR-6.3 API instances subscribe and forward each event to **only the owner's** session via `/user/queue/job-events`; admins also get `/topic/admin/events` (workers, DLQ).
- FR-6.4 Event payload: `{jobId, workflowId?, type, status, attempt, ts}` — no payload data.
- FR-6.5 Pub/Sub is fire-and-forget ⇒ on connect/reconnect the client re-fetches state via REST. Documented.
- FR-6.6 One static `dashboard.html` (vanilla JS) served by the API: job table + live status, worker list, queue depths, DLQ count.
- **AC:** user A never receives user B's events (test); unauthenticated connect rejected; status changes appear live.

### F7 — Observability (M)
- FR-7.1 Micrometer metrics: `jobs_queue_depth{queue=scheduled|ready|processing}`, `jobs_dead_total` (gauge from DB count), `jobs_executed_total{type,outcome}`, `jobs_attempt_duration_seconds{type}`, `jobs_retries_total{type}`, `jobs_lease_expired_total`, `jobs_schedule_lag_seconds` (runAt → RUNNING), `workers_active`.
- FR-7.2 Workers expose actuator metrics (management port) and Prometheus discovers **all replicas** (Docker DNS service discovery).
- FR-7.3 Grafana dashboard JSON committed: queue depth, throughput, success/failure rate, retry rate, p50/p95 duration, schedule lag, active workers, DEAD count.
- FR-7.4 Structured log line per transition; `MDC` carries `jobId`, `workerId`.
- **AC:** metrics visible in Prometheus; dashboard imports from JSON; label cardinality bounded.

### F8 — Scale, chaos, load (M)
- FR-8.1 `docker compose up --scale worker=N` works.
- FR-8.2 **Chaos suite** (scripted, repeatable, documented): kill worker mid-job; kill scheduler; restart Redis; `FLUSHALL`; stop Postgres briefly; duplicate-submit with same idempotency key.
- FR-8.3 **Load test** (k6 or a small script): submit 1k–10k `demo.work` jobs; record submit p95, end-to-end latency p50/p95/p99, jobs/s for 1/2/3/5 workers, DB and Redis CPU.
- FR-8.4 Results go into `docs/BENCHMARKS.md` with hardware, config, and caveats.
- **AC:** chaos scenarios documented with expected vs actual; no lost jobs, no concurrent double-execution observed; benchmark table filled with real numbers.

### F9 — Recurring jobs (cron) (S)
- FR-9.1 `POST/GET/DELETE /schedules` `{type,payload,cron,timezone,priority}`.
- FR-9.2 Scheduler materializes the next occurrence as a normal job; **unique `(schedule_id, fire_time)`** prevents double firing with multiple schedulers.
- FR-9.3 Missed-run policy while down: fire once for the latest missed time (documented), not a flood.
- **AC:** two schedulers ⇒ exactly one job per fire time; DST/timezone test.

### F10 — Hardening and deploy (M)
- FR-10.1 Quotas and limits verified (payload size, workflow size, active jobs, `maxAttempts`).
- FR-10.2 Security review: SSRF tests, ownership tests on every new endpoint, admin endpoints, WebSocket auth, no payload in logs/events.
- FR-10.3 EC2: resource-sized deploy (JVM `-Xmx` per container, worker count appropriate for instance memory), **Postgres/Redis/Prometheus not exposed publicly**, Grafana protected.
- FR-10.4 CD health check also verifies Redis and at least one live worker.
- FR-10.5 Optional: Nginx + HTTPS.
- **AC:** deployed to EC2; smoke script submits a workflow and verifies completion end-to-end.

### F11 — Docs and interview pack (M)
- FR-11.1 README rewritten: problem, architecture diagram, state machine, Redis key design, guarantees (and non-guarantees), benchmarks, chaos results, trade-offs, how to run.
- FR-11.2 ADRs in `docs/adr/` for: Postgres-as-truth, at-least-once, leases+fencing, DLQ in Postgres, Redis Pub/Sub bridge, monolith+profiles, jitter backoff.
- FR-11.3 `docs/INTERVIEW_QA.md` verified against the code; `[MEASURED]` placeholders replaced.
- FR-11.4 A 2-minute demo script and screenshots/GIF of the dashboard.

### F12 — Optional extras (C) — only if everything above is done
- 12a. `QueueBackend` interface with a Postgres `FOR UPDATE SKIP LOCKED` implementation; benchmark vs Redis backend; write up the trade-off.
- 12b. AI failure analysis for DEAD jobs (local model via Ollama) shown on the dashboard — labelled as an experiment.

---

## 7. Non-functional requirements

| Area | Requirement |
|---|---|
| **Durability** | Acknowledged submit (HTTP 201) means the job is in Postgres. |
| **Delivery** | At-least-once; duplicate execution possible only after lease expiry/crash; fenced so only one result is accepted. |
| **Ordering** | Priority then FIFO per priority; no global ordering guarantee; DAG order guaranteed by dependencies. |
| **Latency (hypotheses to measure, not promises)** | Submit API p95 < 100 ms; schedule lag p95 < 2 s at default poll; recovery of a crashed worker's job < lease + 10 s. |
| **Throughput** | Measured in F8 on stated hardware; README reports measured numbers only. |
| **Availability** | Any single worker or scheduler can die with no job loss. API stateless. Single Redis and single Postgres are acknowledged SPOFs (documented). |
| **Security** | JWT on all endpoints except auth; ownership checks; admin RBAC; SSRF protection; no payloads in logs/events; secrets via env. |
| **Observability** | Metrics (F7), logs with IDs, audit trail, attempts table. |
| **Testability** | Real Postgres/Redis via Testcontainers; chaos scripts; Clock injection. |
| **Compatibility** | v1 endpoints and tests unchanged. |

---

## 8. Design

### 8.1 Job state machine

```
 BLOCKED ──(all parents SUCCEEDED)──► PENDING ──(runAt reached, promoted)──► QUEUED
    │                                    ▲                                     │ claim
    │ parent DEAD                        │ retry: run_at = now + backoff       ▼
    ▼                                    └──────(attempts left)────────── RUNNING ──► SUCCEEDED
 UPSTREAM_FAILED                                                              │
                                                  (attempts exhausted / permanent)
                                                                              ▼
                                                                            DEAD ──(admin retry)──► PENDING
 any non-terminal ──(cancel)──► CANCELLED
 Terminal: SUCCEEDED, DEAD*, CANCELLED, UPSTREAM_FAILED     (*DEAD can be re-opened by admin)
```

### 8.2 Data model (Postgres)

```
jobs(
  id uuid PK, owner_id bigint FK users, workflow_id uuid NULL FK,
  type varchar, payload jsonb, priority smallint, status varchar,
  attempt int, max_attempts int, timeout_seconds int,
  run_at timestamptz, pending_parents int default 0,
  locked_by varchar NULL, lease_expires_at timestamptz NULL,
  idempotency_key varchar NULL, last_error text NULL,
  started_at, finished_at, created_at, updated_at, version bigint)
  UNIQUE (owner_id, idempotency_key) WHERE idempotency_key IS NOT NULL
  INDEX (status, run_at) · INDEX (owner_id, created_at) · INDEX (workflow_id)

job_attempts(id, job_id FK, attempt_no, worker_id, started_at, finished_at,
             outcome [SUCCEEDED|FAILED|TIMEOUT|WORKER_LOST|FENCED], error, duration_ms)

workflows(id uuid, owner_id, name, status, created_at)
job_dependencies(job_id FK, depends_on_job_id FK, PK(job_id, depends_on_job_id))
recurring_schedules(id, owner_id, type, payload, cron, timezone, priority,
                    next_fire_at, enabled)  + UNIQUE firing log (schedule_id, fire_time)
```

### 8.3 Redis keys (all in `RedisKeys`)

| Key | Type | Purpose |
|---|---|---|
| `jobs:scheduled` | ZSET score=runAt ms | Delayed jobs and retry backoff |
| `jobs:ready` | ZSET score=`(9-priority)*1e14+enqueueMs` | Runnable jobs, priority then FIFO |
| `jobs:processing` | ZSET score=lease deadline ms | In-flight jobs (for the reaper) |
| `worker:{id}:hb` | STRING TTL 15 s | Liveness |
| `job-events` | Pub/Sub channel | Cross-process event bridge to WebSocket |

Atomic scripts: `promote_due.lua` (scheduled→ready), `claim.lua` (ready→processing), `renew_lease.lua`.

### 8.4 Key decisions (each becomes an ADR)

1. **Postgres = truth, Redis = speed.** Dual-write gap handled by reconciler.
2. **At-least-once + idempotency**, not exactly-once.
3. **Leases + fencing (attempt number) instead of trusting a Redis lock.** A TTL lock can expire while a paused JVM still thinks it holds it; the DB conditional update is what guarantees correctness.
4. **DLQ = `DEAD` rows in Postgres** (queryable, auditable) instead of a Redis ZSET.
5. **Dependency counter** (`pending_parents`) resolved on completion instead of polling parents every tick.
6. **Redis Pub/Sub bridge** for real-time because workers and API are separate JVMs.
7. **Monolith + profiles** instead of microservices.
8. **Full-jitter backoff** to avoid synchronized retry storms.

### 8.5 API surface

```
POST   /jobs                     GET /jobs   GET /jobs/{id}   POST /jobs/{id}/cancel
POST   /workflows                GET /workflows/{id}          POST /workflows/{id}/cancel
POST   /schedules  GET /schedules  DELETE /schedules/{id}                 (F9)
GET    /admin/jobs?status=DEAD   POST /admin/jobs/{id}/retry   DELETE /admin/jobs/{id}
GET    /admin/workers            GET /admin/queues
WS     /ws  → subscribe /user/queue/job-events, /topic/admin/events
(existing) /auth/*  /tasks/*  /actuator/*  /swagger-ui
```

---

## 9. Risks

| Risk | Mitigation |
|---|---|
| Scope creep / not finishing | Cut line in §11; F0–F5 + F7 + F8 already make a strong project |
| Subtle race conditions | Conditional SQL + Lua + Testcontainers concurrency tests + independent audit prompt |
| Owner can't explain AI-written code | Teach-back block per PR; owner re-implements one core piece (Lua claim or Kahn) by hand |
| EC2 too small for many JVMs | Set `-Xmx`, fewer workers on EC2, run scale demos locally |
| DSA neglected | 2 h/day non-negotiable; project is the second priority |
| Overclaiming in interviews | Use the honest framing in `PROJECT_JOURNEY.md` |

## 10. Success criteria (project is "done")

- [ ] Chaos suite passes: no lost job, no accepted double result, across all scenarios in F8.
- [ ] A 4-job diamond workflow runs end to end on EC2 via the smoke script.
- [ ] Benchmarks recorded with real numbers and stated hardware.
- [ ] README explains guarantees *and* limits.
- [ ] Owner can whiteboard the architecture and the failure handling without notes in 10 minutes.

## 11. Milestones, status, cut line

Start date assumed **Mon 23 Nov 2026**. If you started earlier, shift the dates, not the order.

| Wk | Dates | Feature | Pri | Status |
|---|---|---|---|---|
| 1 | 23 Nov | F0 prep + F1 job domain/API | M | ☐ |
| 2 | 30 Nov | F2 scheduler (delay, priority, reconciler) | M | ☐ |
| 3 | 7 Dec | F3 workers (claim, lease, handlers, shutdown) | M | ☐ |
| 4 | 14 Dec | F4 retry/backoff, DLQ, reaper | M | ☐ |
| 5–6 | 21 Dec, 28 Dec | F5 DAG workflows | M | ☐ |
| 7 | 4 Jan | F6 real-time (Pub/Sub → STOMP) + dashboard | S | ☐ |
| 8 | 11 Jan | F7 observability | M | ☐ |
| 9 | 18 Jan | F8 scale, chaos, load | M | ☐ |
| 10 | 25 Jan | F9 recurring jobs · F10 hardening | S/M | ☐ |
| 11 | 1 Feb | F10 deploy · F11 docs | M | ☐ |
| 12 | 8 Feb | F11 interview pack · mock interviews · (F12 optional) | M | ☐ |

**Cut line if behind:** drop F9, F12, Nginx/HTTPS, and shrink F6 to one admin topic. **Never cut:** F1–F5, F7, F8, F11. After F4 you already have a resume-worthy "distributed job queue with retries, DLQ and crash recovery" — add it to your resume then.

## 12. Glossary

**At-least-once** — a job may run more than once, never zero times. **Idempotent** — running twice has the same effect as once. **Lease** — time-limited right to run a job; must be renewed. **Fencing** — rejecting results from a worker that lost its lease, using a monotonically increasing attempt number. **DLQ** — place for jobs that exhausted retries. **DAG** — directed acyclic graph of dependencies. **Reaper** — process that reclaims jobs whose lease expired. **Reconciler** — process that rebuilds Redis state from Postgres.
