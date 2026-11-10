package com.ticketrush.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import com.ticketrush.catalog.EventInfo;
import com.ticketrush.catalog.EventService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@Configuration
@EnableCaching
public class CacheConfig {

	@Bean
	RedisCacheManagerBuilderCustomizer caches(@Value("${ticketrush.cache.event-ttl}") Duration eventTtl) {
		RedisCacheConfiguration event = RedisCacheConfiguration.defaultCacheConfig()
			.disableCachingNullValues()
			.serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(EventInfo.class)))
			.entryTtl((key, value) -> eventTtl.plusSeconds(ThreadLocalRandom.current().nextInt(60)));

		// The locking cache writer from Session 2 is gone: its only customer was the trending cache
		// (sync = true), and that cache was replaced by a projection in Session 4. A lock nobody needs
		// is not free — every read of the cache has to check it.
		return builder -> builder.enableStatistics()     // cache_gets_total{result="hit|miss"} in Prometheus
			.withCacheConfiguration(EventService.EVENT_CACHE, event);
	}
}
