package com.ticketrush.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import tools.jackson.databind.json.JsonMapper;

import com.ticketrush.catalog.EventService;
import com.ticketrush.messaging.Messages.EventChanged;

import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Read model #2: the Redis cache. Its own consumer group, so it gets every message independently of the indexer. */
@Component
public class CacheInvalidator {

	private final CacheManager caches;

	private final JsonMapper json;

	public CacheInvalidator(CacheManager caches, JsonMapper json) {
		this.caches = caches;
		this.json = json;
	}

	@KafkaListener(topics = Topics.EVENTS, groupId = "cache-invalidator",
			autoStartup = "${ticketrush.workers.enabled:true}")
	public void onEvent(ConsumerRecord<String, String> record) {
		long eventId = json.readValue(record.value(), EventChanged.class).eventId();
		caches.getCache(EventService.EVENT_CACHE).evict(eventId);   // evicting twice is harmless: idempotent
	}
}
