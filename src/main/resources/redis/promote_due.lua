-- Moves due jobs from jobs:scheduled to jobs:ready in one atomic step.
-- KEYS[1] jobs:scheduled   members "priority:jobId", score = run-at ms
-- KEYS[2] jobs:ready       members jobId, score = (9 - priority) * 1e14 + due ms
-- KEYS[3] jobs:processing  members jobId (jobs that a worker holds)
-- ARGV[1] now in ms        ARGV[2] maximum number of jobs to move
-- Returns the ids that were moved.
local due = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'WITHSCORES', 'LIMIT', 0, tonumber(ARGV[2]))
local promoted = {}

for i = 1, #due, 2 do
    local member = due[i]
    local dueMs = tonumber(due[i + 1])
    local separator = string.find(member, ':', 1, true)

    if separator then
        local priority = tonumber(string.sub(member, 1, separator - 1))
        local id = string.sub(member, separator + 1)

        -- A job that a worker already holds must not go back to the ready queue.
        if priority and not redis.call('ZSCORE', KEYS[3], id) then
            local score = (9 - priority) * 100000000000000 + dueMs
            -- NX keeps the original place if the job is already ready.
            redis.call('ZADD', KEYS[2], 'NX', string.format('%.0f', score), id)
            promoted[#promoted + 1] = id
        end
    end
    -- Malformed members are dropped too, so they cannot block the head of the queue.
    redis.call('ZREM', KEYS[1], member)
end

return promoted
