import test from 'node:test';
import assert from 'node:assert/strict';
import { LIMITS, allowHit, clientKey, windowStart } from '../lib/rateLimit.js';

/** Fake Neon tagged template: keeps the counters in memory, like the upsert in allowHit. */
function fakeSql() {
  const counts = new Map();
  const sql = (strings, ...values) => {
    const [scope, client, bucket] = values;
    const key = `${scope}|${client}|${bucket}`;
    counts.set(key, (counts.get(key) ?? 0) + 1);
    return Promise.resolve([{ hits: counts.get(key) }]);
  };
  return { sql, counts };
}

test('clientKey hashes the first forwarded IP and never returns it raw', () => {
  const a = clientKey({ headers: { 'x-forwarded-for': '203.0.113.7, 10.0.0.1' } });
  const b = clientKey({ headers: { 'x-forwarded-for': '203.0.113.7' } });
  const c = clientKey({ headers: { 'x-forwarded-for': '198.51.100.2' } });
  assert.equal(a, b);
  assert.notEqual(a, c);
  assert.match(a, /^[0-9a-f]{32}$/);
  assert.ok(!a.includes('203'));
});

test('windowStart aligns on the window', () => {
  assert.equal(windowStart(new Date('2026-10-31T10:42:13Z'), 60).toISOString(), '2026-10-31T10:00:00.000Z');
});

test('allowHit lets the limit through, then refuses until the next window', async () => {
  const { sql } = fakeSql();
  const now = new Date('2026-10-31T10:05:00Z');
  for (let i = 0; i < LIMITS.share.max; i++) assert.equal(await allowHit(sql, 'share', 'c1', now), true);
  assert.equal(await allowHit(sql, 'share', 'c1', now), false);
  assert.equal(await allowHit(sql, 'share', 'c2', now), true, 'other clients are not affected');
  assert.equal(await allowHit(sql, 'share', 'c1', new Date('2026-10-31T11:00:00Z')), true, 'a new window starts fresh');
});
