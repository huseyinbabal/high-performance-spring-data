package com.ticketrush.search;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

import com.ticketrush.catalog.EventRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Limit;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * CQRS projection: builds the read model (Elasticsearch) from the write model (PostgreSQL) — in full with
 * reindex(), and one event at a time when the command side announces a change.
 * The application never uses a real index name: {@code events} is an ALIAS pointing at {@code events-<millis>}.
 */
@Service
public class EventProjector {

	private static final Logger log = LoggerFactory.getLogger(EventProjector.class);

	private static final String ALIAS = EventDocument.INDEX;

	private final EventRepository events;

	private final EventSearchRepository documents;

	private final ElasticsearchOperations operations;

	private final ElasticsearchClient client;

	public EventProjector(EventRepository events, EventSearchRepository documents,
			ElasticsearchOperations operations, ElasticsearchClient client) {
		this.events = events;
		this.documents = documents;
		this.operations = operations;
		this.client = client;
	}

	/** On startup: make sure the `events` alias exists, so search answers (empty) before the first reindex. */
	@EventListener(ApplicationReadyEvent.class)
	public void ensureIndex() throws IOException {
		if (!client.indices().exists(e -> e.index(ALIAS)).value()) {
			String index = createIndex(1, "1s");
			client.indices().updateAliases(u -> u.actions(a -> a.add(ad -> ad.index(index).alias(ALIAS))));
		}
	}

	/** v1 — repository.save() per document: one HTTP request AND one index refresh for every event. */
	public IndexingResult indexOneByOne(int limit) {
		long start = System.nanoTime();
		List<EventDocument> page = events.findDocumentsAfter(0, Limit.of(limit));
		page.forEach(documents::save);
		return IndexingResult.of("v1 save() per document", page.size(), start);
	}

	/**
	 * v2 — rebuild the whole index the way Elasticsearch wants it: a NEW index, keyset pages out of PostgreSQL,
	 * one _bulk request per page, refresh switched off until the end — then one atomic alias swap.
	 * @param swap {@code true}: the old index serves searches until the swap · {@code false}: drop it first
	 */
	public IndexingResult reindex(int shards, int bulkSize, String refreshWhileLoading, boolean swap)
			throws IOException {
		long start = System.nanoTime();
		Set<String> old = indexesBehindAlias();
		if (!swap) {
			deleteIndexes(old);                     // from here until the end, every search fails
		}

		String index = createIndex(shards, refreshWhileLoading);
		IndexCoordinates target = IndexCoordinates.of(index);
		int total = 0;
		long after = 0;
		List<EventDocument> page;
		while (!(page = events.findDocumentsAfter(after, Limit.of(bulkSize))).isEmpty()) {
			operations.save(page, target);          // one _bulk request
			total += page.size();
			after = page.getLast().id();
		}
		client.indices().putSettings(p -> p.index(index).settings(s -> s.refreshInterval(t -> t.time("1s"))));
		client.indices().refresh(r -> r.index(index));

		// one atomic call: searches see the old index or the new one, never neither
		client.indices().updateAliases(u -> {
			u.actions(a -> a.add(ad -> ad.index(index).alias(ALIAS)));
			if (swap) {
				old.forEach(o -> u.actions(a -> a.removeIndex(r -> r.index(o))));
			}
			return u;
		});
		String mode = "v2 bulk " + bulkSize + ", refresh " + refreshWhileLoading + ", " + shards + " shard(s), "
				+ (swap ? "alias swap" : "drop first");
		return IndexingResult.of(mode, total, start);
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

	/** A new physical index from resources/es — the shard count is the only thing that can only be set here. */
	private String createIndex(int shards, String refresh) throws IOException {
		String index = ALIAS + "-" + System.currentTimeMillis();
		try (InputStream settings = new ClassPathResource("es/settings.json").getInputStream();
				InputStream mappings = new ClassPathResource("es/mappings.json").getInputStream()) {
			client.indices()
				.create(c -> c.index(index)
					.settings(s -> s.withJson(settings)
						.numberOfShards(String.valueOf(shards))
						.refreshInterval(t -> t.time(refresh)))
					.mappings(m -> m.withJson(mappings)));
		}
		return index;
	}

	/** The real indexes behind `events`. A plain index called `events` (older runs of this demo) counts too. */
	private Set<String> indexesBehindAlias() throws IOException {
		if (!client.indices().exists(e -> e.index(ALIAS)).value()) {
			return Set.of();
		}
		return client.indices().get(g -> g.index(ALIAS)).indices().keySet();
	}

	private void deleteIndexes(Set<String> indexes) {
		indexes.forEach(i -> {
			try {
				client.indices().delete(d -> d.index(i));
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	public record IndexingResult(String mode, int documents, long elapsedMs, long documentsPerSecond) {

		static IndexingResult of(String mode, int documents, long startNanos) {
			long ms = Math.max(1, (System.nanoTime() - startNanos) / 1_000_000);
			return new IndexingResult(mode, documents, ms, documents * 1000L / ms);
		}
	}
}
