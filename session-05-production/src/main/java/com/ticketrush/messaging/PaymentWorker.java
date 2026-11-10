package com.ticketrush.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import tools.jackson.databind.json.JsonMapper;

import com.ticketrush.hold.HoldService;
import com.ticketrush.messaging.Messages.OrderPlaced;
import com.ticketrush.order.OrderService;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The slow part of a purchase, moved off the request thread. The API answered long ago;
 * this worker takes as long as the payment provider needs — and if the provider is slow,
 * the backlog waits in Kafka instead of in our HTTP threads.
 */
@Component
public class PaymentWorker {

	private final OrderService orders;

	private final HoldService holds;

	private final JsonMapper json;

	public PaymentWorker(OrderService orders, HoldService holds, JsonMapper json) {
		this.orders = orders;
		this.holds = holds;
		this.json = json;
	}

	// A slow handler takes SMALL polls: 10 records × 150 ms is 1.5 s between polls. With the default 500,
	// a 2 s provider would mean 1,000 s per poll — past max.poll.interval.ms (5 min), so the group would
	// throw this consumer out and rebalance, over and over.
	@KafkaListener(topics = Topics.ORDERS, groupId = "payments",
			concurrency = "${ticketrush.payments.concurrency:12}", properties = "max.poll.records=10",
			autoStartup = "${ticketrush.workers.enabled:true}")
	public void onOrderPlaced(ConsumerRecord<String, String> record) {
		OrderPlaced order = json.readValue(record.value(), OrderPlaced.class);
		if (!orders.pay(order)) {
			holds.returnStock(order.ticketTypeId(), order.quantity());   // payment failed: back on sale
		}
	}
}
