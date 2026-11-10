package com.ticketrush.support;

import java.io.IOException;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Session 1's X-Query-Count, grown up: the same per-request statement count, now exported as a metric per
 * endpoint. An N+1 that slips into production shows up on the dashboard as a line that jumps from 1 to 51.
 */
@Component
public class QueryCountFilter extends OncePerRequestFilter {

	private static final String INSTANCE = System.getenv().getOrDefault("HOSTNAME", "local");

	private final MeterRegistry registry;

	public QueryCountFilter(MeterRegistry registry) {
		this.registry = registry;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return request.getRequestURI().startsWith("/actuator");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		SqlCounter.reset();
		response.setHeader("X-Instance", INSTANCE);  // which pod answered — visible proof of load balancing
		try {
			chain.doFilter(request, response);
		}
		finally {
			Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
			if (pattern != null) {
				DistributionSummary.builder("ticketrush.sql.statements")
					.description("SQL statements per HTTP request")
					.tag("uri", pattern.toString())
					.register(registry)
					.record(SqlCounter.current());
			}
		}
	}
}
