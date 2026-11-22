# AGENTS.md — CloudTask v3 (Distributed Job Orchestrator)

You are a coding agent working in `mkalki/cloud-task-api`. Read this whole file at the start of every session. Then read the PRD section for the feature you were assigned (`docs/PRD.md`) and its prompt (`docs/AGENT_PROMPTS.md`).

The owner is a student building this to learn and to defend it in interviews. **Every line you write must be explainable by the owner.** Prefer boring, readable code over clever code.

---

## 1. Project snapshot

| Item | Value |
|---|---|
| Repo | `https://github.com/mkalki/cloud-task-api` (default branch `main`) |
| Base package | `com.mkalki.cloudtaskapi` |
| Stack | Java 21, Spring Boot 3, Spring Security + JWT (+refresh), Spring Data JPA/Hibernate, PostgreSQL 18, Flyway, Bucket4j, Micrometer/Prometheus/Grafana, Docker Compose, GitHub Actions CI/CD to AWS EC2 |
| Existing packages | `audit, config, context, controller, dto, entity, exception, ratelimit, repository, security, service, specification` |
| Existing API | `/auth/register`, `/auth/login`, `/auth/refresh`, `/tasks` CRUD (soft delete, filtering, pagination) |
| v1 status | Done and deployed. 71 commits. Unit + Testcontainers integration tests pass. |
| v3 goal | Evolve into a Redis-backed distributed job orchestrator (priority/delayed scheduling, workers, retries, DLQ, DAG workflows, live dashboard). See `docs/PRD.md`. |

Commands:

```bash
./mvnw test                      # full test suite (must stay green)
docker compose up -d --build     # full local stack
docker compose logs -f app
```

> Only the README was visible when this file was written. **Before coding any feature, inspect the real code** (existing exception handler, DTO style, audit logging, security config, test base classes) and match it.

---

## 2. Golden rules (architectural invariants — never violate)

1. **PostgreSQL is the source of truth. Redis is disposable.** If Redis is wiped, a reconciler must rebuild queues from Postgres with no job lost.
2. **Delivery is at-least-once. Handlers must be idempotent.** Never claim "exactly-once". Say "at-least-once + idempotent handlers = effectively-once".
3. **Correctness never depends on a Redis lock or TTL.** All job state transitions use conditional SQL updates (`... WHERE id=? AND status=? AND attempt=?`) — optimistic fencing. Redis only makes things fast.
4. **Every state transition goes through one place** (`JobStateMachine`/service). No controller or handler sets `status` directly.
5. **Redis multi-step operations are atomic** (Lua script or single command). No "read, then write" races across processes.
6. **Do not break v1.** `/auth/*` and `/tasks/*` behavior, tests, and Swagger must keep working. **Do not rename or remove `Task`.** Jobs are added alongside Tasks.
7. **Do not edit a Flyway migration that has been merged.** Add a new `V<next>__*.sql`. Check the highest existing version first.
8. **Ownership and isolation apply to jobs too.** A user can only see/cancel their own jobs/workflows/events. `ADMIN` endpoints are `ROLE_ADMIN` only. WebSocket topics must be per-user, never a global broadcast of user data.
9. **Monolith with profiles. No microservice split.** One codebase and one image. Profiles: `api`, `scheduler`, `worker`. With no profile set, behave as `api,scheduler` so existing tests and the current deploy keep working.
10. **No new heavy dependencies without asking** (no Kafka, Quartz, Spring Batch, JobRunr, Redisson). Allowed: `spring-boot-starter-data-redis` (Lettuce), `spring-boot-starter-websocket`, Resilience4j (only if PRD says so), Testcontainers modules.
11. **Never commit secrets.** No `.env`, passwords, keys, tokens, EC2 IPs. Use env vars with safe defaults only for local dev.
12. **Metric labels must be low-cardinality.** Never label by job ID, user ID, or payload. Allowed: `type`, `status`, `queue`, `outcome`.
13. **No stubs left behind.** No `TODO` that changes behavior, no commented-out code, no fake "simulated" success paths outside the explicit `demo.*` handlers.

---

## 3. Code conventions

- New code lives in feature packages under the base package: `job`, `workflow`, `queue`, `scheduler`, `worker`, `realtime`. Each may contain its own `entity/repository/service/dto`. Old layer-first packages stay as they are. **Do not mass-repackage v1.**
- Constructor injection only. No field `@Autowired`.
- Use `Instant` (UTC) for time. Inject a `Clock` so time-dependent logic is testable.
- DTOs for all API I/O. Validate with Jakarta Validation. Errors go through the existing global exception handler (same error shape as v1).
- Redis key names live in one class (`RedisKeys`). Lua scripts live in `src/main/resources/redis/*.lua` and are unit/integration-tested.
- Logging: SLF4J, one line per state transition with `jobId`, `type`, `attempt`, `workerId`. Never log payloads (may contain PII).
- Config via `@ConfigurationProperties` (`cloudtask.scheduler.*`, `cloudtask.worker.*`, `cloudtask.jobs.*`), with defaults documented in the README.
- Tests: JUnit 5 + Mockito for logic; **Testcontainers (real Postgres + real Redis)** for anything touching queues/state. No `Thread.sleep` in tests; use Awaitility.

---

## 4. Git workflow (mandatory — push after every feature)

**Why branches:** every push to `main` triggers CD, which hard-resets and redeploys the EC2 box. `main` must always be deployable. **Never push directly to `main`.**

```bash
git switch main && git pull --ff-only
git switch -c feat/<ID>-<slug>            # e.g. feat/F2-redis-scheduler
# ... work in small, logical commits ...
./mvnw test                                # must pass locally
git push -u origin feat/<ID>-<slug>
gh pr create --fill                        # if gh CLI is unavailable, print the PR title/body for the owner
# wait for CI green -> squash-merge -> CD deploys
git switch main && git pull --ff-only
git tag -a v3.<ID> -m "<ID>: <summary>" && git push origin v3.<ID>
```

Rules:

- **Conventional Commits:** `feat(scheduler): promote due jobs atomically via Lua`, `fix(...)`, `test(...)`, `docs(...)`, `chore(...)`, `refactor(...)`. Imperative, ≤72 chars subject.
- One feature = one branch = one PR. Commit at least at: schema, core logic, API, tests, docs.
- **Never** force-push, rewrite pushed history, or `git reset --hard` on a shared branch.
- If `git push` or `gh` fails due to missing credentials, **stop** and print the exact commands for the owner to run. Do not work around authentication.
- If CI is red, fix it on the same branch. Do not merge red.
- If a feature needs new env vars or secrets on EC2/GitHub, list them explicitly in the PR under **"Owner action required"**.
- PR description must contain: Summary, What changed, How to verify (copy-paste commands), Assumptions, Owner action required, and the **Teach-back** block (section 6).

---

## 5. Feature Delivery Protocol (do these steps in order, every feature)

1. **Read:** this file, the feature's PRD section, the feature's prompt, and the existing code it touches.
2. **Plan:** write a 5–10 line plan in the PR description draft (files, tables, keys, risks). If something is ambiguous, make the smallest reasonable assumption and record it under *Assumptions*. Ask the owner only if you are truly blocked.
3. **Implement** in small commits.
4. **Test:** unit tests + Testcontainers integration tests for every acceptance criterion in the PRD. Include at least one **failure-path** test (crash, duplicate, race, bad input).
5. **Run:** `./mvnw test` green. Then `docker compose up -d --build` and smoke-test the feature with real HTTP calls; paste the commands you used into the PR.
6. **Docs:** update (a) README feature table and architecture section, (b) the status checkbox for the feature in `docs/PRD.md` §11, (c) an ADR in `docs/adr/NNN-title.md` if you made a design decision, (d) `docs/INTERVIEW_QA.md` — verify answers for this feature match what you actually built; fix any that do not, and fill in `[MEASURED]` placeholders only with numbers you really measured.
7. **Ship:** commit, push branch, open PR, wait for CI, merge, tag (section 4).
8. **Report to the owner** in chat: what was built, how to verify, known limitations, and anything you were unsure about.

---

## 6. Teach-back block (required in every PR)

End the PR description with:

```
### Teach-back (owner must be able to explain these)
1. <concept> — see <path:line>
2. ...
(5 items: the 5 hardest-to-explain decisions in this feature, each with a file path and one sentence on WHY)
### Self-check questions
1. <question an interviewer would ask about this feature>
... (5 questions; answers go in docs/INTERVIEW_QA.md)
```

---

## 7. Definition of Done (all must be true)

- [ ] Acceptance criteria in `docs/PRD.md` for this feature are met and tested.
- [ ] `./mvnw test` passes locally and in CI.
- [ ] No v1 regression (`/auth`, `/tasks`, rate limiting, actuator health still tested).
- [ ] Docker Compose stack starts from clean (`docker compose down -v && docker compose up -d --build`) and `/actuator/health` is `UP`.
- [ ] New config documented; no secrets committed.
- [ ] Metrics/logs added for new state transitions where the PRD asks.
- [ ] Docs updated (README, PRD checkbox, ADR if needed, INTERVIEW_QA).
- [ ] Branch pushed, PR opened, CI green, merged, tag pushed.
- [ ] Teach-back block present.

---

## 8. Out of scope until told otherwise

Microservices, Kafka, Kubernetes, multi-region, a real email provider, a polished SPA frontend (a single static HTML dashboard page is enough), and **any LLM/AI feature (that is F12, optional and last)**.

## 9. Prompt index

All feature prompts are in `docs/AGENT_PROMPTS.md`: F0 repo prep · F1 job domain · F2 scheduler · F3 workers · F4 retry/DLQ/recovery · F5 DAG workflows · F6 real-time · F7 observability · F8 scale/chaos/load · F9 recurring jobs · F10 hardening/deploy · F11 docs/interview pack · F12 optional extras · plus the independent **audit** prompt.

Session starter to paste into the agent:

```text
Read AGENTS.md and docs/PRD.md. Then execute prompt <Fx> from docs/AGENT_PROMPTS.md exactly,
following the Feature Delivery Protocol. Do not start any other feature.
```
