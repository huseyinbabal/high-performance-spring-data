-- TicketRush schema. Almost no secondary indexes — the ones in indexes.sql are added in class,
-- after measuring why we need them.
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

-- Hibernate asks for 50 ids per round trip (allocationSize = 50) — that is what makes JDBC batching possible.
create sequence customer_seq start 300001  increment 50;
create sequence orders_seq   start 2000001 increment 50;
