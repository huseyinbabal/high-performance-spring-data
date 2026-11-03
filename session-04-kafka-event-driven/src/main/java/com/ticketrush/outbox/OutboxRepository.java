package com.ticketrush.outbox;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

	// FOR UPDATE SKIP LOCKED: lock the rows I take, and skip rows another relay instance already holds.
	// Two instances never publish the same batch, and neither ever waits for the other.
	@Query(value = """
			select * from outbox
			where published_at is null
			order by id
			limit :limit
			for update skip locked
			""", nativeQuery = true)
	List<OutboxEvent> lockNextBatch(int limit);

	@Modifying
	@Query("update OutboxEvent o set o.publishedAt = :now where o.id in :ids")
	int markPublished(List<Long> ids, Instant now);

	@Query("select count(o) from OutboxEvent o where o.publishedAt is null")
	long countUnpublished();
}
