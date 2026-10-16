package com.ticketrush.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<Event, Long> {

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

	@EntityGraph(attributePaths = { "venue", "ticketTypes" })
	Optional<Event> findWithDetailsById(Long id);
}
