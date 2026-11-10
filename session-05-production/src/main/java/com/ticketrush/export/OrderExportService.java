package com.ticketrush.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.stream.Stream;

import com.ticketrush.order.Order;
import com.ticketrush.order.OrderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** "Export all orders as CSV" — the back-office button that takes production down. */
@Service
public class OrderExportService {

	static final String HEADER = "id,customer_id,ticket_type_id,quantity,total,status,created_at\n";

	private final OrderRepository orders;

	private final TransactionTemplate readOnlyTx;

	public OrderExportService(OrderRepository orders, PlatformTransactionManager txManager) {
		this.orders = orders;
		this.readOnlyTx = new TransactionTemplate(txManager);
		this.readOnlyTx.setReadOnly(true);           // → runs on the replica
	}

	/**
	 * v1 — findAll(): one million managed entities in a List, plus a copy of each in the persistence context
	 * for dirty checking, plus the whole CSV in one String. Memory grows with the table.
	 */
	@Transactional(readOnly = true)
	public String exportNaive() {
		StringBuilder csv = new StringBuilder(HEADER);
		for (Order o : orders.findAll()) {
			csv.append(new OrderExportRow(o.getId(), o.getCustomer().getId(), o.getTicketType().getId(),
					o.getQuantity(), o.getTotal(), o.getStatus(), o.getCreatedAt()).toCsv());
		}
		return csv.toString();
	}

	/**
	 * v2 — a database cursor end to end: the driver fetches 1,000 rows, we write 1,000 lines, repeat.
	 * Memory stays flat no matter how large the table is. The stream needs an open transaction,
	 * which is why this runs inside the template and not in the controller.
	 */
	public void export(Writer out) {
		readOnlyTx.executeWithoutResult(status -> {
			try (Stream<OrderExportRow> rows = orders.streamExportRows()) {
				out.write(HEADER);
				for (var it = rows.iterator(); it.hasNext();) {
					out.write(it.next().toCsv());
				}
				out.flush();
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}
}
