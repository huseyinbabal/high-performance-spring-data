package com.ticketrush.search;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

import com.ticketrush.catalog.EventRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Fills the search index from PostgreSQL, and keeps it up to date when an event changes. */
@Service
public class IndexingService {

	private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

	private static final int BULK_SIZE = 2000;

	private final EventRepository events;

	private final EventSearchRepository documents;

	private final ElasticsearchOperations operations;

	private final ElasticsearchClient client;

	public IndexingService(EventRepository events, EventSearchRepository documents,
			ElasticsearchOperations operations, ElasticsearchClient client) {
		this.events = events;
		this.documents = documents;
		this.operations = operations;
		this.client = client;
	}

	/** v1 — repository.save() per document: one HTTP request AND one index refresh for every event. */
	public IndexingResult indexOneByOne(int limit) {
		long start = System.nanoTime();
		List<EventDocument> page = events.findDocumentsAfter(0, Limit.of(limit));
		page.forEach(documents::save);
		return IndexingResult.of("v1 save() per document", page.size(), start);
	}

	/**
	 * v2 — rebuild the whole index the way Elasticsearch wants it:
	 * keyset pages out of PostgreSQL → _bulk requests of 2,000 → refresh switched off until the end.
	 */
	public IndexingResult reindex() {
		long start = System.nanoTime();
		IndexOperations index = operations.indexOps(EventDocument.class);
		index.delete();
		index.createWithMapping();
		refreshInterval("-1");                       // no searchable segments while loading

		int total = 0;
		long after = 0;
		List<EventDocument> page;
		while (!(page = events.findDocumentsAfter(after, Limit.of(BULK_SIZE))).isEmpty()) {
			operations.save(page);                   // one _bulk request
			total += page.size();
			after = page.getLast().id();
		}

		refreshInterval("1s");
		index.refresh();
		return IndexingResult.of("v2 bulk, refresh off", total, start);
	}

	/**
	 * Keep the index in sync: after the database commit, re-index that one event.
	 * This is a DUAL WRITE — two systems, no shared transaction. If Elasticsearch is unreachable right now,
	 * PostgreSQL has the new price and the index keeps the old one, with nothing left to retry. (Session 4.)
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onEventChanged(EventChanged change) {
		try {
			events.findDocument(change.eventId()).ifPresent(operations::save);
		}
		catch (RuntimeException e) {
			log.error("event {} changed in PostgreSQL but could NOT be re-indexed: {}", change.eventId(),
					e.getMessage());
		}
	}

	private void refreshInterval(String value) {
		try {
			// Spring Data covers the common 90%; for the rest, the Elasticsearch client bean is right there
			client.indices()
				.putSettings(p -> p.index(EventDocument.INDEX).settings(s -> s.refreshInterval(t -> t.time(value))));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public record IndexingResult(String mode, int documents, long elapsedMs, long documentsPerSecond) {

		static IndexingResult of(String mode, int documents, long startNanos) {
			long ms = Math.max(1, (System.nanoTime() - startNanos) / 1_000_000);
			return new IndexingResult(mode, documents, ms, documents * 1000L / ms);
		}
	}
}
