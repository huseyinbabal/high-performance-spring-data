package com.ticketrush.trending;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrendingController {

	private final TrendingService trending;

	public TrendingController(TrendingService trending) {
		this.trending = trending;
	}

	@GetMapping("/trending")
	Trending top10() {
		return trending.top10();
	}
}
