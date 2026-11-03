package com.ticketrush.messaging;

import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import com.ticketrush.catalog.EventRepository;
import com.ticketrush.messaging.Messages.EventChanged;
import com.ticketrush.search.EventDocument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;

/** Read model #1: keeps the Elasticsearch index in step with PostgreSQL. */
@Component
public class SearchIndexer {

	private static final Logger log = LoggerFactory.getLogger(SearchIndexer.class);

	private final EventRepository events;

	private final ElasticsearchOperations operations;

	private final JsonMapper json;

	public SearchIndexer(EventRepository events, ElasticsearchOperations operations, JsonMapper json) {
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
	@KafkaListener(topics = Topics.EVENTS, groupId = "search-indexer", batch = "true", concurrency = "3")
	public void onEvents(List<ConsumerRecord<String, String>> records) {
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < records.size(); i++) {
			try {
				ids.add(json.readValue(records.get(i).value(), EventChanged.class).eventId());
			}
			catch (JacksonException e) {
				// Name the bad record: the records before it are committed, only IT goes to the DLT,
				// and the rest of the batch is redelivered. A plain exception would fail all 500.
				throw new BatchListenerFailedException("unreadable message", e, i);
			}
		}
		ids = ids.stream().distinct().toList();
		List<EventDocument> documents = events.findDocuments(ids);
		operations.save(documents);
		log.info("search-indexer: {} messages → {} documents in one bulk request", records.size(), documents.size());
	}
}
