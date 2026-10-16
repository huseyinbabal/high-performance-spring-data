package com.ticketrush.lock;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * A lock every app instance can see, because it lives in Redis instead of in one JVM's memory.
 * Three rules: take it with a TTL in ONE command · release it only with your own token · treat it as advisory.
 */
@Component
public class RedisLock {

	private static final RedisScript<Long> UNLOCK = RedisScript.of(new ClassPathResource("redis/unlock.lua"),
			Long.class);

	private final StringRedisTemplate redis;

	public RedisLock(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/**
	 * @param ttl  how long the lock survives if its holder dies — must be longer than the work
	 * @param wait how long a caller queues for the lock before giving up with 503
	 */
	public <T> T withLock(String key, Duration ttl, Duration wait, Supplier<T> work) {
		String token = UUID.randomUUID().toString();
		long deadline = System.nanoTime() + wait.toNanos();

		// SET key token NX PX ttl — "only if absent, and expire": taking the lock and its TTL is one atomic command
		while (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, ttl))) {
			if (System.nanoTime() > deadline) {
				throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "busy: could not get " + key);
			}
			pause();
		}
		try {
			return work.get();
		}
		finally {
			redis.execute(UNLOCK, List.of(key), token);
		}
	}

	/** Wait a few ms, with jitter, so that waiters do not all retry in the same instant. */
	private static void pause() {
		try {
			Thread.sleep(2 + ThreadLocalRandom.current().nextInt(6));
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
