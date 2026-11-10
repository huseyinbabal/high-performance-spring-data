package com.ticketrush.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moves outbox rows to Kafka. Publish first, mark second: if we crash in between, the rows are sent again
 * on the next run. So delivery is AT LEAST ONCE — and every consumer must be idempotent.
 */
@Component
@ConditionalOnProperty(name = "ticketrush.workers.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

	public static final String MESSAGE_ID = "message-id";

	public static final String MESSAGE_TYPE = "message-type";

	private final OutboxRepository outbox;

	private final KafkaTemplate<String, String> kafka;

	private final TransactionTemplate tx;

	private final int batchSize;

	public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka, TransactionTemplate tx,
			@Value("${ticketrush.outbox.batch-size}") int batchSize) {
		this.outbox = outbox;
		this.kafka = kafka;
		this.tx = tx;
		this.batchSize = batchSize;
	}

	@Scheduled(fixedDelay = 100)
	public void relay() {
		int published;
		do {
			published = tx.execute(status -> publishBatch());
		}
		while (published == batchSize);              // backlog? keep going without waiting for the next tick
	}

	private int publishBatch() {
		List<OutboxEvent> batch = outbox.lockNextBatch(batchSize);
		if (batch.isEmpty()) {
			return 0;
		}
		// send() only queues the record; the producer ships the whole batch in a few requests (linger.ms)
		List<CompletableFuture<?>> sends = batch.stream().<CompletableFuture<?>>map(e -> {
			var record = new ProducerRecord<>(e.getTopic(), e.getMessageKey(), e.getPayload());
			record.headers().add(MESSAGE_ID, String.valueOf(e.getId()).getBytes(StandardCharsets.UTF_8));
			record.headers().add(MESSAGE_TYPE, e.getType().getBytes(StandardCharsets.UTF_8));
			if (e.getTraceparent() != null) {          // the consumer continues the trace of the request
				record.headers().add("traceparent", e.getTraceparent().getBytes(StandardCharsets.UTF_8));
			}
			return kafka.send(record);
		}).toList();

		// every record acknowledged by the broker (acks=all) BEFORE any row is marked as published
		CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).join();
		outbox.markPublished(batch.stream().map(OutboxEvent::getId).toList(), Instant.now());
		return batch.size();
	}
}
