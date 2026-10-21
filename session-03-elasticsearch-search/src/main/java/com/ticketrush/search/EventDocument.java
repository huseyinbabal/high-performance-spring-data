package com.ticketrush.search;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

/**
 * The search read model: one flat, denormalized document per event — venue and cheapest price included,
 * so a search never joins. The index is defined by explicit JSON (resources/es), not inferred from this class.
 * {@code events} is an alias; EventProjector creates the real index behind it.
 */
@Document(indexName = EventDocument.INDEX, createIndex = false, writeTypeHint = WriteTypeHint.FALSE)
@Setting(settingPath = "es/settings.json")
@Mapping(mappingPath = "es/mappings.json")
public record EventDocument(@Id Long id, String name, String artist, String category, String city, String venue,
		String description, @Field(type = FieldType.Date, format = DateFormat.date_time) Instant startsAt,
		BigDecimal minPrice, String status) {

	public static final String INDEX = "events";
}
