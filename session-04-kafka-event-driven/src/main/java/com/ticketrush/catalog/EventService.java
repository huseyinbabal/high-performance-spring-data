package com.ticketrush.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ticketrush.messaging.Messages;
import com.ticketrush.messaging.Topics;
import com.ticketrush.outbox.Outbox;
import com.ticketrush.search.EventChanged;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
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

	private final ApplicationEventPublisher publisher;

	private final Outbox outbox;

	public EventService(EventRepository events, TicketTypeRepository ticketTypes,
			ApplicationEventPublisher publisher, Outbox outbox) {
		this.events = events;
		this.ticketTypes = ticketTypes;
		this.publisher = publisher;
		this.outbox = outbox;
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

	/**
	 * v1 — the dual write from Session 3: one database commit, then two more systems updated "afterwards"
	 * (cache eviction + search re-index). Any of them can fail on its own, and nothing retries.
	 */
	@CacheEvict(cacheNames = EVENT_CACHE, key = "#eventId")
	@Transactional
	public void changePriceDualWrite(long eventId, long ticketTypeId, BigDecimal price) {
		if (ticketTypes.changePrice(eventId, ticketTypeId, price) == 0) {
			throw notFound();
		}
		publisher.publishEvent(new EventChanged(eventId));
	}

	/**
	 * v2 — ONE write. The price change and the fact "event 1 changed" commit together or not at all.
	 * This method knows nothing about Redis or Elasticsearch; they are consumers of that fact.
	 */
	@Transactional
	public void changePrice(long eventId, long ticketTypeId, BigDecimal price) {
		if (ticketTypes.changePrice(eventId, ticketTypeId, price) == 0) {
			throw notFound();
		}
		outbox.add(Topics.EVENTS, eventId, new Messages.EventChanged(eventId));
	}

	/**
	 * "Re-sync these events everywhere": N outbox rows in one transaction (JDBC-batched, Session 1),
	 * relayed to Kafka in batches, indexed in batches. Nothing in the pipeline works one row at a time.
	 */
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
