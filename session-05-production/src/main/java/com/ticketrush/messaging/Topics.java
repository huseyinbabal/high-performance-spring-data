package com.ticketrush.messaging;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;

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

	/**
	 * What happens when a listener throws: retry with a growing pause (0.5 s → 1 s → 2 s … max 10 s)
	 * for up to 5 minutes — long enough to ride out a dependency restart — then park the record on
	 * <topic>.DLT so one bad message cannot block its partition forever.
	 */
	@Bean
	DefaultErrorHandler kafkaErrorHandler(KafkaOperations<String, String> kafka) {
		var backoff = new ExponentialBackOff(500, 2.0);
		backoff.setMaxInterval(10_000);
		backoff.setMaxElapsedTime(300_000);
		return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafka), backoff);
	}

	static String header(ConsumerRecord<?, ?> record, String name) {
		var header = record.headers().lastHeader(name);
		return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
	}

	static String messageId(ConsumerRecord<?, ?> record) {
		return header(record, OutboxRelay.MESSAGE_ID);
	}
}
