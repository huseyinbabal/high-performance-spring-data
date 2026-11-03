package com.ticketrush.trending;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.ticketrush.catalog.EventInfo;
import com.ticketrush.catalog.EventService;
import com.ticketrush.messaging.SalesProjection;
import com.ticketrush.order.OrderRepository;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Limit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrendingService {

	public static final String CACHE = "trending";

	private final OrderRepository orders;

	private final StringRedisTemplate redis;

	private final EventService events;

	public TrendingService(OrderRepository orders, StringRedisTemplate redis, EventService events) {
		this.orders = orders;
		this.redis = redis;
		this.events = events;
	}

	/** v1 — the aggregate query behind a 30 s cache: up to 30 s stale, 65 ms whenever it expires. */
	@Cacheable(cacheNames = CACHE, key = "'top10'", sync = true)
	@Transactional(readOnly = true)
	public Trending top10() {
		Instant now = Instant.now();
		return new Trending(orders.topSelling(now.minus(Duration.ofDays(30)), Limit.of(10)), now);
	}

	/**
	 * v2 — read the projection that SalesProjection keeps up to date from the orders topic.
	 * No aggregate, no TTL, no stampede: the answer was computed when the orders happened.
	 */
	public Trending live() {
		var top = redis.opsForZSet().reverseRangeWithScores(SalesProjection.KEY, 0, 9);
		List<TrendingEvent> result = top.stream().map(entry -> {
			EventInfo event = events.info(Long.parseLong(entry.getValue()));   // from the event cache
			return new TrendingEvent(event.id(), event.name(), event.artist(), entry.getScore().longValue());
		}).toList();
		return new Trending(result, Instant.now());
	}
}
