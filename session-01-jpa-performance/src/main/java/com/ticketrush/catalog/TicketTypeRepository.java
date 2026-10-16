package com.ticketrush.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

	// The availability check and the increment are ONE atomic statement: the row lock makes
	// concurrent buyers queue, and the WHERE clause makes overselling impossible. Returns 0 when sold out.
	@Modifying
	@Query("update TicketType t set t.sold = t.sold + :quantity where t.id = :id and t.sold + :quantity <= t.capacity")
	int reserve(long id, int quantity);

	@Modifying
	@Query("update TicketType t set t.sold = t.sold - :quantity where t.id = :id")
	int release(long id, int quantity);
}
