package com.ticketrush.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.ticketrush.hold.HoldService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController {

	private final EventService events;

	private final HoldService holds;

	public EventController(EventService events, HoldService holds) {
		this.events = events;
		this.holds = holds;
	}

	@GetMapping("/events")
	List<EventCard> upcoming(@RequestParam String category, @RequestParam(defaultValue = "50") int size) {
		return events.upcoming(category, size);
	}

	/** v1 — PostgreSQL on every request. */
	@GetMapping("/v1/events/{id}")
	EventDetail detail(@PathVariable long id) {
		return events.detail(id);
	}

	/** v2 — static part from the cache, live availability from Redis counters. Zero SQL on the hot path. */
	@GetMapping("/v2/events/{id}")
	EventPage page(@PathVariable long id) {
		EventInfo info = events.info(id);
		return new EventPage(info, holds.available(info.tickets().stream().map(EventInfo.Ticket::id).toList()));
	}

	@PutMapping("/events/{id}/tickets/{ticketTypeId}/price")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changePrice(@PathVariable long id, @PathVariable long ticketTypeId, @RequestBody PriceChange change) {
		events.changePrice(id, ticketTypeId, change.price());
	}

	record EventPage(EventInfo event, Map<Long, Integer> available) {
	}

	record PriceChange(BigDecimal price) {
	}
}
