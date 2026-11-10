package com.ticketrush.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.ticketrush.catalog.TicketType;
import com.ticketrush.customer.Customer;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orders_seq")
	@SequenceGenerator(name = "orders_seq", sequenceName = "orders_seq", allocationSize = 50)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private Customer customer;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ticket_type_id")
	private TicketType ticketType;

	private int quantity;

	private BigDecimal total;

	private String status;

	private Instant createdAt;

	protected Order() {
	}

	public Order(Customer customer, TicketType ticketType, int quantity, BigDecimal total, String status) {
		this.customer = customer;
		this.ticketType = ticketType;
		this.quantity = quantity;
		this.total = total;
		this.status = status;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Customer getCustomer() {
		return customer;
	}

	public TicketType getTicketType() {
		return ticketType;
	}

	public int getQuantity() {
		return quantity;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public String getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
