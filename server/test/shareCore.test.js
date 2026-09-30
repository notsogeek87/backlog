import test from 'node:test';
import assert from 'node:assert/strict';
import { escapeHtml, hashToken, newToken, renderPage, tokenMatches, validatePayload } from '../lib/shareCore.js';

test('validatePayload drops unsafe urls, bad image ids and unknown statuses', () => {
  const { items } = validatePayload({
    items: [{ name: ' Celeste ', status: 'NOPE', coverImageId: '../x', url: 'javascript:alert(1)' },
            { name: 'Hades', status: 'COMPLETED', coverImageId: 'co1abc', url: 'https://www.igdb.com/games/hades' }],
  });
  assert.deepEqual(items[0], { name: 'Celeste', status: 'BACKLOG', coverImageId: null, url: null });
  assert.equal(items[1].url, 'https://www.igdb.com/games/hades');
  assert.equal(items[1].coverImageId, 'co1abc');
});

test('validatePayload rejects bad bodies', () => {
  assert.throws(() => validatePayload(null));
  assert.throws(() => validatePayload({ items: [{ name: '' }] }));
  assert.throws(() => validatePayload({ items: Array.from({ length: 2001 }, () => ({ name: 'a' })) }));
});

test('renderPage escapes names and titles', () => {
  const html = renderPage({ title: '<b>x</b>', items: [{ name: '<script>alert(1)</script>', status: 'PLAYED', coverImageId: null, url: null }] });
  assert.ok(!html.includes('<script>'));
  assert.ok(!html.includes('<b>x</b>'));
  assert.ok(html.includes('Joué'));
});

test('token round trip', () => {
  const t = newToken();
  assert.ok(tokenMatches(t, hashToken(t)));
  assert.ok(!tokenMatches('autre', hashToken(t)));
  assert.equal(escapeHtml(`&"'<>`), '&amp;&quot;&#39;&lt;&gt;');
});
