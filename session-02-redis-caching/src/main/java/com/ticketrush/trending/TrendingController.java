package com.ticketrush.trending;

import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrendingController {

	private final TrendingService trending;

	public TrendingController(TrendingService trending) {
		this.trending = trending;
	}

	@GetMapping("/v1/trending")
	Trending compute() {
		return trending.compute();
	}

	@GetMapping("/v2/trending")
	Trending cached() {
		return trending.cached();
	}

	@GetMapping("/v3/trending")
	Trending cachedSync() {
		return trending.cachedSync();
	}

	/** How many times did we actually run the aggregate? The number the stampede demo is about. */
	@GetMapping("/trending/computations")
	Map<String, Long> computations() {
		return Map.of("computations", trending.computations());
	}

	@DeleteMapping("/trending/computations")
	void reset() {
		trending.resetComputations();
	}
}
