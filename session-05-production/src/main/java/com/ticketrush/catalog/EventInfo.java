package com.ticketrush.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The part of an event page that rarely changes — safe to cache for minutes.
 * Availability is deliberately NOT here: it changes with every sale, so it never goes into a cached blob.
 */
public record EventInfo(Long id, String name, String artist, String category, String description, String venue,
		String city, Instant startsAt, String status, List<Ticket> tickets) {

	public record Ticket(Long id, String name, BigDecimal price, int capacity) {
	}

	static EventInfo from(Event e) {
		return new EventInfo(e.getId(), e.getName(), e.getArtist(), e.getCategory(), e.getDescription(),
				e.getVenue().getName(), e.getVenue().getCity(), e.getStartsAt(), e.getStatus(),
				e.getTicketTypes().stream()
					.map(t -> new Ticket(t.getId(), t.getName(), t.getPrice(), t.getCapacity()))
					.toList());
	}
}
