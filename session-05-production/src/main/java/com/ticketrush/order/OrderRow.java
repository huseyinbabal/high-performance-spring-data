package com.ticketrush.order;

import java.math.BigDecimal;
import java.time.Instant;

/** One line of an order list: order + who bought + what they bought. */
public record OrderRow(Long id, String customer, String event, String ticket, int quantity, BigDecimal total,
		String status, Instant createdAt) {

}
