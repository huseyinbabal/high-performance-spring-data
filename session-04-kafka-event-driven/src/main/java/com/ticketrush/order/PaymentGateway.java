package com.ticketrush.order;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stand-in for a remote payment provider: correct, but never fast. */
@Component
public class PaymentGateway {

	private volatile long latencyMs;

	public PaymentGateway(@Value("${ticketrush.payment.latency-ms:150}") long latencyMs) {
		this.latencyMs = latencyMs;
	}

	/**
	 * @param idempotencyKey the order id. A real provider remembers it and refuses to charge twice for the
	 * same key — which is what makes retrying (and at-least-once delivery) safe for payments.
	 */
	public void charge(String idempotencyKey, long customerId, BigDecimal amount) {
		try {
			Thread.sleep(latencyMs);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("payment interrupted", e);
		}
	}

	/** Demo switch: make the provider slow, as it will be on the day of your biggest sale. */
	public void setLatencyMs(long latencyMs) {
		this.latencyMs = latencyMs;
	}
}
