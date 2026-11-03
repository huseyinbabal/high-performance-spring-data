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

	private final SqlSearchService sql;

	private final SearchService search;

	private final IndexingService indexing;

	public SearchController(SqlSearchService sql, SearchService search, IndexingService indexing) {
		this.sql = sql;
		this.search = search;
		this.indexing = indexing;
	}

	/** v1 — PostgreSQL, LIKE '%q%'. */
	@GetMapping("/v1/search")
	SearchResult searchSql(@RequestParam String q) {
		return sql.search(q);
	}

	/** v2 — Elasticsearch: analyzed full-text, typo tolerance, filters and facets in one request. */
	@GetMapping("/v2/search")
	SearchResult search(@RequestParam(required = false) String q, @RequestParam(required = false) String city,
			@RequestParam(required = false) String category, @RequestParam(required = false) BigDecimal maxPrice,
			@RequestParam(defaultValue = "0") int page) {
		return search.search(q, city, category, maxPrice, page);
	}

	@GetMapping("/v2/search/suggest")
	List<String> suggest(@RequestParam String q) {
		return search.suggest(q);
	}

	@GetMapping("/analytics/calendar")
	List<MonthBucket> calendar(@RequestParam(required = false) String city) {
		return search.calendar(city);
	}

	@PostMapping("/v1/search/reindex")
	IndexingResult indexOneByOne(@RequestParam(defaultValue = "2000") int limit) {
		return indexing.indexOneByOne(limit);
	}

	@PostMapping("/v2/search/reindex")
	IndexingResult reindex() {
		return indexing.reindex();
	}
}
