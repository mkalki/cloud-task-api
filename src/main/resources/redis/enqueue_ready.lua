-- Adds a job to jobs:ready unless it is already ready or held by a worker.
-- KEYS[1] jobs:ready   KEYS[2] jobs:processing
-- ARGV[1] jobId        ARGV[2] ready score (integer)
-- Returns 1 if the job was added, 0 otherwise.
if redis.call('ZSCORE', KEYS[2], ARGV[1]) then
    return 0
end
return redis.call('ZADD', KEYS[1], 'NX', ARGV[2], ARGV[1])
