package com.ticketrush.messaging;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import tools.jackson.databind.json.JsonMapper;

import com.ticketrush.catalog.EventRepository;
import com.ticketrush.messaging.Messages.EventChanged;
import com.ticketrush.search.EventDocument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Read model #1: keeps the Elasticsearch index in step with PostgreSQL. */
@Component
public class SearchIndexer {

	private static final Logger log = LoggerFactory.getLogger(SearchIndexer.class);

	private final EventRepository events;

	private final ElasticsearchOperations operations;

	private final JsonMapper json;

	private final TransactionTemplate tx;

	public SearchIndexer(EventRepository events, ElasticsearchOperations operations, JsonMapper json,
			TransactionTemplate tx) {
		this.tx = tx;
		this.events = events;
		this.operations = operations;
		this.json = json;
	}

	/**
	 * Batch listener: up to 500 records per poll become ONE query and ONE _bulk request.
	 * Idempotent by construction — it re-reads the current row and overwrites the document by id,
	 * so a duplicate or out-of-order message can only write the truth again.
	 * If Elasticsearch is down this throws, the offset is not committed, and the batch is retried.
	 */
	@KafkaListener(topics = Topics.EVENTS, groupId = "search-indexer", batch = "true",
			autoStartup = "${ticketrush.workers.enabled:true}")
	public void onEvents(List<ConsumerRecord<String, String>> records) {
		List<Long> ids = records.stream()
			.map(r -> json.readValue(r.value(), EventChanged.class).eventId())
			.distinct()
			.toList();
		// The message says "this changed". Read the new state from the PRIMARY (read-write transaction):
		// a replica that has not replayed the change yet would make us index the OLD state — permanently.
		List<EventDocument> documents = tx.execute(status -> events.findDocuments(ids));
		operations.save(documents);
		log.info("search-indexer: {} messages → {} documents in one bulk request", records.size(), documents.size());
	}
}
