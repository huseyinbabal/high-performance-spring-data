# High-Performance Application Development with Spring Data — Demos

Demo code for the 5-session live training. Every session is a complete, runnable Spring Boot project built around **TicketRush**, a ticketing platform where *at 20:00 tickets go on sale and everyone arrives in the same second*.

**Stack:** Java 27 · Spring Boot 4.1 · Hibernate 7 · PostgreSQL 18 · Redis 8 · k6

| # | Session | Folder | What gets fixed |
|---|---------|--------|-----------------|
| 1 | Spring Data JPA Performance Deep Dive | [`session-01-jpa-performance`](session-01-jpa-performance) | N+1, missing indexes, OFFSET pagination, row-by-row inserts, pool exhaustion + oversell |
| 2 | Caching & Distributed State with Redis | [`session-02-redis-caching`](session-02-redis-caching) | Repeated reads, cache stampede, the on-sale rush hitting PostgreSQL, distributed locks |

The remaining sessions are added after each class.

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

Run one session's infrastructure at a time — they share host ports (PostgreSQL 5440, Redis 6379). `make down` removes containers and data.
