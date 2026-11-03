package com.ticketrush.order;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class SoldOutException extends RuntimeException {

	public SoldOutException(long ticketTypeId) {
		super("ticket type " + ticketTypeId + " is sold out");
	}
}
