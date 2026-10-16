package com.ticketrush.catalog;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of the "upcoming events" list — exactly the columns the screen shows, nothing else. */
public record EventCard(Long id, String name, String artist, String venue, String city, Instant startsAt,
		BigDecimal fromPrice, Long available) {
}
