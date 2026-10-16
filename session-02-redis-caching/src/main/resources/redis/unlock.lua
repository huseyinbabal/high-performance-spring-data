-- Release a lock ONLY if it is still mine. A plain DEL could delete the lock of whoever
-- took it after mine expired.
-- KEYS[1] = lock key · ARGV[1] = my token · Returns 1 released, 0 not mine (any more)
if redis.call('GET', KEYS[1]) == ARGV[1] then
  return redis.call('DEL', KEYS[1])
end
return 0
