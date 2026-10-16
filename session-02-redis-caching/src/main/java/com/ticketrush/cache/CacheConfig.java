package com.ticketrush.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import com.ticketrush.catalog.EventInfo;
import com.ticketrush.catalog.EventService;
import com.ticketrush.trending.Trending;
import com.ticketrush.trending.TrendingService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@Configuration
@EnableCaching
public class CacheConfig {

	@Bean
	RedisCacheManagerBuilderCustomizer caches(RedisConnectionFactory redis,
			@Value("${ticketrush.cache.event-ttl}") Duration eventTtl,
			@Value("${ticketrush.cache.trending-ttl}") Duration trendingTtl) {
		// Every cache gets an explicit TTL and a JSON serializer: readable in redis-cli,
		// and not tied to Java serialization (records are not Serializable anyway).
		RedisCacheConfiguration event = json(EventInfo.class)
			// jitter: 10 min ± up to 60 s, so entries written together do not all expire together
			.entryTtl((key, value) -> eventTtl.plusSeconds(ThreadLocalRandom.current().nextInt(60)));
		RedisCacheConfiguration trending = json(Trending.class).entryTtl(trendingTtl);

		// @Cacheable(sync = true) only synchronizes if the writer takes a lock. The default writer does not;
		// this one keeps the lock in Redis (<cache>~lock), so it holds across every app instance.
		return builder -> builder.cacheWriter(RedisCacheWriter.lockingRedisCacheWriter(redis))
			.withCacheConfiguration(EventService.EVENT_CACHE, event)
			.withCacheConfiguration(TrendingService.CACHE, trending)
			.withCacheConfiguration(TrendingService.CACHE_SYNC, trending);
	}

	private static <T> RedisCacheConfiguration json(Class<T> type) {
		return RedisCacheConfiguration.defaultCacheConfig()
			.disableCachingNullValues()
			.serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(type)));
	}
}
