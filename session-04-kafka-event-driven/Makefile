# TicketRush · Session 4 — Event streaming with Kafka & event-driven design
# Usage: make <target>   (run `make` alone to list targets)

.DEFAULT_GOAL := help

# one line per dead-lettered record: the group that gave up, the payload, the root cause.
# The stack-trace header spans many lines, so records are split where each one starts.
export DLT_PY := import sys, re; \
  recs = sys.stdin.buffer.read().decode('utf-8', 'replace').split('kafka_dlt-exception-fqcn:')[1:]; \
  g = lambda r: (lambda m: m.group(1) if m else '?')(re.search(r'original-consumer-group:([\w-]+)', r)); \
  c = lambda r: (re.findall(r'Caused by: ([\w.$$]+)', r) or ['?'])[-1].rsplit('.', 1)[-1]; \
  [print('  %-18s %-20s <- %s' % (g(r), r.rsplit(chr(9), 1)[-1].strip().splitlines()[0], c(r))) for r in recs]

PSQL  := docker compose exec -T postgres psql -U ticketrush -d ticketrush
REDIS := docker compose exec -T redis redis-cli
KAFKA := docker compose exec -T kafka /opt/kafka/bin
API   := localhost:8080
ES    := localhost:9200
V     ?= v1
JSON  := -H 'Content-Type: application/json'

help: ## List all targets
	@grep -E '^[a-zA-Z0-9_-]+:.*##' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

## --- Environment ---

up: ## Start Postgres + Redis + Elasticsearch + Kafka
	docker compose up -d --wait

down: ## Stop and delete everything
	docker compose down -v

run: ## Run the app on :8080
	SQL_LOG=$(or $(SQL),off) ./mvnw -q spring-boot:run | tee /tmp/ticketrush.log

reindex: ## Build the search index once (100k events)
	@curl -s -X POST '$(API)/v2/search/reindex' ; echo

## --- 1 · Dual write vs outbox ---

price: ## Change event 1's cheapest ticket price   (V=v1 dual write | v2 outbox, PRICE=120)
	@curl -s -X PUT '$(API)/$(V)/events/1/tickets/3/price' $(JSON) -d '{"price":$(or $(PRICE),120)}' -w 'HTTP %{http_code}\n'

compare: ## Event 1's cheapest price in all three systems
	@echo "postgres:      $$($(PSQL) -tAc 'select min(price) from ticket_type where event_id = 1')"
	@echo "elasticsearch: $$(curl -s '$(ES)/events/_doc/1?_source=minPrice' | python3 -c "import json,sys; print(json.load(sys.stdin)['_source']['minPrice'])")"
	@echo "redis cache:   $$(curl -s '$(API)/events/1' | python3 -c "import json,sys; print(min(t['price'] for t in json.load(sys.stdin)['event']['tickets']))")"

es-stop: ## Take Elasticsearch down
	docker compose stop elasticsearch

es-start: ## Bring Elasticsearch back — then watch `make lag` and `make compare`
	docker compose start elasticsearch && docker compose up -d --wait elasticsearch

outbox: ## The outbox table: last 5 rows + how many are still unpublished
	@$(PSQL) -c "select id, topic, message_key, type, payload, published_at is not null as published from outbox order by id desc limit 5"
	@$(PSQL) -tAc "select 'unpublished: ' || count(*) from outbox where published_at is null"

republish: ## Send the last N outbox rows AGAIN — a duplicate delivery on demand   (N=200, TOPIC=ticketrush.orders)
	@$(PSQL) -tAc "update outbox set published_at = null where id in (select id from outbox \
	  where topic = '$(or $(TOPIC),ticketrush.orders)' order by id desc limit $(or $(N),200))" | sed 's/^/  re-queued: /'

## --- 2 · Kafka ---

topics: ## Topics and their partitions
	@$(KAFKA)/kafka-topics.sh --bootstrap-server localhost:9092 --describe | grep -E 'Topic: ticketrush' | grep PartitionCount

lag: ## Consumer lag per group — the one metric that tells you whether consumers keep up
	@# a group that never committed shows CURRENT-OFFSET "-": then the whole log is its lag
	@$(KAFKA)/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups 2>/dev/null \
	  | awk '$$1 ~ /^(search-indexer|cache-invalidator|payments|sales-projection)$$/ { lag[$$1] += ($$4 == "-" ? $$5 : $$6) }  END { for (g in lag) printf "  %-18s lag %d\n", g, lag[g] }' | sort

tail: ## Print messages as they arrive   (TOPIC=ticketrush.events)
	docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
	  --topic $(or $(TOPIC),ticketrush.events) --property print.key=true --property print.headers=true

resync: ## Queue N events for re-indexing through the outbox   (N=5000)
	@curl -s -X POST '$(API)/admin/events/resync?from=2&count=$(or $(N),5000)' ; echo

indexer-log: ## How many messages became how many bulk requests
	@grep 'search-indexer:' $(or $(LOG),/tmp/ticketrush.log) | tail -15 | sed 's/.*search-indexer: //'

poison: ## Publish a malformed message for event 1 — then `make price V=v2` and `make compare`
	@echo '1|{"eventId":"oops"}' | $(KAFKA)/kafka-console-producer.sh --bootstrap-server localhost:9092 \
	  --topic ticketrush.events --property parse.key=true --property key.separator='|'
	@echo "published {\"eventId\":\"oops\"} with key 1 to ticketrush.events"

dlt: ## What ended up on the dead-letter topic: which group gave up, on what, and why
	@$(KAFKA)/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic ticketrush.events-dlt \
	  --from-beginning --timeout-ms 8000 --property print.headers=true 2>/dev/null \
	  | python3 -c "$$DLT_PY"

## --- 3 · Async purchase ---

hold: ## Hold 1 ticket   (CUSTOMER=7, TICKET=2)
	@curl -s -X POST '$(API)/holds' $(JSON) \
	  -d '{"customerId":$(or $(CUSTOMER),7),"ticketTypeId":$(or $(TICKET),2),"quantity":1}' -w '  [%{http_code}]\n'

confirm: ## Confirm a hold   (V=v1 waits for payment | v2 returns 202, TOKEN=…)
	@curl -s -X POST '$(API)/$(V)/holds/$(TOKEN)/confirm' -w '  [%{http_code} · %{time_total}s]\n'

status: ## Order status   (ORDER=…)
	@curl -s '$(API)/orders/$(ORDER)/status' ; echo

payment-slow: ## The payment provider degrades: 2 s per charge
	@curl -s -X PUT '$(API)/admin/payment/latency?ms=$(or $(MS),2000)' ; echo

payment-normal: ## Back to 150 ms
	@curl -s -X PUT '$(API)/admin/payment/latency?ms=150' ; echo

pending: ## Orders still waiting for payment
	@$(PSQL) -tAc "select 'pending orders: ' || count(*) from orders where status = 'PENDING'"

## --- 4 · CQRS projection ---

trending: ## Trending block   (V=v1 SQL aggregate + cache | v2 Redis projection fed by Kafka)
	@curl -s -o /tmp/ticketrush-trending.json -D - '$(API)/$(V)/trending' | grep -iE 'x-query|x-elapsed'
	@python3 -c "import json; [print('  %5d  %s' % (e['ticketsSold'], e['name'])) for e in json.load(open('/tmp/ticketrush-trending.json'))['events'][:5]]"

## --- Measure ---

k6-checkout: ## Load: 200 checkouts/s + 100 page views/s   (V=v1|v2)
	k6 run -q -e V=$(V) -e RATE=$(or $(RATE),200) k6/checkout.js
