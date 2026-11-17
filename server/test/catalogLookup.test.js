import test from 'node:test';
import assert from 'node:assert/strict';
import { isLookupable, lookupPreview, shorten } from '../lib/catalogLookup.js';
import { renderOpenPage } from '../lib/openPage.js';

/** A fetch answering from a {url-prefix: body} map, recording the calls. */
function fakeFetch(routes) {
  const calls = [];
  const impl = async (url, init = {}) => {
    calls.push({ url, init });
    const hit = Object.keys(routes).find((prefix) => url.startsWith(prefix));
    if (!hit) return { ok: false, status: 404, json: async () => ({}) };
    return { ok: true, status: 200, json: async () => routes[hit] };
  };
  impl.calls = calls;
  return impl;
}

test('only catalogue ids are looked up', () => {
  assert.ok(isLookupable('movie', 'movie:1668499'));
  assert.ok(isLookupable('movie', 'tv:1396'));
  assert.ok(isLookupable('game', '1942'));
  assert.ok(isLookupable('book', 'isbn:9780141036144'));
  assert.ok(isLookupable('book', 'ol:OL7353617M'));
  assert.ok(isLookupable('book', 'work:OL45804W'));
  assert.ok(!isLookupable('book', 'ta:0123456789ab'));
  assert.ok(!isLookupable('movie', 'person:1'));
  assert.ok(!isLookupable('game', '1; drop'));
});

test('a film is read from TMDB in French with its directors and runtime', async () => {
  const fetchImpl = fakeFetch({
    'https://api.themoviedb.org/3/movie/1668499': {
      title: 'Le Vertige', release_date: '2026-03-04', poster_path: '/abc.jpg', genres: [{ name: 'Animation' }, { name: 'Comédie' }],
      overview: 'Jacques se rend chez son ami Bruno.', vote_average: 6, runtime: 67,
      credits: { crew: [{ job: 'Director', name: 'Quentin Dupieux' }, { job: 'Writer', name: 'X' }] },
    },
  });
  const p = await lookupPreview('movie', 'movie:1668499', { env: { TMDB_API_KEY: 'k3y' }, fetchImpl });
  assert.deepEqual(p, { t: 'Le Vertige', y: '2026', c: '/abc.jpg', g: 'Animation, Comédie', s: 'Jacques se rend chez son ami Bruno.', r: '6', m: 'Quentin Dupieux · 67 min' });
  assert.match(fetchImpl.calls[0].url, /language=fr-FR&append_to_response=credits&api_key=k3y$/);
});

test('a v4 TMDB token is sent as a bearer, never in the url', async () => {
  const fetchImpl = fakeFetch({ 'https://api.themoviedb.org/3/tv/1396': { name: 'Breaking Bad', first_air_date: '2008-01-20', number_of_seasons: 5, created_by: [{ name: 'Vince Gilligan' }] } });
  const p = await lookupPreview('movie', 'tv:1396', { env: { TMDB_API_KEY: 'eyJtoken' }, fetchImpl });
  assert.equal(p.m, 'Vince Gilligan · 5 saisons');
  assert.ok(!fetchImpl.calls[0].url.includes('eyJ'));
  assert.equal(fetchImpl.calls[0].init.headers.Authorization, 'Bearer eyJtoken');
});

test('no TMDB key or an unreachable catalogue gives no preview', async () => {
  assert.equal(await lookupPreview('movie', 'movie:1', { env: {}, fetchImpl: fakeFetch({}) }), null);
  assert.equal(await lookupPreview('movie', 'movie:1', { env: { TMDB_API_KEY: 'k' }, fetchImpl: fakeFetch({}) }), null);
  const throwing = async () => { throw new Error('timeout'); };
  assert.equal(await lookupPreview('book', 'work:OL1W', { fetchImpl: throwing }), null);
});

test('a book is read from Open Library: edition, then its work and authors', async () => {
  const fetchImpl = fakeFetch({
    'https://openlibrary.org/isbn/9780141036144.json': { title: '1984', publish_date: '2008', covers: [42], authors: [{ key: '/authors/OL1A' }], works: [{ key: '/works/OL9W' }] },
    'https://openlibrary.org/works/OL9W.json': { description: { value: 'Big Brother.' }, subjects: ['Dystopie', 'Politique'] },
    'https://openlibrary.org/authors/OL1A.json': { name: 'George Orwell' },
  });
  const p = await lookupPreview('book', 'isbn:9780141036144', { fetchImpl });
  assert.deepEqual(p, {
    t: '1984', y: '2008', c: 'https://covers.openlibrary.org/b/id/42-L.jpg', g: 'Dystopie, Politique', s: 'Big Brother.',
    m: 'George Orwell', u: 'https://openlibrary.org/isbn/9780141036144',
  });
  assert.match(renderOpenPage('book', 'isbn:9780141036144', p), /<h2>1984<\/h2>/);
});

test('a game is read from IGDB with the server token', async () => {
  const sql = async () => [{ token: 'tok', expires_at: new Date(Date.now() + 30 * 86400e3).toISOString() }];
  const fetchImpl = fakeFetch({
    'https://api.igdb.com/v4/games': [{ name: 'Hades', first_release_date: 1600300800, cover: { image_id: 'co2i0f' }, total_rating: 92.4, platforms: [{ name: 'PC' }], url: 'https://www.igdb.com/games/hades' }],
  });
  const p = await lookupPreview('game', '113112', { sql, env: { TWITCH_CLIENT_ID: 'id', TWITCH_CLIENT_SECRET: 's' }, fetchImpl });
  assert.equal(p.t, 'Hades');
  assert.equal(p.y, '2020');
  assert.equal(p.c, 'co2i0f');
  assert.equal(fetchImpl.calls[0].init.headers['Client-ID'], 'id');
  assert.match(fetchImpl.calls[0].init.body, /where id = 113112;/);
});

test('the page gets a link card for chat apps', () => {
  const html = renderOpenPage('movie', 'movie:1', { t: 'Le Vertige', y: '2026', c: '/abc.jpg', m: 'Quentin Dupieux' });
  assert.match(html, /<meta property="og:title" content="Le Vertige — Backlog">/);
  assert.match(html, /<meta property="og:description" content="2026 · Quentin Dupieux">/);
  assert.match(html, /<meta property="og:image" content="https:\/\/image\.tmdb\.org\/t\/p\/w342\/abc\.jpg">/);
});

test('a long synopsis is cut', () => {
  assert.equal(shorten('a  b\n c'), 'a b c');
  assert.equal(shorten('x'.repeat(400)).length, 300);
});
