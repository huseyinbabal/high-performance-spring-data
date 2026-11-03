-- TicketRush schema — including the indexes we earned in Session 1.
create extension if not exists pg_stat_statements;

create table venue (
    id       bigint primary key,
    name     text   not null,
    city     text   not null,
    capacity int    not null
);

create table event (
    id          bigint      primary key,
    name        text        not null,
    artist      text        not null,
    category    text        not null,
    description text        not null,
    venue_id    bigint      not null references venue (id),
    starts_at   timestamptz not null,
    status      text        not null
);

create table ticket_type (
    id       bigint         primary key,
    event_id bigint         not null references event (id),
    name     text           not null,
    price    numeric(10, 2) not null,
    capacity int            not null,
    sold     int            not null default 0
);
create index ticket_type_event_idx on ticket_type (event_id);
create index event_category_starts_idx on event (category, starts_at);

create table customer (
    id        bigint primary key,
    email     text   not null,
    full_name text   not null,
    city      text   not null
);

create table orders (
    id             bigint         primary key,
    customer_id    bigint         not null references customer (id),
    ticket_type_id bigint         not null references ticket_type (id),
    quantity       int            not null,
    total          numeric(10, 2) not null,
    status         text           not null,
    created_at     timestamptz    not null
);

create index orders_customer_created_idx on orders (customer_id, created_at desc);
create index orders_created_idx on orders (created_at);

-- Session 4: the transactional outbox. A business change and its event are written in ONE transaction.
create table outbox (
    id           bigint      primary key,
    topic        text        not null,
    message_key  text        not null,
    type         text        not null,
    payload      text        not null,
    created_at   timestamptz not null,
    published_at timestamptz
);
-- Partial index: it only contains the rows the relay is looking for, so it stays tiny
-- no matter how many published rows pile up behind it.
create index outbox_unpublished_idx on outbox (id) where published_at is null;
create sequence outbox_seq start 1 increment 50;

-- Hibernate asks for 50 ids per round trip (allocationSize = 50) — that is what makes JDBC batching possible.
create sequence customer_seq start 300001  increment 50;
create sequence orders_seq   start 2000001 increment 50;
