package com.ticketrush.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ticketrush.search.EventDocument;
import com.ticketrush.search.SearchResult.Facet;

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

	// ---------------------------------------------------------------- Session 3: feeding the search index

	// keyset page of ready-made search documents: the JOIN and the min(price) happen here, once, at index time
	@Query("""
			select new com.ticketrush.search.EventDocument(
			    e.id, e.name, e.artist, e.category, v.city, v.name, e.description, e.startsAt,
			    (select min(t.price) from TicketType t where t.event = e), e.status)
			from Event e join e.venue v
			where e.id > :after
			order by e.id
			""")
	List<EventDocument> findDocumentsAfter(long after, Limit limit);

	@Query("""
			select new com.ticketrush.search.EventDocument(
			    e.id, e.name, e.artist, e.category, v.city, v.name, e.description, e.startsAt,
			    (select min(t.price) from TicketType t where t.event = e), e.status)
			from Event e join e.venue v
			where e.id = :id
			""")
	Optional<EventDocument> findDocument(long id);

	// ---------------------------------------------------------------- Session 3: v1 search, the SQL way

	String LIKE_ANY_COLUMN = """
			(lower(e.name) like :pattern or lower(e.artist) like :pattern or lower(e.description) like :pattern
			 or lower(v.name) like :pattern or lower(v.city) like :pattern or e.category like :pattern)
			and e.startsAt > :now
			""";

	@Query("""
			select new com.ticketrush.catalog.EventCard(
			    e.id, e.name, e.artist, v.name, v.city, e.startsAt,
			    (select min(t.price) from TicketType t where t.event = e),
			    (select sum(t.capacity - t.sold) from TicketType t where t.event = e))
			from Event e join e.venue v
			where """ + LIKE_ANY_COLUMN + " order by e.startsAt")
	List<EventCard> searchLike(String pattern, Instant now, Limit limit);

	@Query("select new com.ticketrush.search.SearchResult$Facet(v.city, count(e)) from Event e join e.venue v where "
			+ LIKE_ANY_COLUMN + " group by v.city order by count(e) desc")
	List<Facet> cityFacetLike(String pattern, Instant now);

	@Query("select new com.ticketrush.search.SearchResult$Facet(e.category, count(e)) from Event e join e.venue v where "
			+ LIKE_ANY_COLUMN + " group by e.category order by count(e) desc")
	List<Facet> categoryFacetLike(String pattern, Instant now);
}
