package com.ticketrush.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface EventSearchRepository extends ElasticsearchRepository<EventDocument, Long> {
}
