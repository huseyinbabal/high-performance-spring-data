package com.ticketrush.customer;

import com.ticketrush.customer.CustomerImportService.ImportResult;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerImportController {

	private final CustomerImportService imports;

	public CustomerImportController(CustomerImportService imports) {
		this.imports = imports;
	}

	@PostMapping("/v1/customers/import")
	ImportResult importNaive(@RequestParam(defaultValue = "20000") int n) {
		return imports.importNaiveAll(n);
	}

	@PostMapping("/v2/customers/import")
	ImportResult importBatched(@RequestParam(defaultValue = "20000") int n) {
		return imports.importBatched(n);
	}
}
