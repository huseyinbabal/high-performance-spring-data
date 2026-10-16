package com.ticketrush.catalog;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventService {

	private final EventRepository events;

	public EventService(EventRepository events) {
		this.events = events;
	}

	/** v1 — 1 query for the page, then venue + ticket types for every row. */
	@Transactional(readOnly = true)
	public List<EventCard> upcomingNaive(String category, int size) {
		return events.findByCategoryAndStartsAtAfterOrderByStartsAt(category, Instant.now(), Limit.of(size))
			.stream()
			.map(EventCard::from)
			.toList();
	}

	/** v2 — the query returns the cards. */
	@Transactional(readOnly = true)
	public List<EventCard> upcoming(String category, int size) {
		return events.findCards(category, Instant.now(), Limit.of(size));
	}

	/** v1 — event, then venue, then ticket types: three round trips. */
	@Transactional(readOnly = true)
	public EventDetail detailNaive(long id) {
		return events.findById(id).map(EventDetail::from).orElseThrow(EventService::notFound);
	}

	/** v2 — one round trip via @EntityGraph. */
	@Transactional(readOnly = true)
	public EventDetail detail(long id) {
		return events.findWithDetailsById(id).map(EventDetail::from).orElseThrow(EventService::notFound);
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "event not found");
	}
}
