package com.ticketrush.customer;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;

@Entity
public class Customer {

	// SEQUENCE + allocationSize 50: Hibernate knows the ids BEFORE the INSERT, so inserts can be batched.
	// With IDENTITY every row needs its own round trip to learn its id — batching is silently disabled.
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_seq")
	@SequenceGenerator(name = "customer_seq", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	private String email;

	private String fullName;

	private String city;

	protected Customer() {
	}

	public Customer(String email, String fullName, String city) {
		this.email = email;
		this.fullName = fullName;
		this.city = city;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getFullName() {
		return fullName;
	}

	public String getCity() {
		return city;
	}
}
