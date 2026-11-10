package com.ticketrush.export;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderExportRow(Long id, Long customerId, Long ticketTypeId, int quantity, BigDecimal total,
		String status, Instant createdAt) {

	String toCsv() {
		return id + "," + customerId + "," + ticketTypeId + "," + quantity + "," + total + "," + status + ","
				+ createdAt + "\n";
	}
}
