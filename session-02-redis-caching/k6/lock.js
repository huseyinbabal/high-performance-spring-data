import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 200 buyers a second for the 1,000 tickets of ticket type 3 — spread over one or two app instances.
//   k6 run -e LOCK=jvm   -e PORTS=8080      k6/lock.js   → correct
//   k6 run -e LOCK=jvm   -e PORTS=8080,8082 k6/lock.js   → oversold: the lock is per JVM
//   k6 run -e LOCK=redis -e PORTS=8080,8082 k6/lock.js   → correct again
const LOCK = __ENV.LOCK || 'none';
const PORTS = (__ENV.PORTS || '8080').split(',');
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

const sold = new Counter('tickets_sold');
const soldOut = new Counter('sold_out_409');
const busy = new Counter('lock_timeout_503');
const errors = new Counter('server_errors_5xx');

export const options = {
  scenarios: {
    rush: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 200),
      timeUnit: '1s',
      duration: __ENV.DURATION || '20s',
      preAllocatedVUs: 300,
      maxVUs: 2000,
    },
  },
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const port = PORTS[Math.floor(Math.random() * PORTS.length)];   // a poor man's load balancer
  const body = JSON.stringify({ customerId: 1 + Math.floor(Math.random() * 200000), ticketTypeId: 3, quantity: 1 });
  const res = http.post(`http://localhost:${port}/lock-demo/orders?lock=${LOCK}`, body, JSON_HEADERS);
  if (res.status === 201) sold.add(1);
  else if (res.status === 409) soldOut.add(1);
  else if (res.status === 503) busy.add(1);
  else errors.add(1);
  check(res, { 'answered': (r) => r.status === 201 || r.status === 409 });
}
