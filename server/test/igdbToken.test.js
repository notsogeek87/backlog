import test from 'node:test';
import assert from 'node:assert/strict';
import { ConfigError, MIN_REMAINING_SECONDS, currentToken, tokenFingerprint } from '../lib/igdbToken.js';

const env = { TWITCH_CLIENT_ID: 'cid', TWITCH_CLIENT_SECRET: 'secret' };
const now = new Date('2026-10-31T10:00:00Z');

/** Fake `app_tokens` table: SELECT returns the stored row, INSERT … ON CONFLICT replaces it. */
function fakeSql(initial = null) {
  const db = { row: initial };
  const sql = (strings, ...values) => {
    const text = strings.join('?');
    if (text.startsWith('SELECT')) return Promise.resolve(db.row ? [db.row] : []);
    db.row = { token: values[1], expires_at: values[2] };
    return Promise.resolve([]);
  };
  return { sql, db };
}

function fakeFetch({ validateOk = true } = {}) {
  const calls = [];
  const fetchImpl = (url) => {
    calls.push(url);
    if (url.includes('/validate')) return Promise.resolve({ ok: validateOk });
    return Promise.resolve({ ok: true, json: () => Promise.resolve({ access_token: `tok${calls.length}`, expires_in: 5_000_000 }) });
  };
  return { fetchImpl, calls };
}

test('without a stored token, mints one and stores it', async () => {
  const { sql, db } = fakeSql();
  const { fetchImpl, calls } = fakeFetch();
  const out = await currentToken({ sql, env, fetchImpl, now: new Date() });
  assert.equal(out.clientId, 'cid');
  assert.equal(out.token, 'tok1');
  assert.equal(db.row.token, 'tok1');
  assert.equal(calls.length, 1);
  assert.ok(calls[0].includes('grant_type=client_credentials'));
});

test('reuses a fresh stored token without calling Twitch', async () => {
  const expires = new Date(now.getTime() + (MIN_REMAINING_SECONDS + 3600) * 1000).toISOString();
  const { sql } = fakeSql({ token: 'kept', expires_at: expires });
  const { fetchImpl, calls } = fakeFetch();
  const out = await currentToken({ sql, env, fetchImpl, now });
  assert.equal(out.token, 'kept');
  assert.equal(out.expiresIn, MIN_REMAINING_SECONDS + 3600);
  assert.equal(calls.length, 0);
});

test('renews a token that is about to expire', async () => {
  const { sql } = fakeSql({ token: 'old', expires_at: new Date(now.getTime() + 60_000).toISOString() });
  const { fetchImpl } = fakeFetch();
  assert.equal((await currentToken({ sql, env, fetchImpl, now })).token, 'tok1');
});

test('a reported stale token is renewed only when Twitch confirms it is dead', async () => {
  const row = { token: 'kept', expires_at: new Date(now.getTime() + 30 * 86400_000).toISOString() };
  const alive = fakeFetch({ validateOk: true });
  assert.equal((await currentToken({ sql: fakeSql({ ...row }).sql, env, fetchImpl: alive.fetchImpl, staleFingerprint: tokenFingerprint('kept'), now })).token, 'kept');
  const dead = fakeFetch({ validateOk: false });
  assert.equal((await currentToken({ sql: fakeSql({ ...row }).sql, env, fetchImpl: dead.fetchImpl, staleFingerprint: tokenFingerprint('kept'), now })).token, 'tok2');
  const other = fakeFetch({ validateOk: false });
  await currentToken({ sql: fakeSql({ ...row }).sql, env, fetchImpl: other.fetchImpl, staleFingerprint: tokenFingerprint('older'), now });
  assert.equal(other.calls.length, 0, 'a fingerprint of an older token does not trigger anything');
});

test('missing configuration is a ConfigError', async () => {
  await assert.rejects(currentToken({ sql: fakeSql().sql, env: {}, fetchImpl: fakeFetch().fetchImpl }), ConfigError);
});
