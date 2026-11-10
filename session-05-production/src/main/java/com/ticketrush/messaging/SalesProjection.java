package com.ticketrush.messaging;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import tools.jackson.databind.json.JsonMapper;

import com.ticketrush.messaging.Messages.OrderPlaced;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Read model #3: "tickets sold per event" as a Redis sorted set, updated by every order.
 * The trending block becomes ZREVRANGE instead of an aggregate over 86,000 rows.
 */
@Component
public class SalesProjection {

	public static final String KEY = "trending:tickets";

	private static final RedisScript<Long> COUNT_ONCE = RedisScript.of(new ClassPathResource("redis/count-once.lua"),
			Long.class);

	private final StringRedisTemplate redis;

	private final JsonMapper json;

	public SalesProjection(StringRedisTemplate redis, JsonMapper json) {
		this.redis = redis;
		this.json = json;
	}

	/**
	 * Adding is NOT idempotent: a redelivered message would count the same tickets twice.
	 * So the script records the message id and increments in one atomic step — a duplicate changes nothing.
	 */
	@KafkaListener(topics = Topics.ORDERS, groupId = "sales-projection", concurrency = "3",
			autoStartup = "${ticketrush.workers.enabled:true}")
	public void onOrderPlaced(ConsumerRecord<String, String> record) {
		OrderPlaced order = json.readValue(record.value(), OrderPlaced.class);
		redis.execute(COUNT_ONCE, List.of(KEY, "trending:seen:" + Topics.messageId(record)),
				String.valueOf(order.eventId()), String.valueOf(order.quantity()));
	}
}
