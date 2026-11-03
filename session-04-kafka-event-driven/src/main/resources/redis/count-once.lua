-- Idempotent increment: remember the message id and count it, atomically.
-- KEYS[1] = trending:tickets (ZSET: event id → tickets sold) · KEYS[2] = trending:seen:<message id>
-- ARGV    = eventId, quantity · Returns 1 counted, 0 duplicate
if redis.call('SET', KEYS[2], '1', 'NX', 'EX', 86400) then
  redis.call('ZINCRBY', KEYS[1], tonumber(ARGV[2]), ARGV[1])
  return 1
end
return 0
