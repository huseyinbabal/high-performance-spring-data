package com.ticketrush.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record EventDetail(Long id, String name, String artist, String category, String description, String venue,
		String city, Instant startsAt, String status, List<TicketOption> tickets) {

	public record TicketOption(Long id, String name, BigDecimal price, int available) {
	}

	static EventDetail from(Event e) {
		return new EventDetail(e.getId(), e.getName(), e.getArtist(), e.getCategory(), e.getDescription(),
				e.getVenue().getName(), e.getVenue().getCity(), e.getStartsAt(), e.getStatus(),
				e.getTicketTypes().stream()
					.map(t -> new TicketOption(t.getId(), t.getName(), t.getPrice(), t.available()))
					.toList());
	}
}
