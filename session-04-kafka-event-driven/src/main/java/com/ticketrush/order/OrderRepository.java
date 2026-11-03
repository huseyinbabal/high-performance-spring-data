package com.ticketrush.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ticketrush.trending.TrendingEvent;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

	// keyset pagination: "20 rows older than the last one I saw"
	@Query("""
			select new com.ticketrush.order.OrderRow(
			    o.id, c.email, e.name, t.name, o.quantity, o.total, o.status, o.createdAt)
			from Order o join o.customer c join o.ticketType t join t.event e
			where o.id < :after
			order by o.id desc
			""")
	List<OrderRow> feedAfter(long after, Limit limit);

	@Query("""
			select new com.ticketrush.order.OrderRow(
			    o.id, c.email, e.name, t.name, o.quantity, o.total, o.status, o.createdAt)
			from Order o join o.customer c join o.ticketType t join t.event e
			where c.id = :customerId
			order by o.createdAt desc
			""")
	List<OrderRow> findByCustomer(long customerId, Limit limit);

	// Session 2: the aggregate behind the home page's "trending" block
	@Query("""
			select new com.ticketrush.trending.TrendingEvent(e.id, e.name, e.artist, sum(o.quantity))
			from Order o join o.ticketType t join t.event e
			where o.createdAt > :since and o.status = 'PAID'
			group by e.id, e.name, e.artist
			order by sum(o.quantity) desc
			""")
	List<TrendingEvent> topSelling(Instant since, Limit limit);

	// Session 4: a guarded state change. Returns 0 if the order is not in the expected state —
	// which is how a consumer notices "I have already processed this message".
	@Modifying
	@Query("update Order o set o.status = :to where o.id = :id and o.status = :from")
	int transition(long id, String from, String to);

	@Query("select o.status from Order o where o.id = :id")
	Optional<String> findStatus(long id);

	@Modifying
	@Query("update Order o set o.status = :status where o.id = :id")
	int updateStatus(long id, String status);
}
