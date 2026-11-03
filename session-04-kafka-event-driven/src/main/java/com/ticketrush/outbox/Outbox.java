package com.ticketrush.outbox;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The only way the application "sends" an event: by inserting a row. */
@Component
public class Outbox {

	private final OutboxRepository repository;

	private final JsonMapper json;

	public Outbox(OutboxRepository repository, JsonMapper json) {
		this.repository = repository;
		this.json = json;
	}

	/**
	 * MANDATORY: refuses to run outside a transaction. The row must commit or roll back
	 * together with the business change — that is the whole point of the pattern.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void add(String topic, Object key, Object message) {
		repository.save(new OutboxEvent(topic, String.valueOf(key), message.getClass().getSimpleName(),
				json.writeValueAsString(message)));
	}
}
