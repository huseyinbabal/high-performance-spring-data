package com.ticketrush.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;

/** One row of the "upcoming events" list — exactly the columns the screen shows, nothing else. */
public record EventCard(Long id, String name, String artist, String venue, String city, Instant startsAt,
		BigDecimal fromPrice, Long available) {

	/** v1 mapping: walks LAZY associations, so every call costs extra SELECTs. */
	static EventCard from(Event e) {
		var tickets = e.getTicketTypes();
		return new EventCard(e.getId(), e.getName(), e.getArtist(), e.getVenue().getName(), e.getVenue().getCity(),
				e.getStartsAt(),
				tickets.stream().map(TicketType::getPrice).min(Comparator.naturalOrder()).orElse(null),
				tickets.stream().mapToLong(TicketType::available).sum());
	}
}
