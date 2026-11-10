package com.ticketrush.search;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** What a search page needs: a page of hits, the total, and facet counts for the filter sidebar. */
public record SearchResult(long total, List<Hit> hits, Map<String, List<Facet>> facets) {

	public record Hit(Long id, String name, String artist, String city, String venue, Instant startsAt,
			BigDecimal fromPrice, Float score) {
	}

	public record Facet(String key, long count) {
	}
}
