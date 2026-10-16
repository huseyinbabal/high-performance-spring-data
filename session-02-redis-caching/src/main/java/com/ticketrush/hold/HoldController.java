package com.ticketrush.hold;

import com.ticketrush.hold.HoldService.Hold;
import com.ticketrush.hold.HoldService.HoldRequest;
import com.ticketrush.order.OrderService.Receipt;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** v2 purchase: hold first (Redis), pay within the TTL, confirm (PostgreSQL). */
@RestController
public class HoldController {

	private final HoldService holds;

	public HoldController(HoldService holds) {
		this.holds = holds;
	}

	@PostMapping("/v2/holds")
	@ResponseStatus(HttpStatus.CREATED)
	Hold hold(@RequestBody HoldRequest request) {
		return holds.hold(request);
	}

	@PostMapping("/v2/holds/{token}/confirm")
	@ResponseStatus(HttpStatus.CREATED)
	Receipt confirm(@PathVariable String token) {
		return holds.confirm(token);
	}

	@DeleteMapping("/v2/holds/{token}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void cancel(@PathVariable String token) {
		holds.cancel(token);
	}
}
