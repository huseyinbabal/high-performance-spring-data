package com.ticketrush.export;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
public class OrderExportController {

	private static final MediaType CSV = MediaType.parseMediaType("text/csv");

	private final OrderExportService exports;

	public OrderExportController(OrderExportService exports) {
		this.exports = exports;
	}

	/** v1 — build the whole file in memory, then send it. */
	@GetMapping("/v1/orders/export")
	ResponseEntity<String> exportNaive() {
		return ResponseEntity.ok().contentType(CSV).body(exports.exportNaive());
	}

	/** v2 — stream rows from the database cursor straight into the HTTP response. */
	@GetMapping("/v2/orders/export")
	ResponseEntity<StreamingResponseBody> export() {
		StreamingResponseBody body = out -> exports.export(new OutputStreamWriter(out, StandardCharsets.UTF_8));
		return ResponseEntity.ok().contentType(CSV).body(body);
	}
}
