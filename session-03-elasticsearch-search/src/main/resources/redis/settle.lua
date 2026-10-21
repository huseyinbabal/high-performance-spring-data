-- Settle a hold: remove it, and decide what happens to its tickets.
-- KEYS[1] = tt:{id}:stock · KEYS[2] = tt:{id}:holds
-- ARGV    = now, token, qty, giveBack ("1" = cancel: tickets return to stock)
-- Returns: 1 the hold was still valid · 0 unknown or expired
local score = redis.call('ZSCORE', KEYS[2], ARGV[2])
if not score then
  return 0
end
redis.call('ZREM', KEYS[2], ARGV[2])
local expired = tonumber(score) <= tonumber(ARGV[1])
if expired or ARGV[4] == '1' then
  redis.call('INCRBY', KEYS[1], tonumber(ARGV[3]))
end
if expired then
  return 0
end
return 1
