package com.ticketrush.customer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Bulk-loading customers (think: importing a partner's member list) — the write-path lesson. */
@Service
public class CustomerImportService {

	private static final int CHUNK = 1000;

	private final CustomerRepository customers;

	private final EntityManager em;

	private final TransactionTemplate tx;

	public CustomerImportService(CustomerRepository customers, EntityManager em, TransactionTemplate tx) {
		this.customers = customers;
		this.em = em;
		this.tx = tx;
	}

	/** v1 — save() in a loop: every row is its own transaction, its own commit, its own fsync. */
	public ImportResult importNaive(int n) {
		long start = System.nanoTime();
		String run = UUID.randomUUID().toString().substring(0, 8);
		for (int i = 0; i < n; i++) {
			customers.save(newCustomer(run, i));
		}
		return ImportResult.of("v1 save() per row", n, start);
	}

	public ImportResult importNaiveAll(int n) {
		long start = System.nanoTime();
		String run = UUID.randomUUID().toString().substring(0, 8);
        List<Customer> cList = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			cList.add(newCustomer(run, i));
		}
		customers.saveAll(cList);
		return ImportResult.of("v1 save() per row", n, start);
	}

	/**
	 * v2 — one transaction per 1000 rows. Hibernate queues the INSERTs and ships them 100 at a time
	 * (hibernate.jdbc.batch_size); flush + clear keeps the persistence context from growing without bound.
	 */
	public ImportResult importBatched(int n) {
		long start = System.nanoTime();
		String run = UUID.randomUUID().toString().substring(0, 8);
		for (int from = 0; from < n; from += CHUNK) {
			int begin = from;
			int end = Math.min(from + CHUNK, n);
			tx.executeWithoutResult(status -> {
				for (int i = begin; i < end; i++) {
					em.persist(newCustomer(run, i));
				}
				em.flush();
				em.clear();
			});
		}
		return ImportResult.of("v2 JDBC batching", n, start);
	}

	private static Customer newCustomer(String run, int i) {
		return new Customer("import-" + run + "-" + i + "@example.com", "Imported Customer " + i, "İstanbul");
	}

	public record ImportResult(String mode, int rows, long elapsedMs, long rowsPerSecond) {

		static ImportResult of(String mode, int rows, long startNanos) {
			long ms = Math.max(1, (System.nanoTime() - startNanos) / 1_000_000);
			return new ImportResult(mode, rows, ms, rows * 1000L / ms);
		}
	}
}
