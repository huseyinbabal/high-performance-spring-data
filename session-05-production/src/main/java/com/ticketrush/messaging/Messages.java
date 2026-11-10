package com.ticketrush.messaging;

import java.math.BigDecimal;

/** The payloads on our topics. A message is a public contract: add fields, never rename or remove. */
public final class Messages {

	private Messages() {
	}

	/** Carries only the id: consumers read the current state themselves, so order and duplicates do not matter. */
	public record EventChanged(long eventId) {
	}

	public record OrderPlaced(long orderId, long customerId, long eventId, long ticketTypeId, int quantity,
			BigDecimal total) {
	}
}
