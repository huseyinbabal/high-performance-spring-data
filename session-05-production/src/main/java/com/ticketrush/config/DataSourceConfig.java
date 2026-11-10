package com.ticketrush.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

/**
 * Read/write splitting without touching a single repository.
 * Two pools, one routing proxy: a transaction marked readOnly gets a replica connection, everything else
 * the primary. "Lazy" matters twice — the pool is only asked for a connection when the first statement
 * runs (a cache hit inside a transaction costs no connection), and by then the proxy knows the readOnly flag.
 */
@Configuration
public class DataSourceConfig {

	@Bean
	@ConfigurationProperties("ticketrush.datasource.primary")
	HikariDataSource primaryDataSource() {
		return new HikariDataSource();
	}

	@Bean
	@ConfigurationProperties("ticketrush.datasource.replica")
	HikariDataSource replicaDataSource() {
		return new HikariDataSource();
	}

	@Bean
	@Primary
	DataSource dataSource(@Qualifier("primaryDataSource") HikariDataSource primary,
			@Qualifier("replicaDataSource") HikariDataSource replica) {
		LazyConnectionDataSourceProxy routing = new LazyConnectionDataSourceProxy(primary);
		routing.setReadOnlyDataSource(replica);
		return routing;
	}
}
