package com.ticketrush.search;

import java.math.BigDecimal;

/**
 * Query: a question to the read model (Elasticsearch). It changes nothing and can be answered from a copy.
 * @param from lower bound of startsAt — "now" (/v2) or "now/m" (/v3, cacheable)
 */
public record SearchEventsQuery(String text, String city, String category, BigDecimal maxPrice, int page,
		String from) {
}
