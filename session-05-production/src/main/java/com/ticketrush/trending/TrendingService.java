package com.ticketrush.trending;

import java.time.Instant;
import java.util.List;

import com.ticketrush.catalog.EventInfo;
import com.ticketrush.catalog.EventService;
import com.ticketrush.messaging.SalesProjection;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TrendingService {

	private final StringRedisTemplate redis;

	private final EventService events;

	public TrendingService(StringRedisTemplate redis, EventService events) {
		this.redis = redis;
		this.events = events;
	}

	/** The projection SalesProjection maintains from the orders topic (Session 4): no SQL, always current. */
	public Trending live() {
		var top = redis.opsForZSet().reverseRangeWithScores(SalesProjection.KEY, 0, 9);
		List<TrendingEvent> result = top.stream().map(entry -> {
			EventInfo event = events.info(Long.parseLong(entry.getValue()));
			return new TrendingEvent(event.id(), event.name(), event.artist(), entry.getScore().longValue());
		}).toList();
		return new Trending(result, Instant.now());
	}
}
