-- Moves the lease deadline of a job that is still in jobs:processing.
-- KEYS[1] jobs:processing
-- ARGV[1] jobId   ARGV[2] new lease deadline in epoch ms
-- Returns 1 if the job was still there and its deadline changed, 0 otherwise.
-- It never adds a member: a job that was released or taken over must not come back.
if redis.call('ZSCORE', KEYS[1], ARGV[1]) then
    redis.call('ZADD', KEYS[1], 'XX', ARGV[2], ARGV[1])
    return 1
end
return 0
