# TicketRush · Session 2 — Redis caching & distributed state
# Usage: make <target>   (run `make` alone to list targets)

.DEFAULT_GOAL := help

PSQL  := docker compose exec -T postgres psql -U ticketrush -d ticketrush
REDIS := docker compose exec -T redis redis-cli
API   := localhost:8080
V     ?= v1
HEAD  := curl -s -o /dev/null -D -
JSON  := -H 'Content-Type: application/json'

help: ## List all targets
	@grep -E '^[a-zA-Z0-9_-]+:.*##' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

## --- Environment ---

up: ## Start Postgres (seeds ~1.6M rows, ~20 s) + Redis
	docker compose up -d --wait

down: ## Stop and delete everything
	docker compose down -v

run: ## Run the app on :8080   (SQL=debug prints every statement)
	SQL_LOG=$(or $(SQL),off) ./mvnw -q spring-boot:run

redis-cli: ## Open a redis-cli shell
	docker compose exec redis redis-cli

keys: ## Every key in Redis right now
	@$(REDIS) --scan | sort

flush: ## Empty Redis (cache + stock + holds)
	@$(REDIS) flushall

## --- 1 · Cache-aside ---

event: ## The event page — watch X-Query-Count go to 0   (V=v1|v2)
	@$(HEAD) '$(API)/$(V)/events/1' | grep -iE 'HTTP|x-query|x-elapsed'

cached-event: ## What is actually stored: the JSON and its TTL
	@$(REDIS) get event::1 | cut -c1-200 ; echo "ttl (s): $$($(REDIS) ttl event::1)"

price: ## Change a ticket price → the write evicts the cache entry   (PRICE=650)
	@curl -s -X PUT '$(API)/events/1/tickets/1/price' $(JSON) -d '{"price":$(or $(PRICE),650)}' -w 'HTTP %{http_code}\n'

## --- 2 · Stampede ---

trending: ## The trending block   (V=v1 no cache | v2 @Cacheable | v3 sync=true)
	@$(HEAD) '$(API)/$(V)/trending' | grep -iE 'HTTP|x-query|x-elapsed'

computations: ## How many times the aggregate really ran since the last reset
	@curl -s $(API)/trending/computations ; echo

computations-reset: ## Reset that counter (and drop both trending cache entries)
	@curl -s -X DELETE $(API)/trending/computations ; $(REDIS) del trending::top10 trending-sync::top10 > /dev/null

## --- 3 · Holds: distributed state with a TTL ---

hold: ## Hold N tickets of the hot event for 2 minutes   (N=2, CUSTOMER=7)
	@curl -s -X POST '$(API)/v2/holds' $(JSON) \
	  -d '{"customerId":$(or $(CUSTOMER),7),"ticketTypeId":1,"quantity":$(or $(N),2)}' -w '  [%{http_code}]\n'

confirm: ## Pay for a hold   (TOKEN=<token from make hold>)
	@curl -s -X POST '$(API)/v2/holds/$(TOKEN)/confirm' -w '  [%{http_code}]\n'

cancel: ## Give a hold back   (TOKEN=…)
	@curl -s -X DELETE '$(API)/v2/holds/$(TOKEN)' -w 'HTTP %{http_code}\n'

stock: ## Redis: remaining stock + open holds (with seconds until each expires)
	@echo "stock: $$($(REDIS) get 'tt:{1}:stock')"; \
	$(REDIS) eval "local t = redis.call('TIME')[1] local out = {} for i, v in ipairs(redis.call('ZRANGE', KEYS[1], 0, -1, 'WITHSCORES')) do if i % 2 == 1 then out[#out + 1] = v else out[#out] = out[#out] .. '  expires in ' .. math.floor(v / 1000 - t) .. ' s' end end return out" 1 'tt:{1}:holds'

spam: ## One customer asks for 8 holds in a row → 429 after the 5th
	@for i in 1 2 3 4 5 6 7 8; do curl -s -o /dev/null -X POST '$(API)/v2/holds' $(JSON) \
	  -d '{"customerId":99,"ticketTypeId":2,"quantity":1}' -w '%{http_code} '; done; echo

sold: ## PostgreSQL truth for the hot ticket: capacity vs counter vs paid orders
	@$(PSQL) -c "select t.capacity, t.sold as sold_counter, (select coalesce(sum(o.quantity), 0) from orders o where o.ticket_type_id = 1 and o.status = 'PAID') as tickets_really_sold from ticket_type t where t.id = 1"

reset-hot: ## Reset the hot ticket in PostgreSQL and Redis before the next run
	@$(PSQL) -qc "delete from orders where ticket_type_id = 1; update ticket_type set sold = 0 where id = 1"
	@$(REDIS) del 'tt:{1}:stock' 'tt:{1}:holds' > /dev/null

## --- 4 · A second instance: which lock still works? ---

run-2: ## Run a SECOND instance on :8082 (same database, same Redis)   (PORT2=8082)
	./mvnw -q spring-boot:run -Dspring-boot.run.arguments=--server.port=$(or $(PORT2),8082)

buy-locked: ## One purchase through the lock demo   (LOCK=none|jvm|redis|atomic)
	@curl -s -X POST '$(API)/lock-demo/orders?lock=$(or $(LOCK),redis)' $(JSON) \
	  -d '{"customerId":7,"ticketTypeId":3,"quantity":1}' -w '  [%{http_code} · %{time_total}s]\n'

k6-lock: ## Load: 200 buyers/s for 1,000 tickets   (LOCK=none|jvm|redis|atomic, PORTS=8080 or 8080,8082)
	@$(MAKE) -s lock-reset
	k6 run -q -e LOCK=$(or $(LOCK),none) -e PORTS=$(or $(PORTS),8080) -e RATE=$(or $(RATE),200) k6/lock.js
	@$(MAKE) -s lock-sold

lock-sold: ## Ticket type 3: capacity vs the sold counter vs tickets really sold
	@$(PSQL) -c "select t.capacity, t.sold as sold_counter, (select coalesce(sum(o.quantity), 0) from orders o where o.ticket_type_id = 3 and o.created_at > now() - interval '1 hour') as tickets_really_sold from ticket_type t where t.id = 3"

lock-reset: ## Reset ticket type 3 before a run
	@$(PSQL) -qc "delete from orders where ticket_type_id = 3; update ticket_type set sold = 0 where id = 3"

lock-watch: ## Who holds the lock right now, and for how much longer
	@echo "holder: $$($(REDIS) get lock:tt:3)   ttl (ms): $$($(REDIS) pttl lock:tt:3)"

## --- Measure ---

db-calls: ## pg_stat_statements: how many statements PostgreSQL executed since the last reset
	@$(PSQL) -c "select sum(calls) as statements, round(sum(total_exec_time)::numeric) as total_ms from pg_stat_statements where query not like '%pg_stat_statements%' and query !~* '^(begin|commit|set|show)'"

stats-reset: ## Reset pg_stat_statements
	@$(PSQL) -qc "select pg_stat_statements_reset()" > /dev/null

pool: ## HikariCP right now: active / idle / pending / timeouts
	@curl -s $(API)/actuator/prometheus | grep -E '^hikaricp_connections(_active|_idle|_pending|_timeout_total)'

k6-event: ## Load: the event page   (V=v1|v2, RATE=2000)
	k6 run -q -e V=$(V) -e RATE=$(or $(RATE),2000) k6/event-page.js

k6-trending: ## Load: the trending block   (V=v1|v2|v3, RATE=300)
	k6 run -q -e V=$(V) -e RATE=$(or $(RATE),300) k6/trending.js

k6-on-sale: ## Load: 1,000 buyers/s for 1,000 tickets   (V=v1|v2, RATE=1000)
	k6 run -q -e V=$(V) -e RATE=$(or $(RATE),1000) k6/on-sale.js
