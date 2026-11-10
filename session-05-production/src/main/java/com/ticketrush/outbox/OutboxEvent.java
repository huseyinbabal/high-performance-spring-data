package com.ticketrush.outbox;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/** A message waiting to be published. Written in the same transaction as the change it describes. */
@Entity
@Table(name = "outbox")
public class OutboxEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "outbox_seq")
	@SequenceGenerator(name = "outbox_seq", sequenceName = "outbox_seq", allocationSize = 50)
	private Long id;

	private String topic;

	private String messageKey;

	private String type;

	private String payload;

	private Instant createdAt;

	private Instant publishedAt;

	/** W3C trace context of the request that wrote this row: the relay puts it on the Kafka record. */
	private String traceparent;

	protected OutboxEvent() {
	}

	public OutboxEvent(String topic, String messageKey, String type, String payload, String traceparent) {
		this.traceparent = traceparent;
		this.topic = topic;
		this.messageKey = messageKey;
		this.type = type;
		this.payload = payload;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getTopic() {
		return topic;
	}

	public String getMessageKey() {
		return messageKey;
	}

	public String getType() {
		return type;
	}

	public String getTraceparent() {
		return traceparent;
	}

	public String getPayload() {
		return payload;
	}
}
