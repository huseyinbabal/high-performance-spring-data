import http from 'k6/http';
import { check } from 'k6';

// The search box. Same words, two engines.
//   v1 → PostgreSQL LIKE '%q%' (3 full scans per request) · v2 → Elasticsearch · v3 → v2 + cacheable date filter
const V = __ENV.V || 'v1';
const BASE = __ENV.BASE || 'http://localhost:8080';
const QUERIES = ['akustik', 'senfonik', 'turne', 'kuzgun', 'orchestra', 'acoustic', 'poyraz', 'veda',
  'backstage', 'festival', 'arena', 'shuttle', 'gece', 'anniversary'];

export const options = {
  scenarios: {
    search: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 100),
      timeUnit: '1s',
      duration: __ENV.DURATION || '20s',
      preAllocatedVUs: 100,
      maxVUs: 600,
    },
  },
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const q = QUERIES[Math.floor(Math.random() * QUERIES.length)];
  const res = http.get(`${BASE}/${V}/search?q=${q}`);
  check(res, { 'status 200': (r) => r.status === 200 });
}
