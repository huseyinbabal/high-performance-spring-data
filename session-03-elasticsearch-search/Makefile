# TicketRush · Session 3 — Search & analytics with Elasticsearch
# Usage: make <target>   (run `make` alone to list targets)

.DEFAULT_GOAL := help

PSQL  := docker compose exec -T postgres psql -U ticketrush -d ticketrush
REDIS := docker compose exec -T redis redis-cli
API   := localhost:8080
ES    := localhost:9200
V     ?= v1
Q     ?= akustik
JSON  := -H 'Content-Type: application/json'
# _profile → one line per shard: query / collector / aggregation / fetch time
export PROFILE := import json,sys; d=json.load(sys.stdin); print('took: %d ms   hits: %s' % (d['took'], d['hits']['total']['value']))
PROFILE += ; ms=lambda ns: '%.2f ms' % (ns/1e6)
PROFILE += ; [print('shard %s  query %s · collect %s · aggregations %s · fetch %s' % (s['id'].split('][')[-1].rstrip(']'), ms(sum(q['time_in_nanos'] for x in s['searches'] for q in x['query'])), ms(sum(c['time_in_nanos'] for x in s['searches'] for c in x['collector'])), ms(sum(a['time_in_nanos'] for a in s['aggregations'])), ms(s.get('fetch',{}).get('time_in_nanos',0)))) for s in d['profile']['shards']]
PROFILE += ; q=d['profile']['shards'][0]['searches'][0]['query'][0]; [print('  %-14s %s  %s' % (c['type'], ms(c['time_in_nanos']), c['description'][:70])) for c in q.get('children',[])]
# _explain → the score tree, three levels deep
export EXPLAIN := import json,sys; d=json.load(sys.stdin); print('matched:', d['matched'])
EXPLAIN += ; p=lambda e, n=0: n < 3 and (print('  ' * n + '%6.2f  %s' % (e['value'], e['description'][:76])) or [p(x, n + 1) for x in e.get('details', [])])
EXPLAIN += ; d['matched'] and p(d['explanation'])
# total + first 5 hits + facets, without needing jq
SHOW  := python3 -c "import json,sys; d=json.load(sys.stdin); print('total:', d['total']); [print('  %-44s %-10s %s' % (h['name'], h['city'], h['fromPrice'])) for h in d['hits'][:5]]; [print(k + ':', ', '.join('%s (%d)' % (f['key'], f['count']) for f in v[:5])) for k, v in d['facets'].items()]"

help: ## List all targets
	@grep -E '^[a-zA-Z0-9_-]+:.*##' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

## --- Environment ---

up: ## Start Postgres (seeds ~1.6M rows) + Redis + Elasticsearch + Kibana
	docker compose up -d --wait

down: ## Stop and delete everything
	docker compose down -v

kibana: ## Open Kibana Dev Tools (http://localhost:5601)
	open http://localhost:5601/app/dev_tools#/console

run: ## Run the app on :8080 (creates the `events` index if it is missing)
	SQL_LOG=$(or $(SQL),off) ./mvnw -q spring-boot:run

## --- 1 · Filling the index ---

index-slow: ## v1: index 2,000 events with save() per document
	@curl -s -X POST '$(API)/v1/search/reindex?limit=$(or $(N),2000)' ; echo

reindex: ## v2: build a new index (100k events) with _bulk, swap the alias   (SHARDS=1 BULK=2000 REFRESH=-1 SWAP=true)
	@curl -s -X POST '$(API)/v2/search/reindex?shards=$(or $(SHARDS),1)&bulk=$(or $(BULK),2000)&refresh=$(or $(REFRESH),-1)&swap=$(or $(SWAP),true)' ; echo

index-info: ## Which index the `events` alias points to: docs, shards, size on disk
	@curl -s '$(ES)/_cat/aliases/events?v&h=alias,index'
	@curl -s '$(ES)/_cat/indices/events?v&h=index,health,pri,rep,docs.count,store.size'

segments: ## The Lucene segments of the index — what refresh creates and merges clean up
	@curl -s '$(ES)/_cat/segments/events?v&h=index,shard,segment,docs.count,size,searchable'

mapping: ## The index mapping as Elasticsearch sees it
	@curl -s '$(ES)/events/_mapping?pretty'

analyze: ## How a text becomes tokens   (TEXT="Kuzey Rüzgârı İstanbul")
	@curl -s '$(ES)/events/_analyze' $(JSON) -d '{"analyzer":"folded","text":"$(or $(TEXT),Kuzey Rüzgârı İstanbul)"}' \
	  | python3 -c "import json,sys; print(' · '.join(t['token'] for t in json.load(sys.stdin)['tokens']))"

## --- 2 · Searching ---

search: ## Search   (V=v1 PostgreSQL | v2 Elasticsearch | v3 + cacheable date filter, Q="rock istanbul")
	@curl -s -G '$(API)/$(V)/search' --data-urlencode 'q=$(Q)' -o /tmp/ticketrush-search.json -D - | grep -iE 'x-query|x-elapsed'
	@$(SHOW) < /tmp/ticketrush-search.json

filter: ## Full-text + filters   (Q=..., CITY=İstanbul, CATEGORY=rock, MAX=300)
	@curl -s -G '$(API)/v2/search' --data-urlencode 'q=$(Q)' --data-urlencode 'city=$(or $(CITY),İstanbul)' \
	  --data-urlencode 'category=$(or $(CATEGORY),rock)' --data-urlencode 'maxPrice=$(or $(MAX),300)' | $(SHOW)

page: ## v1: page N of the upcoming events with from + size   (PAGE=499 — try 500)
	@curl -s '$(API)/v1/events/browse?page=$(or $(PAGE),0)' -D - -o /tmp/ticketrush-page.json | grep -iE 'x-elapsed|^HTTP' ; head -c 200 /tmp/ticketrush-page.json; echo

page-after: ## v2: the next 20 after a cursor   (AFTER=<the "next" value of the previous page>)
	@curl -s '$(API)/v2/events/browse$(if $(AFTER),?after=$(AFTER))' -D - -o /tmp/ticketrush-page.json | grep -iE 'x-elapsed' ; head -c 300 /tmp/ticketrush-page.json; echo

suggest: ## Search-as-you-type   (Q="gece yol")
	@curl -s -G '$(API)/v2/search/suggest' --data-urlencode 'q=$(Q)' ; echo

calendar: ## Analytics: upcoming events per month + average entry price   (CITY=İzmir)
	@curl -s -G '$(API)/analytics/calendar' --data-urlencode 'city=$(or $(CITY),İzmir)' | python3 -m json.tool | head -30

explain: ## Why this event scored what it did: BM25, term by term   (ID=29643 Q="kuzgn akustik")
	@curl -s '$(ES)/events/_explain/$(or $(ID),29643)' $(JSON) -d '{"query":{"bool":{"must":{"match":{"all":{"query":"$(Q)","operator":"and","fuzziness":"AUTO"}}},"should":{"multi_match":{"query":"$(Q)","fields":["name^3","artist^3"]}}}}}' \
	  | python3 -c "$$EXPLAIN"

profile: ## Where the milliseconds go inside Elasticsearch: the /v2 search query, profiled   (Q="rock istanbul")
	@curl -s '$(ES)/events/_search?request_cache=false' $(JSON) -d '{"profile":true,"size":20,"query":{"bool":{"must":{"match":{"all":{"query":"$(Q)","operator":"and","fuzziness":"AUTO"}}},"should":{"multi_match":{"query":"$(Q)","fields":["name^3","artist^3"]}},"filter":{"range":{"startsAt":{"gte":"now"}}}}},"aggs":{"city":{"terms":{"field":"city"}},"category":{"terms":{"field":"category"}}}}' \
	  | python3 -c "$$PROFILE"

cache-stats: ## Elasticsearch's own caches: filter (query) cache and shard request cache
	@curl -s '$(ES)/events/_stats/query_cache,request_cache' | python3 -c "import json,sys; t=json.load(sys.stdin)['_all']['total']; q=t['query_cache']; r=t['request_cache']; print('query cache   (filters):   %6d hits  %6d misses  %5d entries' % (q['hit_count'], q['miss_count'], q['cache_count'])); print('request cache (size=0):    %6d hits  %6d misses' % (r['hit_count'], r['miss_count']))"

cache-clear: ## Empty both caches (to measure from cold)
	@curl -s -X POST '$(ES)/events/_cache/clear' > /dev/null && echo cleared

dsl: ## The JSON a target sends to Elasticsearch, ready for Kibana Dev Tools (copied to clipboard)   (T=search|filter|page|page-after|suggest|calendar|explain|profile|analyze + that target's vars)
	@python3 kibana/dsl.py $(or $(T),search) | tee /dev/stderr | pbcopy

explain-like: ## Why v1 is slow: the plan of a LIKE '%…%' search
	@$(PSQL) -c "explain (analyze, buffers) select e.id from event e join venue v on v.id = e.venue_id where (lower(e.name) like '%akustik%' or lower(e.artist) like '%akustik%' or lower(e.description) like '%akustik%' or lower(v.city) like '%akustik%') and e.starts_at > now() order by e.starts_at limit 20"

## --- 3 · Keeping the index in sync (and how it breaks) ---

price: ## Change the hot event's VIP price in PostgreSQL → the app re-indexes that event   (PRICE=120)
	@curl -s -X PUT '$(API)/events/1/tickets/3/price' $(JSON) -d '{"price":$(or $(PRICE),120)}' -w 'HTTP %{http_code}\n'

compare: ## Event 1's cheapest price: PostgreSQL vs Elasticsearch
	@echo "postgres:      $$($(PSQL) -tAc 'select min(price) from ticket_type where event_id = 1')"
	@echo "elasticsearch: $$(curl -s '$(ES)/events/_doc/1?_source=minPrice' | python3 -c "import json,sys; print(json.load(sys.stdin)['_source']['minPrice'])")"

es-stop: ## Take Elasticsearch down (then run `make price` and watch the two drift apart)
	docker compose stop elasticsearch

es-start: ## Bring Elasticsearch back
	docker compose start elasticsearch && docker compose up -d --wait elasticsearch

## --- Measure ---

k6-search: ## Load: the search box   (V=v1|v2|v3, RATE=100, DURATION=20s)
	k6 run -q -e V=$(V) -e RATE=$(or $(RATE),100) -e DURATION=$(or $(DURATION),20s) k6/search.js

k6-paging: ## Load: deep infinite scroll   (V=v1 from+size | v2 search_after, RATE=500)
	k6 run -q -e V=$(V) -e RATE=$(or $(RATE),500) k6/paging.js
