package com.ticketrush.lock;

import com.ticketrush.lock.LockDemoService.Mode;
import com.ticketrush.order.OrderService.PurchaseRequest;
import com.ticketrush.order.OrderService.Receipt;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LockDemoController {

	private final LockDemoService service;

	public LockDemoController(LockDemoService service) {
		this.service = service;
	}

	/** POST /lock-demo/orders?lock=none|jvm|redis|atomic */
	@PostMapping("/lock-demo/orders")
	@ResponseStatus(HttpStatus.CREATED)
	Receipt purchase(@RequestBody PurchaseRequest request, @RequestParam(defaultValue = "none") String lock) {
		return service.purchase(request, Mode.valueOf(lock.toUpperCase()));
	}
}
