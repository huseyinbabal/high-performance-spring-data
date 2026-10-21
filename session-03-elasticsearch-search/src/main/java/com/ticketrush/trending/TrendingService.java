package com.ticketrush.trending;

import java.time.Duration;
import java.time.Instant;

import com.ticketrush.order.OrderRepository;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrendingService {

	public static final String CACHE = "trending";

	private final OrderRepository orders;

	public TrendingService(OrderRepository orders) {
		this.orders = orders;
	}

	/** Stampede-safe (Session 2): sync = true + the locking cache writer in CacheConfig. */
	@Cacheable(cacheNames = CACHE, key = "'top10'", sync = true)
	@Transactional(readOnly = true)
	public Trending top10() {
		Instant now = Instant.now();
		return new Trending(orders.topSelling(now.minus(Duration.ofDays(30)), Limit.of(10)), now);
	}
}
