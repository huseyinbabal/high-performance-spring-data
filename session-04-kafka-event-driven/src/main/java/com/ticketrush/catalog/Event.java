package com.ticketrush.catalog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

@Entity
public class Event {

	@Id
	private Long id;

	private String name;

	private String artist;

	private String category;

	private String description;

	private String status;

	private Instant startsAt;

	// LAZY everywhere: an association is loaded when a query asks for it, never by accident
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "venue_id")
	private Venue venue;

	@OneToMany(mappedBy = "event")
	private List<TicketType> ticketTypes = new ArrayList<>();

	protected Event() {
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getArtist() {
		return artist;
	}

	public String getCategory() {
		return category;
	}

	public String getDescription() {
		return description;
	}

	public String getStatus() {
		return status;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Venue getVenue() {
		return venue;
	}

	public List<TicketType> getTicketTypes() {
		return ticketTypes;
	}
}
