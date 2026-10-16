package com.ticketrush.trending;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import com.ticketrush.order.OrderRepository;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Top 10 events by tickets sold in the last 30 days" — an aggregate over ~86,000 orders.
 * Indexed and still ~40 ms: this is the kind of query a cache is for.
 */
@Service
public class TrendingService {

	public static final String CACHE = "trending";

	public static final String CACHE_SYNC = "trending-sync";

	private final OrderRepository orders;

	private final AtomicLong computations = new AtomicLong();

	public TrendingService(OrderRepository orders) {
		this.orders = orders;
	}

	/** v1 — every request runs the aggregate. */
	@Transactional(readOnly = true)
	public Trending compute() {
		computations.incrementAndGet();
		Instant now = Instant.now();
		return new Trending(orders.topSelling(now.minus(Duration.ofDays(30)), Limit.of(10)), now);
	}

	/**
	 * v2 — cache-aside. Fine until the entry expires under load: every request that arrives during the
	 * ~40 ms recomputation misses too, and they ALL run the aggregate at once (cache stampede).
	 */
	@Cacheable(cacheNames = CACHE, key = "'top10'")
	@Transactional(readOnly = true)
	public Trending cached() {
		return compute();
	}

	/** v3 — sync = true: one caller recomputes, everyone else waits for that result. */
	@Cacheable(cacheNames = CACHE_SYNC, key = "'top10'", sync = true)
	@Transactional(readOnly = true)
	public Trending cachedSync() {
		return compute();
	}

	public long computations() {
		return computations.get();
	}

	public void resetComputations() {
		computations.set(0);
	}
}
