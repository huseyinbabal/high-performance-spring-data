package com.ticketrush.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
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

	private final TicketTypeRepository ticketTypes;

	public EventService(EventRepository events, TicketTypeRepository ticketTypes) {
		this.events = events;
		this.ticketTypes = ticketTypes;
	}

	@Transactional(readOnly = true)
	public List<EventCard> upcoming(String category, int size) {
		return events.findCards(category, Instant.now(), Limit.of(size));
	}

	/** v1 — the Session 1 result: one indexed query, every time. */
	@Transactional(readOnly = true)
	public EventDetail detail(long id) {
		return events.findWithDetailsById(id).map(EventDetail::from).orElseThrow(EventService::notFound);
	}

	/**
	 * v2 — cache-aside. On a hit this method body does not run at all: no transaction, no connection,
	 * no SQL. On a miss it runs once and Spring stores the result in Redis under event::<id>.
	 */
	@Cacheable(EVENT_CACHE)
	@Transactional(readOnly = true)
	public EventInfo info(long id) {
		return events.findWithDetailsById(id).map(EventInfo::from).orElseThrow(EventService::notFound);
	}

	/**
	 * The write path owns invalidation: change the row, then delete the cache entry.
	 * Evict (not update): the next reader rebuilds from the database, so the cache can never be newer than it.
	 */
	@CacheEvict(cacheNames = EVENT_CACHE, key = "#eventId")
	@Transactional
	public void changePrice(long eventId, long ticketTypeId, BigDecimal price) {
		if (ticketTypes.changePrice(eventId, ticketTypeId, price) == 0) {
			throw notFound();
		}
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "event not found");
	}
}
