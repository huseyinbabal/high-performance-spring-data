import http from 'k6/http';
import { check } from 'k6';

// "Upcoming events" — the hottest read of the platform.
//   k6 run -e V=v1 -e RATE=300 k6/browse.js
// constant-arrival-rate keeps sending RATE req/s even when the app slows down,
// so a struggling app cannot hide behind fewer requests.
const V = __ENV.V || 'v1';
const BASE = __ENV.BASE || 'http://localhost:8080';
const CATEGORIES = ['rock', 'pop', 'jazz', 'electronic', 'classical', 'theatre', 'standup', 'festival'];

export const options = {
  scenarios: {
    browse: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 300),
      timeUnit: '1s',
      duration: __ENV.DURATION || '20s',
      preAllocatedVUs: 100,
      maxVUs: 400,
    },
  },
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const category = CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)];
  const res = http.get(`${BASE}/${V}/events?category=${category}&size=50`);
  check(res, { 'status 200': (r) => r.status === 200 });
}
