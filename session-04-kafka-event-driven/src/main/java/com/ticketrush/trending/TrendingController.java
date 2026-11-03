package com.ticketrush.trending;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrendingController {

	private final TrendingService trending;

	public TrendingController(TrendingService trending) {
		this.trending = trending;
	}

	/** v1 — SQL aggregate, cached. */
	@GetMapping("/v1/trending")
	Trending top10() {
		return trending.top10();
	}

	/** v2 — event-driven projection in Redis. */
	@GetMapping("/v2/trending")
	Trending live() {
		return trending.live();
	}
}
