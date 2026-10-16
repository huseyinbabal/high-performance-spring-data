package com.ticketrush.order;

import java.util.List;

import com.ticketrush.order.OrderService.PurchaseRequest;
import com.ticketrush.order.OrderService.Receipt;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

	private final OrderService orders;

	public OrderController(OrderService orders) {
		this.orders = orders;
	}

	@GetMapping("/orders")
	List<OrderRow> feed(@RequestParam(required = false) Long after, @RequestParam(defaultValue = "20") int size) {
		return orders.feed(after, size);
	}

	@GetMapping("/customers/{customerId}/orders")
	List<OrderRow> ordersOf(@PathVariable long customerId, @RequestParam(defaultValue = "20") int size) {
		return orders.ordersOf(customerId, size);
	}

	/** v1 — buy straight against PostgreSQL (the Session 1 result). */
	@PostMapping("/v1/orders")
	@ResponseStatus(HttpStatus.CREATED)
	Receipt purchase(@RequestBody PurchaseRequest request) {
		return orders.purchase(request);
	}
}
