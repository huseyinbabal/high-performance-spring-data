package com.ticketrush.catalog;

import java.time.Instant;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventService {

	public static final String EVENT_CACHE = "event";

	private final EventRepository events;

	public EventService(EventRepository events) {
		this.events = events;
	}

	@Transactional(readOnly = true)
	public List<EventCard> upcoming(String category, int size) {
		return events.findCards(category, Instant.now(), Limit.of(size));
	}

	/** Cache-aside (Session 2): static event info lives in Redis for minutes. */
	@Cacheable(EVENT_CACHE)
	@Transactional(readOnly = true)
	public EventInfo info(long id) {
		return events.findWithDetailsById(id).map(EventInfo::from).orElseThrow(EventService::notFound);
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "event not found");
	}
}
