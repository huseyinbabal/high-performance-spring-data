package com.ticketrush.search;

import java.math.BigDecimal;
import java.util.List;

import com.ticketrush.search.IndexingService.IndexingResult;
import com.ticketrush.search.SearchService.MonthBucket;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

	private final SearchService search;

	private final IndexingService indexing;

	public SearchController(SearchService search, IndexingService indexing) {
		this.search = search;
		this.indexing = indexing;
	}

	@GetMapping("/search")
	SearchResult search(@RequestParam(required = false) String q, @RequestParam(required = false) String city,
			@RequestParam(required = false) String category, @RequestParam(required = false) BigDecimal maxPrice,
			@RequestParam(defaultValue = "0") int page) {
		return search.search(q, city, category, maxPrice, page);
	}

	@GetMapping("/search/suggest")
	List<String> suggest(@RequestParam String q) {
		return search.suggest(q);
	}

	@GetMapping("/analytics/calendar")
	List<MonthBucket> calendar(@RequestParam(required = false) String city) {
		return search.calendar(city);
	}

	@PostMapping("/search/reindex")
	IndexingResult reindex() {
		return indexing.reindex();
	}
}
