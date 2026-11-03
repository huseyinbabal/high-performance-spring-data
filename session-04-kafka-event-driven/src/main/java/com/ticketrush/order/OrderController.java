package com.ticketrush.order;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

	private final OrderService orders;

	private final PaymentGateway payments;

	public OrderController(OrderService orders, PaymentGateway payments) {
		this.orders = orders;
		this.payments = payments;
	}

	@GetMapping("/orders")
	List<OrderRow> feed(@RequestParam(required = false) Long after, @RequestParam(defaultValue = "20") int size) {
		return orders.feed(after, size);
	}

	@GetMapping("/orders/{id}/status")
	Map<String, String> status(@PathVariable long id) {
		return Map.of("status", orders.statusOf(id));
	}

	/** Demo switch: PUT /admin/payment/latency?ms=2000 makes the payment provider slow. */
	@PutMapping("/admin/payment/latency")
	Map<String, Long> paymentLatency(@RequestParam long ms) {
		payments.setLatencyMs(ms);
		return Map.of("paymentLatencyMs", ms);
	}

	@GetMapping("/customers/{customerId}/orders")
	List<OrderRow> ordersOf(@PathVariable long customerId, @RequestParam(defaultValue = "20") int size) {
		return orders.ordersOf(customerId, size);
	}
}
