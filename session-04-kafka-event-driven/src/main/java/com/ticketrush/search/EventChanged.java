package com.ticketrush.search;

/** Published by the write path when an event's searchable data changes. */
public record EventChanged(long eventId) {
}
