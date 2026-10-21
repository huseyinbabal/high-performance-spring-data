package com.ticketrush.hold;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ticketrush.catalog.TicketTypeRepository;
import com.ticketrush.order.OrderService;
import com.ticketrush.order.OrderService.PurchaseRequest;
import com.ticketrush.order.OrderService.Receipt;
import com.ticketrush.order.SoldOutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ticket holds: "these 2 tickets are yours for 2 minutes". The state lives in Redis, so it is shared by
 * every app instance, it expires by itself, and the sold-out answer never touches PostgreSQL.
 */
@Service
public class HoldService {

	private static final RedisScript<Long> HOLD = RedisScript.of(new ClassPathResource("redis/hold.lua"), Long.class);

	private static final RedisScript<Long> SETTLE = RedisScript.of(new ClassPathResource("redis/settle.lua"),
			Long.class);

	private final StringRedisTemplate redis;

	private final TicketTypeRepository ticketTypes;

	private final OrderService orders;

	private final RateLimiter rateLimiter;

	private final Duration ttl;

	public HoldService(StringRedisTemplate redis, TicketTypeRepository ticketTypes, OrderService orders,
			RateLimiter rateLimiter, @Value("${ticketrush.hold.ttl}") Duration ttl) {
		this.redis = redis;
		this.ticketTypes = ticketTypes;
		this.orders = orders;
		this.rateLimiter = rateLimiter;
		this.ttl = ttl;
	}

	public Hold hold(HoldRequest req) {
		if (!rateLimiter.allowHold(req.customerId())) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "too many holds, slow down");
		}
		// the token is the whole hold: ticketType.quantity.customer.random
		String token = req.ticketTypeId() + "." + req.quantity() + "." + req.customerId() + "."
				+ UUID.randomUUID().toString().substring(0, 8);
		long now = System.currentTimeMillis();
		long expiresAt = now + ttl.toMillis();

		long remaining = take(req, token, now, expiresAt);
		if (remaining == -2) {                       // first hold ever for this ticket type
			initStock(req.ticketTypeId());
			remaining = take(req, token, now, expiresAt);
		}
		if (remaining < 0) {
			throw new SoldOutException(req.ticketTypeId());
		}
		return new Hold(token, Instant.ofEpochMilli(expiresAt), remaining);
	}

	/** Turn a hold into a paid order. Only buyers who hold tickets ever reach the database. */
	public Receipt confirm(String token) {
		HoldToken hold = HoldToken.parse(token);
		if (settle(hold, token, false) == 0) {
			throw new ResponseStatusException(HttpStatus.GONE, "hold expired");
		}
		try {
			return orders.purchase(new PurchaseRequest(hold.customerId(), hold.ticketTypeId(), hold.quantity()));
		}
		catch (RuntimeException e) {                 // payment failed: the tickets go back on sale
			redis.opsForValue().increment(stockKey(hold.ticketTypeId()), hold.quantity());
			throw e;
		}
	}

	public void cancel(String token) {
		settle(HoldToken.parse(token), token, true);
	}

	/** Live availability for an event page: one MGET, no SQL. */
	public Map<Long, Integer> available(List<Long> ticketTypeIds) {
		List<String> values = redis.opsForValue().multiGet(ticketTypeIds.stream().map(HoldService::stockKey).toList());
		Map<Long, Integer> result = new LinkedHashMap<>();
		for (int i = 0; i < ticketTypeIds.size(); i++) {
			long id = ticketTypeIds.get(i);
			String value = values.get(i);
			result.put(id, value != null ? Integer.parseInt(value) : initStock(id));
		}
		return result;
	}

	private long take(HoldRequest req, String token, long now, long expiresAt) {
		return redis.execute(HOLD, keys(req.ticketTypeId()), String.valueOf(now), String.valueOf(expiresAt), token,
				String.valueOf(req.quantity()));
	}

	private long settle(HoldToken hold, String token, boolean giveBack) {
		return redis.execute(SETTLE, keys(hold.ticketTypeId()), String.valueOf(System.currentTimeMillis()), token,
				String.valueOf(hold.quantity()), giveBack ? "1" : "0");
	}

	/** Seed the counter from the source of truth. SET NX: if two instances race, the first one wins. */
	private int initStock(long ticketTypeId) {
		int available = ticketTypes.findById(ticketTypeId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ticket type not found"))
			.available();
		redis.opsForValue().setIfAbsent(stockKey(ticketTypeId), String.valueOf(available));
		return available;
	}

	// {…} is a Redis Cluster hash tag: both keys of one ticket type land on the same node,
	// which a multi-key Lua script requires.
	private static List<String> keys(long ticketTypeId) {
		return List.of(stockKey(ticketTypeId), "tt:{" + ticketTypeId + "}:holds");
	}

	private static String stockKey(long ticketTypeId) {
		return "tt:{" + ticketTypeId + "}:stock";
	}

	public record HoldRequest(long customerId, long ticketTypeId, int quantity) {
	}

	public record Hold(String token, Instant expiresAt, long remaining) {
	}

	record HoldToken(long ticketTypeId, int quantity, long customerId) {

		static HoldToken parse(String token) {
			String[] parts = token.split("\\.");
			if (parts.length != 4) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "malformed hold token");
			}
			return new HoldToken(Long.parseLong(parts[0]), Integer.parseInt(parts[1]), Long.parseLong(parts[2]));
		}
	}
}
