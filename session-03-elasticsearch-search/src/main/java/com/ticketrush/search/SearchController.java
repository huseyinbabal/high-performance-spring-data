package com.ticketrush.search;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;

import com.ticketrush.search.EventProjector.IndexingResult;
import com.ticketrush.search.EventQueryService.Browse;
import com.ticketrush.search.EventQueryService.MonthBucket;

import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

	private final SqlSearchService sql;

	private final EventQueryService search;

	private final EventProjector indexing;

	public SearchController(SqlSearchService sql, EventQueryService search, EventProjector indexing) {
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
		return search.handle(new SearchEventsQuery(q, city, category, maxPrice, page, "now"));
	}

	/** v3 — the same search, with the date filter rounded to the minute so Elasticsearch can cache it. */
	@GetMapping("/v3/search")
	SearchResult searchCached(@RequestParam(required = false) String q, @RequestParam(required = false) String city,
			@RequestParam(required = false) String category, @RequestParam(required = false) BigDecimal maxPrice,
			@RequestParam(defaultValue = "0") int page) {
		return search.handle(new SearchEventsQuery(q, city, category, maxPrice, page, "now/m"));
	}

	/** v1 — deep paging with from + size. Page 500 and beyond: Elasticsearch refuses (index.max_result_window). */
	@GetMapping("/v1/events/browse")
	Browse browsePage(@RequestParam(defaultValue = "0") int page) {
		return search.browsePage(page);
	}

	/** v2 — search_after: pass the `next` value of the previous answer, as deep as you like. */
	@GetMapping("/v2/events/browse")
	Browse browseAfter(@RequestParam(required = false) List<Long> after) {
		return after == null ? search.browsePage(0) : search.browseAfter(List.copyOf(after));
	}

	@GetMapping("/v2/search/suggest")
	List<String> suggest(@RequestParam String q) {
		return search.suggest(q);
	}

	@GetMapping("/analytics/calendar")
	List<MonthBucket> calendar(@RequestParam(required = false) String city) {
		return search.calendar(city);
	}

	/** Show what Elasticsearch said (e.g. "Result window is too large") instead of a bare 500. */
	@ExceptionHandler(UncategorizedElasticsearchException.class)
	ResponseEntity<String> elasticsearchFailed(UncategorizedElasticsearchException e) {
		String reason = e.getCause() instanceof ElasticsearchException es
				? es.error().rootCause().stream().map(ErrorCause::reason).findFirst().orElse(es.getMessage())
				: e.getMessage();
		return ResponseEntity.badRequest().body(reason + "\n");
	}

	@PostMapping("/v1/search/reindex")
	IndexingResult indexOneByOne(@RequestParam(defaultValue = "2000") int limit) {
		return indexing.indexOneByOne(limit);
	}

	@PostMapping("/v2/search/reindex")
	IndexingResult reindex(@RequestParam(defaultValue = "1") int shards, @RequestParam(defaultValue = "2000") int bulk,
			@RequestParam(defaultValue = "-1") String refresh, @RequestParam(defaultValue = "true") boolean swap)
			throws IOException {
		return indexing.reindex(shards, bulk, refresh, swap);
	}
}
