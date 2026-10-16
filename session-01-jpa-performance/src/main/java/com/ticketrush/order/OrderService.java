package com.ticketrush.order;

import java.math.BigDecimal;
import java.util.List;

import com.ticketrush.catalog.TicketType;
import com.ticketrush.catalog.TicketTypeRepository;
import com.ticketrush.customer.CustomerRepository;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

	// ---------------------------------------------------------------- reads

	/** v1 — OFFSET walks and throws away every earlier row, Page adds a count(*), the mapping adds N+1. */
	@Transactional(readOnly = true)
	public Page<OrderRow> feedNaive(int page, int size) {
		return orders.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))).map(OrderRow::from);
	}

	/** v2 — keyset: pass the last id you saw; no OFFSET, no count, one query. */
	@Transactional(readOnly = true)
	public List<OrderRow> feed(Long after, int size) {
		return orders.feedAfter(after == null ? Long.MAX_VALUE : after, Limit.of(size));
	}

	@Transactional(readOnly = true)
	public List<OrderRow> ordersOf(long customerId, int size) {
		return orders.findByCustomer(customerId, Limit.of(size));
	}

	// ---------------------------------------------------------------- writes

	/**
	 * v1 — the method everyone writes first. Two bugs that only show up under load:
	 * (1) the payment call runs INSIDE the transaction, so a pooled connection sits idle for its whole duration;
	 * (2) read sold → add → write back is not atomic, so concurrent buyers overwrite each other (oversell).
	 */
	@Transactional
	public Receipt purchaseNaive(PurchaseRequest req) {
		TicketType ticket = ticketTypes.findById(req.ticketTypeId()).orElseThrow(OrderService::unknownTicket);
		if (ticket.available() < req.quantity()) {
			throw new SoldOutException(req.ticketTypeId());
		}
		BigDecimal total = ticket.getPrice().multiply(BigDecimal.valueOf(req.quantity()));

		payments.charge(req.customerId(), total);

		ticket.setSold(ticket.getSold() + req.quantity());
		Order order = orders.save(new Order(customers.getReferenceById(req.customerId()), ticket, req.quantity(),
				total, "PAID"));
		return new Receipt(order.getId(), "PAID", total);
	}

	/**
	 * v2 — two short transactions with the slow call between them, holding no connection:
	 * reserve atomically + record the order → pay → confirm (or give the tickets back).
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
