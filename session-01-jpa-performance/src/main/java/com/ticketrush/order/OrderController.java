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

	/** Back-office order feed, offset style: ?page=40000 */
	@GetMapping("/v1/orders")
	FeedPage feedNaive(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		var result = orders.feedNaive(page, size);
		return new FeedPage(result.getContent(), result.getTotalElements());
	}

	/** Back-office order feed, keyset style: ?after=<last id on the previous page> */
	@GetMapping("/v2/orders")
	List<OrderRow> feed(@RequestParam(required = false) Long after, @RequestParam(defaultValue = "20") int size) {
		return orders.feed(after, size);
	}

	@GetMapping("/v2/customers/{customerId}/orders")
	List<OrderRow> ordersOf(@PathVariable long customerId, @RequestParam(defaultValue = "20") int size) {
		return orders.ordersOf(customerId, size);
	}

	@PostMapping("/v1/orders")
	@ResponseStatus(HttpStatus.CREATED)
	Receipt purchaseNaive(@RequestBody PurchaseRequest request) {
		return orders.purchaseNaive(request);
	}

	@PostMapping("/v2/orders")
	@ResponseStatus(HttpStatus.CREATED)
	Receipt purchase(@RequestBody PurchaseRequest request) {
		return orders.purchase(request);
	}

	record FeedPage(List<OrderRow> content, long total) {
	}
}
