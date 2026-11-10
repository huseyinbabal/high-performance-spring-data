package com.ticketrush.search;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import com.ticketrush.search.SearchResult.Facet;
import com.ticketrush.search.SearchResult.Hit;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SearchService {

	private final ElasticsearchOperations operations;

	private final Timer searchTimer;

	public SearchService(ElasticsearchOperations operations, MeterRegistry registry) {
		this.operations = operations;
		// our own timer around the Elasticsearch round trip, with histogram buckets so Prometheus can compute p95
		this.searchTimer = Timer.builder("ticketrush.search")
			.description("Elasticsearch search round trip")
			.publishPercentileHistogram()
			.register(registry);
	}

	public SearchResult search(String text, String city, String category, BigDecimal maxPrice, int page) {
		return searchTimer.record(() -> doSearch(text, city, category, maxPrice, page));
	}

	private SearchResult doSearch(String text, String city, String category, BigDecimal maxPrice, int page) {
		NativeQuery query = NativeQuery.builder().withQuery(q -> q.bool(b -> {
			if (StringUtils.hasText(text)) {
				// must = recall: every word has to appear somewhere in the document, typos allowed
				b.must(m -> m.match(t -> t.field("all").query(text).operator(Operator.And).fuzziness("AUTO")));
				// should = ranking: a hit in the name or artist counts for more than one in the description
				b.should(s -> s.multiMatch(mm -> mm.query(text).fields("name^3", "artist^3")));
			}
			// filters are yes/no questions: no scoring, and Elasticsearch caches them
			b.filter(f -> f.range(r -> r.date(d -> d.field("startsAt").gte("now"))));
			if (StringUtils.hasText(city)) {
				b.filter(f -> f.term(t -> t.field("city").value(city)));
			}
			if (StringUtils.hasText(category)) {
				b.filter(f -> f.term(t -> t.field("category").value(category)));
			}
			if (maxPrice != null) {
				b.filter(f -> f.range(r -> r.number(n -> n.field("minPrice").lte(maxPrice.doubleValue()))));
			}
			return b;
		}))
			// facets come back with the hits, computed over the whole result set — one round trip
			.withAggregation("city", Aggregation.of(a -> a.terms(t -> t.field("city").size(10))))
			.withAggregation("category", Aggregation.of(a -> a.terms(t -> t.field("category").size(10))))
			.withPageable(PageRequest.of(page, 20))
			.build();

		SearchHits<EventDocument> hits = operations.search(query, EventDocument.class);

		Map<String, List<Facet>> facets = new LinkedHashMap<>();
		facets.put("city", terms(hits, "city"));
		facets.put("category", terms(hits, "category"));
		return new SearchResult(hits.getTotalHits(), hits.getSearchHits().stream().map(h -> {
			EventDocument d = h.getContent();
			return new Hit(d.id(), d.name(), d.artist(), d.city(), d.venue(), d.startsAt(), d.minPrice(),
					h.getScore());
		}).toList(), facets);
	}

	/** Search-as-you-type: "gece yol" → event names, answered from a prefix-optimised sub-field. */
	public List<String> suggest(String prefix) {
		NativeQuery query = NativeQuery.builder()
			.withQuery(q -> q.multiMatch(mm -> mm.query(prefix)
				.type(TextQueryType.BoolPrefix)
				.fields("name.suggest", "name.suggest._2gram", "name.suggest._3gram")))
			.withPageable(PageRequest.of(0, 50))
			.build();
		return operations.search(query, EventDocument.class)
			.getSearchHits()
			.stream()
			.map(h -> h.getContent().name())
			.distinct()
			.limit(8)
			.toList();
	}

	/** Analytics on the same index: upcoming events per month, with the average entry price. */
	public List<MonthBucket> calendar(String city) {
		NativeQuery query = NativeQuery.builder().withQuery(q -> q.bool(b -> {
			b.filter(f -> f.range(r -> r.date(d -> d.field("startsAt").gte("now"))));
			if (StringUtils.hasText(city)) {
				b.filter(f -> f.term(t -> t.field("city").value(city)));
			}
			return b;
		}))
			.withAggregation("months",
					Aggregation.of(a -> a.dateHistogram(h -> h.field("startsAt").calendarInterval(CalendarInterval.Month))
						.aggregations("avgPrice", avg -> avg.avg(v -> v.field("minPrice")))))
			.withMaxResults(0)                       // we want the buckets, not the documents
			.build();

		SearchHits<EventDocument> hits = operations.search(query, EventDocument.class);
		return ((ElasticsearchAggregations) hits.getAggregations()).get("months")
			.aggregation()
			.getAggregate()
			.dateHistogram()
			.buckets()
			.array()
			.stream()
			.map(b -> new MonthBucket(b.keyAsString().substring(0, 7), b.docCount(),
					Math.round(b.aggregations().get("avgPrice").avg().value() * 100) / 100.0))
			.toList();
	}

	private static List<Facet> terms(SearchHits<EventDocument> hits, String name) {
		return ((ElasticsearchAggregations) hits.getAggregations()).get(name)
			.aggregation()
			.getAggregate()
			.sterms()
			.buckets()
			.array()
			.stream()
			.map(b -> new Facet(b.key().stringValue(), b.docCount()))
			.toList();
	}

	public record MonthBucket(String month, long events, double avgFromPrice) {
	}
}
