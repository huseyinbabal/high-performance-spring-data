package com.ticketrush.hold;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** Per-customer fixed-window limit, shared by every app instance because the counter lives in Redis. */
@Component
public class RateLimiter {

	private static final RedisScript<Long> SCRIPT = RedisScript.of(new ClassPathResource("redis/rate-limit.lua"),
			Long.class);

	private final StringRedisTemplate redis;

	private final int holdsPerMinute;

	public RateLimiter(StringRedisTemplate redis, @Value("${ticketrush.rate-limit.holds-per-minute}") int limit) {
		this.redis = redis;
		this.holdsPerMinute = limit;
	}

	public boolean allowHold(long customerId) {
		long window = System.currentTimeMillis() / 60_000;
		String key = "rate:holds:" + customerId + ":" + window;
		return redis.execute(SCRIPT, List.of(key), String.valueOf(holdsPerMinute), "60") == 1;
	}
}
