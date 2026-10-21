package com.ticketrush.trending;

import java.time.Instant;
import java.util.List;

/** The home page's "selling fastest right now" block. */
public record Trending(List<TrendingEvent> events, Instant computedAt) {
}
