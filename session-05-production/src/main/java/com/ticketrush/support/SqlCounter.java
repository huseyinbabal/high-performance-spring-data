package com.ticketrush.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Hibernate calls this for every SQL statement it is about to send. We just count —
 * per thread, which is per request — so each response can say how chatty it was.
 */
public class SqlCounter implements StatementInspector {

	private static final ThreadLocal<int[]> COUNT = ThreadLocal.withInitial(() -> new int[1]);

	@Override
	public String inspect(String sql) {
		COUNT.get()[0]++;
		return sql;
	}

	public static void reset() {
		COUNT.get()[0] = 0;
	}

	public static int current() {
		return COUNT.get()[0];
	}
}
