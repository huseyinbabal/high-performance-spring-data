import http from 'k6/http';
import { check } from 'k6';

// The final exam: everything the platform does, at once, for one minute.
// Open Grafana (make grafana) before you start it.
const BASE = __ENV.BASE || 'http://localhost:8080';
const JSON_HEADERS = { 'Content-Type': 'application/json' };
const SCALE = Number(__ENV.SCALE || 1);
const DURATION = __ENV.DURATION || '60s';
const CATEGORIES = ['rock', 'pop', 'jazz', 'electronic', 'classical', 'theatre', 'standup', 'festival'];
const QUERIES = ['akustik', 'rock istanbul', 'senfonik izmir', 'kuzgn', 'orchestra', 'veda turnesi', 'ruzgari', 'festival ankara'];

function scenario(exec, rate, vus) {
  return { executor: 'constant-arrival-rate', exec, rate: Math.ceil(rate * SCALE), timeUnit: '1s', duration: DURATION,
           preAllocatedVUs: vus, maxVUs: vus * 10 };
}

export const options = {
  scenarios: {
    listing: scenario('listing', 300, 50),      // PostgreSQL replica
    eventPage: scenario('eventPage', 800, 100), // Redis cache + counters
    search: scenario('search', 200, 50),        // Elasticsearch
    trending: scenario('trending', 100, 20),    // Redis projection
    checkout: scenario('checkout', 150, 100),   // Redis hold → PostgreSQL primary → outbox → Kafka
    reprice: scenario('reprice', 5, 5),         // PostgreSQL primary → outbox → Kafka → Redis + Elasticsearch
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:listing}': ['p(95)<50'],
    'http_req_duration{name:eventPage}': ['p(95)<50'],
    'http_req_duration{name:search}': ['p(95)<50'],
    'http_req_duration{name:trending}': ['p(95)<50'],
    'http_req_duration{name:confirm}': ['p(95)<50'],
    'http_req_duration{name:reprice}': ['p(95)<50'],
  },
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

const pick = (list) => list[Math.floor(Math.random() * list.length)];
const ok = (res) => check(res, { 'ok': (r) => r.status >= 200 && r.status < 300 });

export function listing() {
  ok(http.get(`${BASE}/events?category=${pick(CATEGORIES)}&size=50`, { tags: { name: 'listing' } }));
}

export function eventPage() {
  const id = Math.random() < 0.6 ? 1 : 2 + Math.floor(Math.random() * 500);
  ok(http.get(`${BASE}/events/${id}`, { tags: { name: 'eventPage' } }));
}

export function search() {
  ok(http.get(`${BASE}/search?q=${encodeURIComponent(pick(QUERIES))}`, { tags: { name: 'search' } }));
}

export function trending() {
  ok(http.get(`${BASE}/trending`, { tags: { name: 'trending' } }));
}

export function checkout() {
  const body = JSON.stringify({
    customerId: 1 + Math.floor(Math.random() * 200000),
    ticketTypeId: 4 + Math.floor(Math.random() * 6000),
    quantity: 1 + Math.floor(Math.random() * 2),
  });
  const hold = http.post(`${BASE}/holds`, body, { headers: JSON_HEADERS, tags: { name: 'hold' } });
  if (hold.status !== 201) return;
  ok(http.post(`${BASE}/holds/${hold.json('token')}/confirm`, null, { tags: { name: 'confirm' } }));
}

export function reprice() {
  const event = 2 + Math.floor(Math.random() * 500);
  const price = (150 + Math.random() * 400).toFixed(2);
  ok(http.put(`${BASE}/events/${event}/tickets/${event * 3}/price`, JSON.stringify({ price }),
    { headers: JSON_HEADERS, tags: { name: 'reprice' } }));
}
