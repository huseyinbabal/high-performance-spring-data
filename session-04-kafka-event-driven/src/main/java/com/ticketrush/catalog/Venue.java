package com.ticketrush.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Venue {

	@Id
	private Long id;

	private String name;

	private String city;

	private int capacity;

	protected Venue() {
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCity() {
		return city;
	}

	public int getCapacity() {
		return capacity;
	}
}
