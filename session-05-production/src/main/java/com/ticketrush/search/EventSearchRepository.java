package com.ticketrush.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Nothing calls this directly — its job is at startup: Spring Data creates the `events` index with OUR
 * settings and mapping if it is missing, before any consumer can auto-create it with a guessed one.
 */
public interface EventSearchRepository extends ElasticsearchRepository<EventDocument, Long> {
}
