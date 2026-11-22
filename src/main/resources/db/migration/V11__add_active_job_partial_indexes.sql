-- Partial indexes that only contain jobs that have not finished. Finished jobs pile up
-- over time, so these stay small while idx_jobs_owner_created and idx_jobs_status_run_at grow.
--
-- PostgreSQL only uses a partial index when the query's WHERE clause provably implies the
-- index's WHERE clause, so queries must spell the statuses out as literals (not bind parameters).

-- Active-job cap: count(*) ... where owner_id = ? and status in (<active statuses>).
CREATE INDEX idx_jobs_owner_active
    ON jobs (owner_id)
    WHERE status IN ('BLOCKED', 'PENDING', 'QUEUED', 'RUNNING');

-- Scheduler and reconciler (F2): PENDING jobs that are due and QUEUED jobs, by run_at.
CREATE INDEX idx_jobs_waiting_run_at
    ON jobs (status, run_at)
    WHERE status IN ('PENDING', 'QUEUED');
