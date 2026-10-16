package com.ticketrush.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<Event, Long> {

	// v1 — returns entities; whoever maps them to a DTO pays for venue + ticketTypes row by row (N+1)
	List<Event> findByCategoryAndStartsAtAfterOrderByStartsAt(String category, Instant now, Limit limit);

	// v2 — ONE statement: constructor projection, a join for the venue,
	//      scalar subqueries for the ticket aggregates (evaluated only for the rows that survive LIMIT)
	@Query("""
			select new com.ticketrush.catalog.EventCard(
			    e.id, e.name, e.artist, v.name, v.city, e.startsAt,
			    (select min(t.price) from TicketType t where t.event = e),
			    (select sum(t.capacity - t.sold) from TicketType t where t.event = e))
			from Event e join e.venue v
			where e.category = :category and e.startsAt > :now
			order by e.startsAt
			""")
	List<EventCard> findCards(String category, Instant now, Limit limit);

	// v2 detail — the entity graph turns three SELECTs into one JOIN, only for this query
	@EntityGraph(attributePaths = { "venue", "ticketTypes" })
	Optional<Event> findWithDetailsById(Long id);
}
