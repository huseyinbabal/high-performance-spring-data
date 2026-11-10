package com.ticketrush.search;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

import com.ticketrush.catalog.EventRepository;

import org.springframework.data.domain.Limit;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Service;

/** Rebuilds the search index from PostgreSQL (Session 3). Day-to-day changes arrive through Kafka (Session 4). */
@Service
public class IndexingService {

	private static final int BULK_SIZE = 2000;

	private final EventRepository events;

	private final ElasticsearchOperations operations;

	private final ElasticsearchClient client;

	public IndexingService(EventRepository events, ElasticsearchOperations operations, ElasticsearchClient client) {
		this.events = events;
		this.operations = operations;
		this.client = client;
	}

	/** Keyset pages out of PostgreSQL (served by the replica) → _bulk requests of 2,000 → refresh off until the end. */
	public IndexingResult reindex() {
		long start = System.nanoTime();
		IndexOperations index = operations.indexOps(EventDocument.class);
		index.delete();
		index.createWithMapping();
		refreshInterval("-1");

		int total = 0;
		long after = 0;
		List<EventDocument> page;
		while (!(page = events.findDocumentsAfter(after, Limit.of(BULK_SIZE))).isEmpty()) {
			operations.save(page);
			total += page.size();
			after = page.getLast().id();
		}

		refreshInterval("1s");
		index.refresh();
		return IndexingResult.of(total, start);
	}

	private void refreshInterval(String value) {
		try {
			client.indices()
				.putSettings(p -> p.index(EventDocument.INDEX).settings(s -> s.refreshInterval(t -> t.time(value))));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public record IndexingResult(int documents, long elapsedMs, long documentsPerSecond) {

		static IndexingResult of(int documents, long startNanos) {
			long ms = Math.max(1, (System.nanoTime() - startNanos) / 1_000_000);
			return new IndexingResult(documents, ms, documents * 1000L / ms);
		}
	}
}
