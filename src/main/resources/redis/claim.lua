-- Takes the best job from jobs:ready and registers it in jobs:processing in one atomic step.
-- KEYS[1] jobs:ready       members jobId, lowest score first (priority, then FIFO)
-- KEYS[2] jobs:processing  members jobId, score = lease deadline in epoch ms
-- ARGV[1] lease deadline in epoch ms
-- Returns the bare job id, or nil when jobs:ready is empty.
local popped = redis.call('ZPOPMIN', KEYS[1])
if #popped == 0 then
    return nil
end

-- jobs:ready holds bare job ids. If a "priority:jobId" member (the jobs:scheduled format) ever
-- shows up, only the id part is registered and returned.
local member = popped[1]
local separator = string.find(member, ':', 1, true)
if separator then
    member = string.sub(member, separator + 1)
end

redis.call('ZADD', KEYS[2], ARGV[1], member)
return member
