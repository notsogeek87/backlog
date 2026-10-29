import test from 'node:test';
import assert from 'node:assert/strict';
import { parsePreview, renderOpenPage } from '../lib/openPage.js';

test('a film link shows its details, TMDB and IMDb links and keeps the open-in-app button', () => {
  const html = renderOpenPage('movie', 'movie:603', { t: 'Matrix', y: '1999', c: '/abc.jpg', g: 'Action, SF', s: 'Un hacker.', r: '8.2', m: 'Wachowski' });
  assert.match(html, /Ouvrir dans l'app/);
  assert.match(html, /<h2>Matrix<\/h2>/);
  assert.match(html, /image\.tmdb\.org\/t\/p\/w342\/abc\.jpg/);
  assert.match(html, /https:\/\/www\.themoviedb\.org\/movie\/603/);
  assert.match(html, /imdb\.com\/find\/\?s=tt&amp;q=Matrix/);
  assert.match(html, /★ 8\.2\/10/);
});

test('a game links to IGDB, by its page when valid and by search otherwise', () => {
  assert.equal(parsePreview('game', '1', { t: 'Hades', u: 'https://www.igdb.com/games/hades' }).links[0].url, 'https://www.igdb.com/games/hades');
  assert.match(parsePreview('game', '1', { t: 'Hades', u: 'javascript:alert(1)' }).links[0].url, /^https:\/\/www\.igdb\.com\/search\?type=1&q=Hades$/);
});

test('unsafe values are dropped and html is escaped', () => {
  const p = parsePreview('book', 'isbn:1', { t: '<script>x</script>', c: 'https://evil.example/a.jpg', u: 'https://evil.example', y: '-3', r: '99' });
  assert.equal(p.image, null);
  assert.deepEqual(p.links, []);
  assert.equal(p.year, null);
  assert.equal(p.rating, null);
  assert.ok(!renderOpenPage('book', 'isbn:1', { t: '<script>x</script>' }).includes('<script>x'));
});

test('a link without details stays the plain page', () => {
  assert.equal(parsePreview('movie', 'movie:1', {}), null);
  assert.ok(!renderOpenPage('movie', 'movie:1', {}).includes('class="card"'));
});

test('the open button is an Android intent that falls back to the GitHub repository', () => {
  const html = renderOpenPage('movie', 'movie:603', {});
  assert.match(html, /href="intent:\/\/open\/movie\/movie%3A603#Intent;scheme=backlog;package=com\.davidgcd\.backlog;S\.browser_fallback_url=https%3A%2F%2Fgithub\.com%2Fnotsogeek87%2Fbacklog;end"/);
  assert.match(html, /href="https:\/\/github\.com\/notsogeek87\/backlog"/);
});
