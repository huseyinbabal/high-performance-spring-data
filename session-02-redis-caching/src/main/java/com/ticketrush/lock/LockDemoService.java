package com.ticketrush.lock;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;

import com.ticketrush.catalog.TicketType;
import com.ticketrush.catalog.TicketTypeRepository;
import com.ticketrush.customer.CustomerRepository;
import com.ticketrush.order.Order;
import com.ticketrush.order.OrderRepository;
import com.ticketrush.order.OrderService.PurchaseRequest;
import com.ticketrush.order.OrderService.Receipt;
import com.ticketrush.order.SoldOutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * The same purchase, protected four different ways. Run it on ONE instance, then on TWO:
 * the lock that was correct yesterday stops being correct the moment a second replica starts.
 */
@Service
public class LockDemoService {

	public enum Mode { NONE, JVM, REDIS, ATOMIC }

	private final ReentrantLock jvmLock = new ReentrantLock();   // lives in THIS process — invisible to the others

	private final RedisLock redisLock;

	private final TicketTypeRepository ticketTypes;

	private final OrderRepository orders;

	private final CustomerRepository customers;

	private final TransactionTemplate tx;

	private final long workMs;

	public LockDemoService(RedisLock redisLock, TicketTypeRepository ticketTypes, OrderRepository orders,
			CustomerRepository customers, TransactionTemplate tx,
			@Value("${ticketrush.lock-demo.work-ms}") long workMs) {
		this.workMs = workMs;
		this.redisLock = redisLock;
		this.ticketTypes = ticketTypes;
		this.orders = orders;
		this.customers = customers;
		this.tx = tx;
	}

	public Receipt purchase(PurchaseRequest req, Mode mode) {
		return switch (mode) {
			case NONE -> readCheckWrite(req);
			case JVM -> {
				jvmLock.lock();
				try {
					yield readCheckWrite(req);
				}
				finally {
					jvmLock.unlock();
				}
			}
			// one lock PER TICKET TYPE: buyers of different tickets do not wait for each other
			case REDIS -> redisLock.withLock("lock:tt:" + req.ticketTypeId(), Duration.ofSeconds(5),
					Duration.ofSeconds(3), () -> readCheckWrite(req));
			case ATOMIC -> atomic(req);
		};
	}

	/**
	 * The critical section: read sold → check → write sold + 1. Three steps, so two callers can interleave.
	 * Note the order: the lock is taken OUTSIDE the transaction and released after the commit.
	 * Release before the commit and the next holder reads the old value.
	 */
	private Receipt readCheckWrite(PurchaseRequest req) {
		return tx.execute(status -> {
			TicketType ticket = ticketTypes.findById(req.ticketTypeId()).orElseThrow(LockDemoService::unknown);
			if (ticket.available() < req.quantity()) {
				throw new SoldOutException(req.ticketTypeId());
			}
			decide();                                // between the read and the write: where the race lives
			ticket.setSold(ticket.getSold() + req.quantity());
			return save(req, ticket);
		});
	}

	/** No lock at all: the database does check + write as one statement (Session 1). */
	private Receipt atomic(PurchaseRequest req) {
		return tx.execute(status -> {
			TicketType ticket = ticketTypes.findById(req.ticketTypeId()).orElseThrow(LockDemoService::unknown);
			decide();                                // same work as above — it just no longer needs protecting
			if (ticketTypes.reserve(req.ticketTypeId(), req.quantity()) == 0) {
				throw new SoldOutException(req.ticketTypeId());
			}
			return save(req, ticket);
		});
	}

	/** Stand-in for the business logic between "read" and "write": pricing rules, a fraud check, a quota lookup. */
	private void decide() {
		try {
			Thread.sleep(workMs);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private Receipt save(PurchaseRequest req, TicketType ticket) {
		BigDecimal total = ticket.getPrice().multiply(BigDecimal.valueOf(req.quantity()));
		Order order = orders.save(new Order(customers.getReferenceById(req.customerId()), ticket, req.quantity(),
				total, "PAID"));
		return new Receipt(order.getId(), "PAID", total);
	}

	private static ResponseStatusException unknown() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "ticket type not found");
	}
}
