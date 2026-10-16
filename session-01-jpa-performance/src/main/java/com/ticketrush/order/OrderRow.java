package com.ticketrush.order;

import java.math.BigDecimal;
import java.time.Instant;

/** One line of an order list: order + who bought + what they bought. */
public record OrderRow(Long id, String customer, String event, String ticket, int quantity, BigDecimal total,
		String status, Instant createdAt) {

	/** v1 mapping: customer, ticket type and event are LAZY — three extra SELECTs per row. */
	static OrderRow from(Order o) {
		return new OrderRow(o.getId(), o.getCustomer().getEmail(), o.getTicketType().getEvent().getName(),
				o.getTicketType().getName(), o.getQuantity(), o.getTotal(), o.getStatus(), o.getCreatedAt());
	}
}
