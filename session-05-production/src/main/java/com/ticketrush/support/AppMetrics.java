package com.ticketrush.support;

import com.ticketrush.outbox.OutboxRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.context.annotation.Configuration;

/** The numbers that are specific to OUR system — no library can know them for us. */
@Configuration
public class AppMetrics {

	public AppMetrics(MeterRegistry registry, OutboxRepository outbox) {
		// rows committed but not yet in Kafka: 0 when healthy, growing when the relay or the broker is in trouble
		Gauge.builder("ticketrush.outbox.unpublished", outbox, OutboxRepository::countUnpublished)
			.description("Outbox rows waiting to be published")
			.register(registry);
	}
}
