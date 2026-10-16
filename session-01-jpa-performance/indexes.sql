-- Applied live with `make indexes` — AFTER we have seen the sequential scans.
-- (Lives outside db/ on purpose: db/ runs automatically on first start.)

-- Equality column first, then the sort column: the index returns rows already ordered,
-- so LIMIT 50 stops after 50 index entries.
create index if not exists event_category_starts_idx on event (category, starts_at);

-- Postgres does NOT index foreign keys for you. orders.customer_id had no index at all.
create index if not exists orders_customer_created_idx on orders (customer_id, created_at desc);

analyze;
