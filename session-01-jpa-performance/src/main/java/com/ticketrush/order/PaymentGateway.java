package com.ticketrush.order;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Stand-in for a remote payment provider: correct, but never fast. */
@Component
public class PaymentGateway {

	private final long latencyMs;

	public PaymentGateway(@Value("${ticketrush.payment.latency-ms:150}") long latencyMs) {
		this.latencyMs = latencyMs;
	}

	public void charge(long customerId, BigDecimal amount) {
		try {
			Thread.sleep(latencyMs);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("payment interrupted", e);
		}
	}
}
