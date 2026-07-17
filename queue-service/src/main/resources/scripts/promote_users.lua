-- KEYS[1]: waitlist ZSET key ({eventId}:waitlist)
-- KEYS[2]: active_sessions ZSET key ({eventId}:active_sessions)
-- KEYS[3]: heartbeats ZSET key ({eventId}:heartbeats)
-- ARGV[1]: max_active_sessions (integer)
-- ARGV[2]: heartbeat_grace_ms (integer)
-- ARGV[3]: current_time_ms (integer)

local max_expiry = tonumber(ARGV[3]) - tonumber(ARGV[2])
local expired = redis.call('ZRANGEBYSCORE', KEYS[3], '-inf', max_expiry)
local evicted = {}
for i = 1, #expired do
    local userId = expired[i]
    redis.call('ZREM', KEYS[2], userId)
    redis.call('ZREM', KEYS[3], userId)
    table.insert(evicted, userId)
end

local active_count = redis.call('ZCARD', KEYS[2])
local available_slots = tonumber(ARGV[1]) - active_count

local promoted = {}
if available_slots > 0 then
    local candidates = redis.call('ZPOPMIN', KEYS[1], available_slots)
    for i = 1, #candidates, 2 do
        local userId = candidates[i]
        redis.call('ZADD', KEYS[2], tonumber(ARGV[3]), userId)
        redis.call('ZADD', KEYS[3], tonumber(ARGV[3]), userId)
        table.insert(promoted, userId)
    end
end

return { cjson.encode(promoted), cjson.encode(evicted) }
