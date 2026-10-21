package com.ticketrush.catalog.command;

import com.ticketrush.catalog.EventService;
import com.ticketrush.catalog.TicketTypeRepository;
import com.ticketrush.search.EventChanged;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * CQRS write side: commands change the normalized model in PostgreSQL, inside one transaction.
 * It never touches Elasticsearch — it only announces what changed; EventProjector updates the read model.
 */
@Service
public class EventCommandService {

	private final TicketTypeRepository ticketTypes;

	private final ApplicationEventPublisher publisher;

	public EventCommandService(TicketTypeRepository ticketTypes, ApplicationEventPublisher publisher) {
		this.ticketTypes = ticketTypes;
		this.publisher = publisher;
	}

	/**
	 * The write path owns invalidation: change the row, then delete the cache entry (Session 2).
	 * Evict (not update): the next reader rebuilds from the database, so the cache can never be newer than it.
	 */
	@CacheEvict(cacheNames = EventService.EVENT_CACHE, key = "#command.eventId()")
	@Transactional
	public void handle(ChangePriceCommand command) {
		if (ticketTypes.changePrice(command.eventId(), command.ticketTypeId(), command.price()) == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "event not found");
		}
		publisher.publishEvent(new EventChanged(command.eventId()));   // the read model must follow
	}
}
