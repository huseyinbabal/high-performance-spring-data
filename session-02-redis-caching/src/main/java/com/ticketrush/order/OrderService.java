package com.ticketrush.order;

import java.math.BigDecimal;
import java.util.List;

import com.ticketrush.catalog.TicketType;
import com.ticketrush.catalog.TicketTypeRepository;
import com.ticketrush.customer.CustomerRepository;

import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {

	private final OrderRepository orders;

	private final TicketTypeRepository ticketTypes;

	private final CustomerRepository customers;

	private final PaymentGateway payments;

	private final TransactionTemplate tx;

	public OrderService(OrderRepository orders, TicketTypeRepository ticketTypes, CustomerRepository customers,
			PaymentGateway payments, TransactionTemplate tx) {
		this.orders = orders;
		this.ticketTypes = ticketTypes;
		this.customers = customers;
		this.payments = payments;
		this.tx = tx;
	}

	@Transactional(readOnly = true)
	public List<OrderRow> feed(Long after, int size) {
		return orders.feedAfter(after == null ? Long.MAX_VALUE : after, Limit.of(size));
	}

	@Transactional(readOnly = true)
	public List<OrderRow> ordersOf(long customerId, int size) {
		return orders.findByCustomer(customerId, Limit.of(size));
	}

	/**
	 * The Session 1 purchase: reserve atomically + record the order → pay (no connection held) → confirm.
	 * Correct and fast — but every buyer, including the thousands who will hear "sold out",
	 * still costs PostgreSQL a SELECT and an UPDATE on the same hot row.
	 */
	public Receipt purchase(PurchaseRequest req) {
		Receipt pending = tx.execute(status -> {
			TicketType ticket = ticketTypes.findById(req.ticketTypeId()).orElseThrow(OrderService::unknownTicket);
			if (ticketTypes.reserve(req.ticketTypeId(), req.quantity()) == 0) {
				throw new SoldOutException(req.ticketTypeId());
			}
			BigDecimal total = ticket.getPrice().multiply(BigDecimal.valueOf(req.quantity()));
			Order order = orders.save(new Order(customers.getReferenceById(req.customerId()), ticket,
					req.quantity(), total, "PENDING"));
			return new Receipt(order.getId(), "PENDING", total);
		});

		try {
			payments.charge(req.customerId(), pending.total());
		}
		catch (RuntimeException e) {
			tx.executeWithoutResult(status -> {
				ticketTypes.release(req.ticketTypeId(), req.quantity());
				orders.updateStatus(pending.orderId(), "FAILED");
			});
			throw e;
		}

		tx.executeWithoutResult(status -> orders.updateStatus(pending.orderId(), "PAID"));
		return new Receipt(pending.orderId(), "PAID", pending.total());
	}

	private static ResponseStatusException unknownTicket() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "ticket type not found");
	}

	public record PurchaseRequest(long customerId, long ticketTypeId, int quantity) {
	}

	public record Receipt(Long orderId, String status, BigDecimal total) {
	}
}
