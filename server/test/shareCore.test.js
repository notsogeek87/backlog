import test from 'node:test';
import assert from 'node:assert/strict';
import { escapeHtml, hashToken, newToken, renderPage, tokenMatches, validatePayload } from '../lib/shareCore.js';

test('validatePayload drops unsafe urls, bad image ids and unknown statuses', () => {
  const { items } = validatePayload({
    items: [{ name: ' Celeste ', status: 'NOPE', coverImageId: '../x', url: 'javascript:alert(1)' },
            { name: 'Hades', status: 'COMPLETED', coverImageId: 'co1abc', url: 'https://www.igdb.com/games/hades' }],
  });
  assert.deepEqual(items[0], { name: 'Celeste', status: 'BACKLOG', coverImageId: null, url: null, rank: null });
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

test('validatePayload keeps only sane ranks', () => {
  const { items } = validatePayload({ items: [{ name: 'a', rank: 2 }, { name: 'b', rank: 0 }, { name: 'c', rank: 'x' }, { name: 'd', rank: 1.5 }] });
  assert.deepEqual(items.map((g) => g.rank), [2, null, null, null]);
});

test('renderPage lists ranked games first, numbered by rank, and keeps them out of the status groups', () => {
  const html = renderPage({
    title: 'Mon backlog',
    items: [
      { name: 'Zelda', status: 'BACKLOG', coverImageId: null, url: null, rank: null },
      { name: 'Celeste', status: 'COMPLETED', coverImageId: null, url: null, rank: 2 },
      { name: 'Hades', status: 'PLAYED', coverImageId: null, url: null, rank: 1 },
    ],
  });
  assert.ok(html.indexOf('Mon classement') < html.indexOf('Backlog <small>'));
  assert.ok(html.indexOf('Hades') < html.indexOf('Celeste'));
  assert.ok(html.includes('<b class="rank">1</b>'));
  assert.equal(html.match(/Celeste/g).length, 1);
  assert.ok(!html.includes('Terminé <small>'));
});

test('owner is trimmed, capped and shown on the page (escaped)', () => {
  const { owner } = validatePayload({ owner: '  ' + 'x'.repeat(60), items: [] });
  assert.equal(owner.length, 40);
  assert.equal(validatePayload({ items: [] }).owner, '');
  const html = renderPage({ title: 'T', owner: '<b>David</b>', items: [{ name: 'Hades', status: 'PLAYED', coverImageId: null, url: null, rank: 1 }] });
  assert.match(html, /Le top de &lt;b&gt;David&lt;\/b&gt;/);
  assert.ok(!html.includes('<b>David'));
  assert.match(renderPage({ title: 'T', items: [] }), /<h1>T<\/h1><p>Ce backlog/);
});

test('movies payload: only sane poster / tmdb url / note / status survive', () => {
  const p = validatePayload({
    kind: 'movies',
    items: [
      { name: 'Alien', status: 'WATCHED', series: false, year: 1979, posterUrl: 'https://image.tmdb.org/t/p/w500/abc_1-2.jpg', url: 'https://www.themoviedb.org/movie/348', rating: 9 },
      { name: 'X', status: 'NOPE', posterUrl: 'https://evil.example/a.jpg', url: 'javascript:alert(1)', rating: 11, year: 'y' },
    ],
  });
  assert.equal(p.kind, 'movies');
  assert.deepEqual(p.items[0], { name: 'Alien', status: 'WATCHED', series: false, year: 1979, posterPath: '/abc_1-2.jpg', url: 'https://www.themoviedb.org/movie/348', rating: 9 });
  assert.deepEqual(p.items[1], { name: 'X', status: 'TO_WATCH', series: false, year: null, posterPath: null, url: null, rating: null });
});

test('movies page: rated posters best note first, unrated grouped by status, all escaped', () => {
  const html = renderPage({
    kind: 'movies',
    title: 'Ma liste',
    owner: 'David',
    items: [
      { name: 'Bof', status: 'WATCHED', series: false, year: 2001, posterPath: '/b.jpg', url: null, rating: 4 },
      { name: 'Top', status: 'WATCHED', series: true, year: null, posterPath: '/t.jpg', url: 'https://www.themoviedb.org/tv/1', rating: 10 },
      { name: '<i>Plus tard</i>', status: 'TO_WATCH', series: false, year: null, posterPath: null, url: null, rating: null },
    ],
  });
  assert.ok(html.includes('Les notes de David'));
  assert.ok(html.indexOf('Top') < html.indexOf('Bof'));
  assert.ok(html.includes('★ 10'));
  assert.ok(html.includes('https://image.tmdb.org/t/p/w342/t.jpg'));
  assert.ok(html.indexOf('Bof') < html.indexOf('À voir <small>'));
  assert.ok(!html.includes('<i>Plus tard'));
});
