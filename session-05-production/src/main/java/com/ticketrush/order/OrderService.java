package com.ticketrush.order;

import java.math.BigDecimal;
import java.util.List;

import com.ticketrush.catalog.TicketType;
import com.ticketrush.catalog.TicketTypeRepository;
import com.ticketrush.customer.CustomerRepository;
import com.ticketrush.messaging.Messages.OrderPlaced;
import com.ticketrush.messaging.Topics;
import com.ticketrush.outbox.Outbox;

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

	private final Outbox outbox;

	public OrderService(OrderRepository orders, TicketTypeRepository ticketTypes, CustomerRepository customers,
			PaymentGateway payments, TransactionTemplate tx, Outbox outbox) {
		this.outbox = outbox;
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

	/** Synchronous purchase: the request thread waits for the payment provider. Kept for tonight's thread demo. */
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
			payments.charge(String.valueOf(pending.orderId()), req.customerId(), pending.total());
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

	/** Asynchronous purchase (Session 4): order + OrderPlaced event in one transaction; PaymentWorker settles it. */
	@Transactional
	public Receipt place(PurchaseRequest req) {
		TicketType ticket = ticketTypes.findById(req.ticketTypeId()).orElseThrow(OrderService::unknownTicket);
		if (ticketTypes.reserve(req.ticketTypeId(), req.quantity()) == 0) {
			throw new SoldOutException(req.ticketTypeId());
		}
		BigDecimal total = ticket.getPrice().multiply(BigDecimal.valueOf(req.quantity()));
		Order order = orders.save(new Order(customers.getReferenceById(req.customerId()), ticket, req.quantity(),
				total, "PENDING"));
		outbox.add(Topics.ORDERS, order.getId(), new OrderPlaced(order.getId(), req.customerId(),
				ticket.getEvent().getId(), req.ticketTypeId(), req.quantity(), total));
		return new Receipt(order.getId(), "PENDING", total);
	}

	/**
	 * Called by the payment worker, possibly more than once for the same order (at-least-once delivery).
	 * Safe to repeat: the charge carries an idempotency key, and PENDING → PAID succeeds only the first time.
	 *
	 * @return false if the payment failed and the tickets were released
	 */
	public boolean pay(OrderPlaced order) {
		// A decision, so it reads from the PRIMARY (tx.execute = read-write transaction). On the replica the
		// order committed a few ms ago might not exist yet — and we would skip a payment as "already settled".
		String current = tx.execute(s -> orders.findStatus(order.orderId()).orElse(null));
		if (!"PENDING".equals(current)) {
			return true;                             // duplicate message: already settled
		}
		try {
			payments.charge(String.valueOf(order.orderId()), order.customerId(), order.total());
		}
		catch (RuntimeException e) {
			tx.executeWithoutResult(status -> {
				if (orders.transition(order.orderId(), "PENDING", "FAILED") == 1) {
					ticketTypes.release(order.ticketTypeId(), order.quantity());
				}
			});
			return false;
		}
		tx.executeWithoutResult(status -> orders.transition(order.orderId(), "PENDING", "PAID"));
		return true;
	}

	/** Status polling from the UI: served by the replica; being a few ms behind is fine here. */
	@Transactional(readOnly = true)
	public String statusOf(long orderId) {
		return orders.findStatus(orderId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
	}

	private static ResponseStatusException unknownTicket() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "ticket type not found");
	}

	public record PurchaseRequest(long customerId, long ticketTypeId, int quantity) {
	}

	public record Receipt(Long orderId, String status, BigDecimal total) {
	}
}
