-- Take a hold: atomically return expired holds to stock, then reserve `qty` tickets.
-- KEYS[1] = tt:{id}:stock   (tickets neither sold nor held)
-- KEYS[2] = tt:{id}:holds   (ZSET: member = hold token, score = expiry in epoch ms)
-- ARGV    = now, expiresAt, token, qty
-- Returns: remaining stock (>= 0) · -1 sold out · -2 stock not initialised yet
local now = tonumber(ARGV[1])
local qty = tonumber(ARGV[4])

local stock = redis.call('GET', KEYS[1])
if not stock then
  return -2
end

-- 1. holds whose time ran out give their tickets back (no background job, no keyspace events)
for _, token in ipairs(redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', now)) do
  redis.call('INCRBY', KEYS[1], tonumber(string.match(token, '^%d+%.(%d+)%.')))
  redis.call('ZREM', KEYS[2], token)
end

-- 2. check and take, in the same atomic step
stock = tonumber(redis.call('GET', KEYS[1]))
if stock < qty then
  return -1
end
redis.call('DECRBY', KEYS[1], qty)
redis.call('ZADD', KEYS[2], tonumber(ARGV[2]), ARGV[3])
return stock - qty
