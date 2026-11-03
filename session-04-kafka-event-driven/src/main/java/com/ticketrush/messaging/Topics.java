package com.ticketrush.messaging;

import java.nio.charset.StandardCharsets;

import jakarta.persistence.EntityNotFoundException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import tools.jackson.core.JacksonException;

import com.ticketrush.outbox.OutboxRelay;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@EnableScheduling
public class Topics {

	/** Catalog changes, keyed by event id: all changes of one event stay in order on one partition. */
	public static final String EVENTS = "ticketrush.events";

	/** Order lifecycle, keyed by order id. 12 partitions = up to 12 payment workers in parallel. */
	public static final String ORDERS = "ticketrush.orders";

	@Bean
	NewTopic eventsTopic() {
		return TopicBuilder.name(EVENTS).partitions(3).replicas(1).build();
	}

	@Bean
	NewTopic ordersTopic() {
		return TopicBuilder.name(ORDERS).partitions(12).replicas(1).build();
	}

	// The recoverer writes a failed record to the SAME partition number of <topic>-dlt (Spring Kafka 4 default),
	// so each DLT needs at least as many partitions as its source topic.
	@Bean
	NewTopic eventsDeadLetterTopic() {
		return TopicBuilder.name(EVENTS + "-dlt").partitions(3).replicas(1).build();
	}

	@Bean
	NewTopic ordersDeadLetterTopic() {
		return TopicBuilder.name(ORDERS + "-dlt").partitions(12).replicas(1).build();
	}

	/**
	 * What happens when a listener throws: retry with a growing pause (0.5 s → 1 s → 2 s … max 10 s)
	 * for up to 5 minutes — long enough to ride out a dependency restart — then park the record on
	 * <topic>-dlt (Spring Kafka 4 default) so one bad message cannot block its partition forever.
	 * A payload we cannot even parse will never succeed: it skips the retries and goes to the DLT at once.
	 * Without that line one malformed message stops its partition — and every event behind it — for 5 minutes.
	 */
	@Bean
	DefaultErrorHandler kafkaErrorHandler(KafkaOperations<String, String> kafka) {
		var backoff = new ExponentialBackOff(500, 2.0);
		backoff.setMaxInterval(10_000);
		backoff.setMaxElapsedTime(300_000);
		var handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafka), backoff);
		handler.addNotRetryableExceptions(JacksonException.class);   // poison: retrying cannot fix it
		return handler;
	}

	static String header(ConsumerRecord<?, ?> record, String name) {
		var header = record.headers().lastHeader(name);
		return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
	}

	static String messageId(ConsumerRecord<?, ?> record) {
		return header(record, OutboxRelay.MESSAGE_ID);
	}
}
