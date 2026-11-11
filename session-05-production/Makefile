# TicketRush · Session 5 — Async, scaling & production readiness
# Usage: make <target>   (run `make` alone to list targets)

.DEFAULT_GOAL := help

# ON=compose (default): the app on your laptop (`make run`) + docker compose.
# ON=k8s: the same targets against the kind cluster — `make lag ON=k8s`, `make db-load ON=k8s`, …
ON      ?= compose
KC      := kubectl --kubeconfig k8s/.kubeconfig
ifeq ($(ON),k8s)
PRIMARY := $(KC) -n database exec -i postgres-0 -- psql -U ticketrush -d ticketrush
REPLICA := $(KC) -n database exec -i postgres-replica-0 -- psql -U ticketrush -d ticketrush
REDIS   := $(KC) -n database exec -i redis-0 -- redis-cli
KAFKA   := $(KC) -n database exec -i kafka-0 -- /opt/kafka/bin
API     := localhost:8088
ES      := localhost:9200
GRAFANA := localhost:3000
PROM    := localhost:9091
else
PRIMARY := docker compose exec -T postgres psql -U ticketrush -d ticketrush
REPLICA := docker compose exec -T postgres-replica psql -U ticketrush -d ticketrush
REDIS   := docker compose exec -T redis redis-cli
KAFKA   := docker compose exec -T kafka /opt/kafka/bin
API     := localhost:8080
ES      := localhost:9200
GRAFANA := localhost:3001
PROM    := localhost:9095
endif
V       ?= v1
VT      ?= true
JSON    := -H 'Content-Type: application/json'
K8S     := localhost:8088
LOAD    := select coalesce(sum(calls) filter (where query ~* '^select'), 0) as reads, coalesce(sum(calls) filter (where query ~* '^(insert|update|delete)'), 0) as writes from pg_stat_statements where query !~* 'pg_stat|pg_is_in|outbox'

help: ## List all targets
	@grep -E '^[a-zA-Z0-9_-]+:.*##' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

## --- Environment ---

up: ## Start everything: Postgres primary + replica, Redis, Elasticsearch, Kafka, Prometheus, Grafana
	docker compose up -d --wait

down: ## Stop and delete everything
	docker compose down -v

run: ## Run the app   (VT=true|false virtual threads, HEAP=384m to cap the heap)
	VIRTUAL_THREADS=$(VT) ./mvnw -q spring-boot:run $(if $(HEAP),-Dspring-boot.run.jvmArguments=-Xmx$(HEAP),)

reindex: ## Build the search index once (100k events)
	@curl -s -X POST '$(API)/search/reindex' ; echo

## --- 1 · Threads ---

payment-slow: ## The payment provider degrades: 2 s per charge
	@curl -s -X PUT '$(API)/admin/payment/latency?ms=$(or $(MS),2000)' ; echo

payment-normal: ## Back to 150 ms
	@curl -s -X PUT '$(API)/admin/payment/latency?ms=150' ; echo

threads: ## How many platform threads the JVM has right now
	@curl -s $(API)/actuator/prometheus | grep -E '^jvm_threads_(live|peak)_threads' | sed 's/{[^}]*}//'

k6-threads: ## Load: 200 synchronous checkouts/s + 100 page views/s (run with VT=false, then VT=true)
	k6 run -q -e RATE=$(or $(RATE),200) k6/threads.js

## --- 2 · Streaming a big result ---

export: ## Export 1M orders as CSV   (V=v1 in memory | v2 streamed) — try it with `make run HEAP=384m`
	@curl -s -o /tmp/ticketrush-orders.csv -w 'HTTP %{http_code} · %{size_download} bytes · %{time_total} s\n' '$(API)/$(V)/orders/export'
	@echo "lines: $$(wc -l < /tmp/ticketrush-orders.csv)"

heap: ## JVM heap used / max, in MB
	@curl -s $(API)/actuator/prometheus | awk -F'} ' '/^jvm_memory_used_bytes\{.*area="heap"/ {u += $$2} /^jvm_memory_max_bytes\{.*area="heap"/ {m += $$2} END {printf "heap used %d MB of %d MB\n", u / 1048576, m / 1048576}'

## --- 3 · Read replica ---

db-load: ## Statements executed on the primary vs the replica since the last `make db-reset`
	@echo "primary: $$($(PRIMARY) -tAF ' reads, ' -c "$(LOAD)") writes"
	@echo "replica: $$($(REPLICA) -tAF ' reads, ' -c "$(LOAD)") writes"

db-reset: ## Reset pg_stat_statements on both nodes
	@$(PRIMARY) -qc "select pg_stat_statements_reset()" > /dev/null ; $(REPLICA) -qc "select pg_stat_statements_reset()" > /dev/null

replica-lag: ## How far the replica is behind the primary
	@$(PRIMARY) -c "select client_addr, state, coalesce(replay_lag, interval '0') as replay_lag, pg_wal_lsn_diff(sent_lsn, replay_lsn) as bytes_behind from pg_stat_replication"

pool: ## HikariCP per pool: active / pending / timeouts
	@curl -s $(API)/actuator/prometheus | grep -E '^hikaricp_connections(_active|_pending|_timeout_total)' | sed 's/application="ticketrush",//'

sql-log: ## PostgreSQL logs every statement AND every cursor fetch, on both nodes   (S=on|off — turn it off before a load test)
	@$(PRIMARY) -qtAc "alter system set log_min_duration_statement = $(if $(filter off,$(S)),-1,0)" -c "select pg_reload_conf()" > /dev/null
	@$(REPLICA) -qtAc "alter system set log_min_duration_statement = $(if $(filter off,$(S)),-1,0)" -c "select pg_reload_conf()" > /dev/null
	@echo "PostgreSQL statement log: $(if $(filter off,$(S)),off,ON — make pg-log to follow it)"

pg-log: ## Follow the replica's log — run `make export V=v2` in another terminal: 1 execute, then ~1,000 "execute fetch from"
ifeq ($(ON),k8s)
	@$(KC) -n database logs -f postgres-replica-0 -c postgres --tail=5 | cut -c1-180
else
	@docker compose logs -f --tail=5 --no-log-prefix postgres-replica | cut -c1-180
endif

## --- 4 · Observability ---

grafana: ## Open the dashboard (compose: localhost:3001 · ON=k8s: localhost:3000)
	@open 'http://$(GRAFANA)/d/ticketrush' 2>/dev/null || echo 'http://$(GRAFANA)/d/ticketrush'

prometheus: ## Open Prometheus targets (compose: localhost:9095 · ON=k8s: localhost:9091)
	@open 'http://$(PROM)/targets' 2>/dev/null || echo 'http://$(PROM)/targets'

metrics: ## Our own metrics, straight from the app
	@curl -s $(API)/actuator/prometheus | grep -E '^(ticketrush_outbox|ticketrush_sql_statements_(sum|count)|ticketrush_search_seconds_(sum|count)|cache_gets_total)' | sed 's/application="ticketrush",//'

health: ## Liveness, readiness, and what the app depends on
	@curl -s $(API)/actuator/health | python3 -c "import json,sys; d=json.load(sys.stdin); print('overall:', d['status']); [print('  %-16s %s' % (k, v['status'])) for k, v in d.get('components', {}).items()]"

lag: ## Kafka consumer lag per group
	@$(KAFKA)/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups 2>/dev/null \
	  | awk '$$1 ~ /^(search-indexer|cache-invalidator|payments|sales-projection)$$/ { lag[$$1] += $$6 } END { for (g in lag) printf "  %-18s lag %d\n", g, lag[g] }' | sort

compare: ## One random event's cheapest price in PostgreSQL, Elasticsearch and the cache   (EVENT=1)
	@echo "postgres:      $$($(PRIMARY) -tAc 'select min(price) from ticket_type where event_id = $(or $(EVENT),1)')"
	@echo "elasticsearch: $$(curl -s '$(ES)/events/_doc/$(or $(EVENT),1)?_source=minPrice' | python3 -c "import json,sys; print(json.load(sys.stdin)['_source']['minPrice'])")"
	@echo "redis cache:   $$(curl -s '$(API)/events/$(or $(EVENT),1)' | python3 -c "import json,sys; print(min(t['price'] for t in json.load(sys.stdin)['event']['tickets']))")"

## --- 5 · Kubernetes: kind + Flux (GitOps) ---
# 5 nodes in 3 pools: app ×2 · database ×1 · monitoring ×1 (deploy/kind/cluster.yaml).
# Flux pulls deploy/ from GitHub: database + monitoring first, then the app. GitHub Actions builds the image.
# The cluster has its OWN kubeconfig (k8s/.kubeconfig). Your default kubectl context is never read or changed.

kind-up: ## Create the 5-node cluster `ticketrush` (API localhost:8088 · Grafana :3000 · Prometheus :9091)
	kind create cluster --config deploy/kind/cluster.yaml --kubeconfig k8s/.kubeconfig
	$(KC) get nodes -L pool

flux-up: ## Install Flux and point it at the Git repository — from here on Git is the source of truth
	flux install --kubeconfig k8s/.kubeconfig
	$(KC) apply -f deploy/flux/sync.yaml

cluster-up: kind-up flux-up flux-wait ## Everything: cluster, Flux, database, monitoring, app (~10 min the first time)

flux-wait: ## Wait until every layer is Ready (database → monitoring → apps)
	$(KC) -n flux-system wait kustomization/cluster --for=condition=Ready --timeout=5m
	$(KC) -n flux-system wait kustomization/database kustomization/monitoring --for=condition=Ready --timeout=20m
	$(KC) -n flux-system wait kustomization/apps --for=condition=Ready --timeout=10m

flux-status: ## What Flux is doing: sources, layers, Helm releases
	@flux --kubeconfig k8s/.kubeconfig get sources git
	@flux --kubeconfig k8s/.kubeconfig get kustomizations
	@flux --kubeconfig k8s/.kubeconfig get helmreleases -A

flux-sync: ## Don't wait for the 1-minute poll: fetch Git now and apply
	flux --kubeconfig k8s/.kubeconfig reconcile kustomization apps --with-source

image: ## The image the cluster runs right now (written into Git by GitHub Actions)
	@$(KC) -n ticketrush get deploy -o custom-columns='DEPLOYMENT:.metadata.name,IMAGE:.spec.template.spec.containers[0].image,READY:.status.readyReplicas'

nodes: ## Nodes and their pools, and what runs on each
	@$(KC) get nodes -L pool
	@$(KC) get pods -A -o wide --no-headers | awk '$$1 ~ /^(ticketrush|database|monitoring)$$/ {printf "  %-20s %-11s %s\n", $$8, $$1, $$2}' | sort

kind-image: ## Build the image locally and load it into kind (offline fallback when GitHub Actions is not an option)
	./mvnw -q -DskipTests package
	docker build -q -t ghcr.io/huseyinbabal/ticketrush:dev .
	kind load docker-image ghcr.io/huseyinbabal/ticketrush:dev --name ticketrush

kind-down: ## Delete the cluster
	kind delete cluster --name ticketrush ; rm -f k8s/.kubeconfig

pods: ## The app pods and what they are doing
	@$(KC) -n ticketrush get pods -o wide

who: ## 6 requests through the Service — which pod answered each?
	@for i in 1 2 3 4 5 6; do curl -s -o /dev/null -D - '$(K8S)/events/1' | grep -i x-instance | tr -d '\r'; done

scale-api: ## Scale the API   (N=3)
	$(KC) -n ticketrush scale deploy/ticketrush-api --replicas=$(or $(N),3)

scale-workers: ## Scale the consumers   (N=3) — then watch `make lag`
	$(KC) -n ticketrush scale deploy/ticketrush-worker --replicas=$(or $(N),3)
	$(KC) -n ticketrush rollout status deploy/ticketrush-worker --timeout=180s

worker-logs: ## Follow the worker pods (partition assignments show the rebalance)
	$(KC) -n ticketrush logs -l role=worker -f --tail=20 --prefix

k8s-spam: ## One customer, 8 hold requests spread over 3 pods → still 201 ×5, then 429
	@for i in 1 2 3 4 5 6 7 8; do curl -s -o /dev/null -X POST '$(K8S)/holds' $(JSON) \
	  -d '{"customerId":99,"ticketTypeId":2,"quantity":1}' -w '%{http_code} '; done; echo

sold: ## Hot ticket: capacity vs the counter vs orders
	@$(PRIMARY) -c "select t.capacity, t.sold as sold_counter, (select coalesce(sum(o.quantity), 0) from orders o where o.ticket_type_id = 1 and o.status in ('PENDING', 'PAID')) as tickets_in_orders from ticket_type t where t.id = 1"

reset-hot: ## Reset the hot ticket in PostgreSQL and Redis
	@$(PRIMARY) -qc "delete from orders where ticket_type_id = 1; update ticket_type set sold = 0 where id = 1"
	@$(REDIS) del 'tt:{1}:stock' 'tt:{1}:holds' > /dev/null

k6-on-sale-k8s: ## Load: 1,000 buyers/s for 1,000 tickets, across all API pods
	k6 run -q -e BASE=http://$(K8S) k6/on-sale.js

k6-showtime-k8s: ## The final load test against the cluster
	k6 run -q -e BASE=http://$(K8S) -e SCALE=$(or $(SCALE),1) k6/showtime.js

trace: ## One checkout, traced end to end (forced sampling): HTTP → SQL → outbox → Kafka → payment worker → SQL
	@TP="00-$$(openssl rand -hex 16)-$$(openssl rand -hex 8)-01"; TRACE=$$(echo $$TP | cut -d- -f2); \
	TOKEN=$$(curl -s -X POST '$(API)/holds' $(JSON) -H "traceparent: $$TP" \
	  -d "{\"customerId\":$$RANDOM,\"ticketTypeId\":$(or $(TT),777),\"quantity\":1}" | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])"); \
	curl -s -X POST "$(API)/holds/$$TOKEN/confirm" -H "traceparent: $$TP"; echo; \
	echo "trace $$TRACE"; \
	echo "  → http://$(GRAFANA)/explore?schemaVersion=1&panes=%7B%22t%22%3A%7B%22datasource%22%3A%22tempo%22%2C%22queries%22%3A%5B%7B%22refId%22%3A%22A%22%2C%22datasource%22%3A%7B%22type%22%3A%22tempo%22%2C%22uid%22%3A%22tempo%22%7D%2C%22queryType%22%3A%22traceql%22%2C%22query%22%3A%22$$TRACE%22%7D%5D%7D%7D"

## --- The final load test ---

k6-showtime: ## Everything at once for 60 s: listing, event page, search, trending, checkout, price changes   (SCALE=1)
	k6 run -q -e SCALE=$(or $(SCALE),1) k6/showtime.js
