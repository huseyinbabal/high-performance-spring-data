package com.ticketrush.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.ticketrush.hold.HoldService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

	@GetMapping("/events/{id}")
	EventPage page(@PathVariable long id) {
		EventInfo info = events.info(id);
		return new EventPage(info, holds.available(info.tickets().stream().map(EventInfo.Ticket::id).toList()));
	}

	@PutMapping("/events/{id}/tickets/{ticketTypeId}/price")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changePrice(@PathVariable long id, @PathVariable long ticketTypeId, @RequestBody PriceChange change) {
		events.changePrice(id, ticketTypeId, change.price());
	}

	@PostMapping("/admin/events/resync")
	Map<String, Integer> resync(@RequestParam(defaultValue = "2") long from,
			@RequestParam(defaultValue = "5000") int count) {
		return Map.of("queued", events.resync(from, count));
	}

	record EventPage(EventInfo event, Map<Long, Integer> available) {
	}

	record PriceChange(BigDecimal price) {
	}
}
