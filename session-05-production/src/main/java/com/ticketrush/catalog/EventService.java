package com.ticketrush.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ticketrush.messaging.Messages;
import com.ticketrush.messaging.Topics;
import com.ticketrush.outbox.Outbox;

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

	private final Outbox outbox;

	public EventService(EventRepository events, TicketTypeRepository ticketTypes, Outbox outbox) {
		this.events = events;
		this.ticketTypes = ticketTypes;
		this.outbox = outbox;
	}

	/** readOnly = true → Session 5 routes this to the replica. A listing may be a few ms behind. */
	@Transactional(readOnly = true)
	public List<EventCard> upcoming(String category, int size) {
		return events.findCards(category, Instant.now(), Limit.of(size));
	}

	/**
	 * Cache-aside (Session 2). NOT readOnly, on purpose: what we load here is stored in Redis for 10 minutes.
	 * Read it from a replica that is 5 ms behind a price change, and the cache serves the old price for 10 minutes.
	 * Whatever feeds another store reads from the primary.
	 */
	@Cacheable(EVENT_CACHE)
	@Transactional
	public EventInfo info(long id) {
		return events.findWithDetailsById(id).map(EventInfo::from).orElseThrow(EventService::notFound);
	}

	/** Outbox (Session 4): the change and its event commit together; Redis and Elasticsearch follow via Kafka. */
	@Transactional
	public void changePrice(long eventId, long ticketTypeId, BigDecimal price) {
		if (ticketTypes.changePrice(eventId, ticketTypeId, price) == 0) {
			throw notFound();
		}
		outbox.add(Topics.EVENTS, eventId, new Messages.EventChanged(eventId));
	}

	@Transactional
	public int resync(long fromId, int count) {
		for (long id = fromId; id < fromId + count; id++) {
			outbox.add(Topics.EVENTS, id, new Messages.EventChanged(id));
		}
		return count;
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "event not found");
	}
}
