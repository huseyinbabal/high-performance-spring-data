package com.ticketrush.catalog;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

	// check + increment in ONE atomic statement; returns 0 when sold out
	@Modifying
	@Query("update TicketType t set t.sold = t.sold + :quantity where t.id = :id and t.sold + :quantity <= t.capacity")
	int reserve(long id, int quantity);

	@Modifying
	@Query("update TicketType t set t.sold = t.sold - :quantity where t.id = :id")
	int release(long id, int quantity);

	@Modifying
	@Query("update TicketType t set t.price = :price where t.id = :id and t.event.id = :eventId")
	int changePrice(long eventId, long id, BigDecimal price);
}
