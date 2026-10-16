package com.ticketrush.order;

import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

	// v2 feed — keyset pagination: "give me 20 rows older than the last one I saw".
	// The primary-key index jumps straight to :after; page 40,000 costs the same as page 1.
	@Query("""
			select new com.ticketrush.order.OrderRow(
			    o.id, c.email, e.name, t.name, o.quantity, o.total, o.status, o.createdAt)
			from Order o join o.customer c join o.ticketType t join t.event e
			where o.id < :after
			order by o.id desc
			""")
	List<OrderRow> feedAfter(long after, Limit limit);

	// "My orders" — a sequential scan over 1M rows until orders(customer_id, created_at) exists
	@Query("""
			select new com.ticketrush.order.OrderRow(
			    o.id, c.email, e.name, t.name, o.quantity, o.total, o.status, o.createdAt)
			from Order o join o.customer c join o.ticketType t join t.event e
			where c.id = :customerId
			order by o.createdAt desc
			""")
	List<OrderRow> findByCustomer(long customerId, Limit limit);

	@Modifying
	@Query("update Order o set o.status = :status where o.id = :id")
	int updateStatus(long id, String status);
}
