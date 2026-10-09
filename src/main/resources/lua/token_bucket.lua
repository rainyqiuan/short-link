local key = KEYS[1]          -- shortlink:limit:redirect:<ip>
local capacity = tonumber(ARGV[1]) -- 桶的容量
local rate = tonumber(ARGV[2])     -- 速率
-- 统一成「秒，double」（System.currentTimeMillis() / 1000.0 或 Instant.now().toEpochMilli() / 1000.0）
local now = tonumber(ARGV[3])      -- 当前时间

local tokens = tonumber(redis.call('hget', key, 'tokens'))
local lastRefill = tonumber(redis.call('hget', key, 'lastRefill'))

if tokens == nil then
    tokens = capacity
    lastRefill = now
else
    local timePassed = now - lastRefill
    if timePassed > 0 then
        local newTokens = timePassed * rate
        tokens = math.min(capacity, tokens + newTokens) -- 必须封顶，防止撑爆
        lastRefill = now                                -- 更新上次补充时间
    end
end

local allowed = 0
local retryAfter = 0
if tokens >= 1 then
    allowed = 1
    tokens = tokens - 1
else
    retryAfter = math.ceil((1 - tokens) / rate)
end

redis.call('hset', key, 'tokens', tokens)
redis.call('hset', key, 'lastRefill', lastRefill)
redis.call('expire', key, math.max(3600, math.ceil(capacity/rate)))

if allowed == 1 then
    return 0
else
    return retryAfter
end