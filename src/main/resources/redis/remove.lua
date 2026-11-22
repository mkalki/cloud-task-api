-- Removes a job from jobs:scheduled (whatever its priority) and from jobs:ready.
-- KEYS[1] jobs:scheduled   KEYS[2] jobs:ready   ARGV[1] jobId
-- Returns how many entries were removed.
local removed = 0
for priority = 0, 9 do
    removed = removed + redis.call('ZREM', KEYS[1], priority .. ':' .. ARGV[1])
end
removed = removed + redis.call('ZREM', KEYS[2], ARGV[1])
return removed
