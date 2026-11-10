-- Fixed-window counter. INCR and EXPIRE must be one atomic step:
-- done as two commands, a crash in between leaves a counter that never expires.
-- KEYS[1] = rate:{customer}:{window} · ARGV = limit, windowSeconds · Returns 1 allowed, 0 rejected
local count = redis.call('INCR', KEYS[1])
if count == 1 then
  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
end
if count > tonumber(ARGV[1]) then
  return 0
end
return 1
