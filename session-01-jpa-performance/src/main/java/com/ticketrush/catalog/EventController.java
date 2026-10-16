package com.ticketrush.catalog;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Every read exists twice: /v1 is what we usually write first, /v2 is what survives load. */
@RestController
public class EventController {

	private final EventService events;

	public EventController(EventService events) {
		this.events = events;
	}

	@GetMapping("/v1/events")
	List<EventCard> upcomingNaive(@RequestParam String category, @RequestParam(defaultValue = "50") int size) {
		return events.upcomingNaive(category, size);
	}

	@GetMapping("/v2/events")
	List<EventCard> upcoming(@RequestParam String category, @RequestParam(defaultValue = "50") int size) {
		return events.upcoming(category, size);
	}

	@GetMapping("/v1/events/{id}")
	EventDetail detailNaive(@PathVariable long id) {
		return events.detailNaive(id);
	}

	@GetMapping("/v2/events/{id}")
	EventDetail detail(@PathVariable long id) {
		return events.detail(id);
	}
}
