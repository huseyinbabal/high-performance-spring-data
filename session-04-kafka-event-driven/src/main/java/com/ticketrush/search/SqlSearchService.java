package com.ticketrush.search;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ticketrush.catalog.EventRepository;
import com.ticketrush.search.SearchResult.Facet;
import com.ticketrush.search.SearchResult.Hit;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * v1 — search the way a relational database lets you: LIKE '%text%' over every searchable column.
 * A leading wildcard cannot use a B-tree index, so each of the three queries reads all 100,000 events.
 */
@Service
public class SqlSearchService {

	private final EventRepository events;

	public SqlSearchService(EventRepository events) {
		this.events = events;
	}

	@Transactional(readOnly = true)
	public SearchResult search(String text) {
		String pattern = "%" + text.toLowerCase() + "%";
		Instant now = Instant.now();

		List<Hit> hits = events.searchLike(pattern, now, Limit.of(20))
			.stream()
			.map(c -> new Hit(c.id(), c.name(), c.artist(), c.city(), c.venue(), c.startsAt(), c.fromPrice(), null))
			.toList();

		Map<String, List<Facet>> facets = new LinkedHashMap<>();
		facets.put("city", events.cityFacetLike(pattern, now));          // the sidebar needs counts:
		facets.put("category", events.categoryFacetLike(pattern, now));  // two more full scans
		long total = facets.get("category").stream().mapToLong(Facet::count).sum();
		return new SearchResult(total, hits, facets);
	}
}
