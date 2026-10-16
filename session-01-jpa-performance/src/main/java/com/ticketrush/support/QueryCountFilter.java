package com.ticketrush.support;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** Adds X-Query-Count / X-Elapsed-Ms to every API response and logs one line per request. */
@Component
public class QueryCountFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger("ticketrush.sql");

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return request.getRequestURI().startsWith("/actuator");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		// buffer the body so headers can still be written after the controller has run
		var buffered = new ContentCachingResponseWrapper(response);
		SqlCounter.reset();
		long start = System.nanoTime();
		try {
			chain.doFilter(request, buffered);
		}
		finally {
			long ms = (System.nanoTime() - start) / 1_000_000;
			int queries = SqlCounter.current();
			buffered.setHeader("X-Query-Count", String.valueOf(queries));
			buffered.setHeader("X-Elapsed-Ms", String.valueOf(ms));
			buffered.copyBodyToResponse();
			log.info("{} {} → {} queries, {} ms", request.getMethod(), request.getRequestURI(), queries, ms);
		}
	}
}
