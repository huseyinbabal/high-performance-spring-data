# High-Performance Application Development with Spring Data — Demos

Demo code for the 5-session live training. Every session is a complete, runnable Spring Boot project built around **TicketRush**, a ticketing platform where *at 20:00 tickets go on sale and everyone arrives in the same second*.

**Stack:** Java 27 · Spring Boot 4.1 · Hibernate 7 · PostgreSQL 18 · Redis 8 · Elasticsearch 9 · Kafka 4 · k6

| # | Session | Folder | What gets fixed |
|---|---------|--------|-----------------|
| 1 | Spring Data JPA Performance Deep Dive | [`session-01-jpa-performance`](session-01-jpa-performance) | N+1, missing indexes, OFFSET pagination, row-by-row inserts, pool exhaustion + oversell |
| 2 | Caching & Distributed State with Redis | [`session-02-redis-caching`](session-02-redis-caching) | Repeated reads, cache stampede, the on-sale rush hitting PostgreSQL, distributed locks |
| 3 | Search & Analytics with Elasticsearch | [`session-03-elasticsearch-search`](session-03-elasticsearch-search) | `LIKE '%…%'` search, facets, deep paging, slow indexing, reindex downtime, keeping a CQRS read model in sync |
| 4 | Event-Driven Architecture with Kafka | [`session-04-kafka-event-driven`](session-04-kafka-event-driven) | Dual writes, transactional outbox, idempotent + batch consumers, slow work off the request, sagas, retries + dead-letter topics, CQRS projections |
| 5 | Async, Scaling & Production Readiness | [`session-05-production`](session-05-production) | Thread starvation (virtual threads), out-of-memory exports (streaming), read replica + lag, pool sizing, metrics/logs/traces, scaling on Kubernetes with Flux GitOps |

Each project is cumulative: session N starts from session N-1's *fixed* code and adds one technology. The code you are about to improve is under `/v1/...` and the improved version under `/v2/...`, so both can be measured side by side.

## Run a demo

Needs: Docker, JDK 27, [k6](https://k6.io), `make`.

```bash
cd session-01-jpa-performance
make            # list all targets
make up         # start the infrastructure (first start seeds ~1.6M rows, ~20 s)
make run        # start the app on :8080 (another terminal)
make events V=v1 && make events V=v2      # before / after
```

Run one session's infrastructure at a time — they share host ports (PostgreSQL 5440 — 5442 in session 4, Redis 6379, Elasticsearch 9200, Kibana 5601, Kafka 9092). In session 3, `make dsl T=search` prints any search as Kibana Dev Tools JSON. In session 4, `make lag` shows consumer lag and `make dlt` what landed on the dead-letter topic. `make down` removes containers and data.

### Session 5 on Kubernetes (kind + Flux)

```bash
cd session-05-production
make cluster-up       # 5-node kind cluster (pools: app ×2, database, monitoring) + Flux; Flux deploys the rest from this repo
make flux-status      # sources, layers (database → monitoring → apps), Helm releases
make nodes            # which pod runs in which pool
make who              # API on localhost:8088 · Grafana localhost:3000 (Prometheus, Loki, Tempo) · Prometheus localhost:9091
```

Pushing a change under `session-05-production/src` runs [`.github/workflows/session-05-image.yml`](.github/workflows/session-05-image.yml): it builds a multi-arch image to `ghcr.io/huseyinbabal/ticketrush`, commits the new tag to `deploy/apps/kustomization.yaml`, and Flux rolls it out. Data services and the monitoring stack run in the cluster, not on the host. Fork the repo? Change the URL in `deploy/flux/sync.yaml` and the image in `deploy/apps/kustomization.yaml`.
