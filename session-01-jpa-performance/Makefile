# TicketRush · Session 1 — Spring Data JPA performance
# Usage: make <target>   (run `make` alone to list targets)

.DEFAULT_GOAL := help

PSQL := docker compose exec -T postgres psql -U ticketrush -d ticketrush
API  := localhost:8080
V    ?= v1
# print only the status line + our two diagnostic headers
HEAD := curl -s -o /dev/null -D - -w '' 

help: ## List all targets
	@grep -E '^[a-zA-Z0-9_-]+:.*##' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

## --- Environment ---

up: ## Start Postgres and seed ~1.6M rows (first start takes ~20 s)
	docker compose up -d --wait
	@$(PSQL) -c "select (select count(*) from event) events, (select count(*) from ticket_type) ticket_types, (select count(*) from customer) customers, (select count(*) from orders) orders"

down: ## Stop and delete the database (next `make up` re-seeds from scratch)
	docker compose down -v

run: ## Run the app on :8080   (SQL=debug prints every statement)
	SQL_LOG=$(or $(SQL),off) ./mvnw -q spring-boot:run

psql: ## Open a psql shell
	docker compose exec postgres psql -U ticketrush -d ticketrush

## --- 1 · N+1 ---
events: ## List upcoming rock events — watch X-Query-Count   (V=v1|v2)
	@$(HEAD) '$(API)/$(V)/events?category=rock&size=50' | grep -iE 'HTTP|x-query|x-elapsed'

event: ## One event's detail page — 3 queries vs 1   (V=v1|v2)
	@$(HEAD) '$(API)/$(V)/events/1' | grep -iE 'HTTP|x-query|x-elapsed'

## --- 2 · Indexes ---

my-orders: ## "My orders" for one customer — watch X-Elapsed-Ms before/after `make indexes`
	@$(HEAD) '$(API)/v2/customers/42/orders' | grep -iE 'HTTP|x-query|x-elapsed'

explain-orders: ## EXPLAIN ANALYZE the "my orders" query
	@$(PSQL) -c "explain (analyze, buffers) select * from orders where customer_id = 42 order by created_at desc limit 20"

explain-events: ## EXPLAIN ANALYZE the "upcoming events" query
	@$(PSQL) -c "explain (analyze, buffers) select * from event where category = 'rock' and starts_at > now() order by starts_at limit 50"

indexes: ## Create the two missing indexes (indexes.sql)
	$(PSQL) -q < indexes.sql

indexes-drop: ## Drop them again — back to the baseline
	$(PSQL) -c "drop index if exists event_category_starts_idx, orders_customer_created_idx"

## --- 3 · Pagination ---

feed: ## Order feed, deep page — OFFSET vs keyset   (V=v1|v2)
	@if [ "$(V)" = "v1" ]; then $(HEAD) '$(API)/v1/orders?page=40000&size=20' | grep -iE 'HTTP|x-query|x-elapsed'; \
	else $(HEAD) '$(API)/v2/orders?after=200000&size=20' | grep -iE 'HTTP|x-query|x-elapsed'; fi

explain-offset: ## EXPLAIN ANALYZE the deep OFFSET query
	@$(PSQL) -c "explain (analyze, buffers) select * from orders order by id desc offset 800000 limit 20"

## --- 4 · Batch writes ---

import: ## Import N customers — save() per row vs JDBC batching   (V=v1|v2, N=20000)
	@curl -s -X POST '$(API)/$(V)/customers/import?n=$(or $(N),20000)' ; echo

## --- 5 · The on-sale moment: connection pool + oversell ---

buy: ## Buy one ticket for the hot event   (V=v1|v2)
	@curl -s -X POST '$(API)/$(V)/orders' -H 'Content-Type: application/json' \
	  -d '{"customerId":7,"ticketTypeId":1,"quantity":1}' -w '  [%{http_code} · %{time_total}s]\n'

sold: ## Hot ticket: capacity vs the sold counter vs tickets actually sold (the oversell check)
	@$(PSQL) -c "select t.capacity, t.sold as sold_counter, (select coalesce(sum(o.quantity), 0) from orders o where o.ticket_type_id = 1 and o.status = 'PAID') as tickets_really_sold from ticket_type t where t.id = 1"

reset-hot: ## Reset the hot ticket (sold = 0, its orders deleted) before the next run
	@$(PSQL) -qc "delete from orders where ticket_type_id = 1; update ticket_type set sold = 0 where id = 1"

pool: ## HikariCP right now: active / idle / pending / timeouts
	@curl -s $(API)/actuator/prometheus | grep -E '^hikaricp_connections(_active|_idle|_pending|_timeout_total)' 

## --- Measure ---

top-queries: ## pg_stat_statements: the 8 queries that cost the most total time
	@$(PSQL) -c "select calls, round(total_exec_time::numeric) as total_ms, round(mean_exec_time::numeric, 2) as mean_ms, rows, left(regexp_replace(query, '\s+', ' ', 'g'), 110) as query from pg_stat_statements where query not like '%pg_stat_statements%' order by total_exec_time desc limit 8"

stats-reset: ## Reset pg_stat_statements before a measurement
	@$(PSQL) -qc "select pg_stat_statements_reset()" > /dev/null

k6-browse: ## Load: upcoming events   (V=v1|v2, RATE=300)
	k6 run -q -e V=$(V) -e RATE=$(or $(RATE),300) k6/browse.js

k6-feed: ## Load: deep order feed   (V=v1|v2, RATE=100)
	k6 run -q -e V=$(V) -e RATE=$(or $(RATE),100) k6/feed.js

k6-buy: ## Load: the on-sale rush for 1,000 tickets   (V=v1|v2, RATE=150)
	k6 run -q -e V=$(V) -e RATE=$(or $(RATE),150) k6/purchase.js
