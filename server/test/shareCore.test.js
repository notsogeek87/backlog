import test from 'node:test';
import assert from 'node:assert/strict';
import { CSP, escapeHtml, hashToken, newToken, renderPage, tokenMatches, validatePayload } from '../lib/shareCore.js';

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

test('library payload keeps one validated section per typology', () => {
  const p = validatePayload({
    kind: 'library',
    title: 'Ma librairie',
    owner: 'David',
    games: { items: [{ name: 'Hades', status: 'COMPLETED', url: 'javascript:1' }] },
    movies: { items: [{ name: 'Dune', status: 'WATCHED', series: false, rating: 9, url: 'https://www.themoviedb.org/movie/1' }] },
  });
  assert.equal(p.kind, 'library');
  assert.equal(p.games[0].url, null);
  assert.equal(p.movies[0].rating, 9);
  assert.throws(() => validatePayload({ kind: 'library', games: { items: [{ name: '' }] } }));
});

test('renderPage of a library has a tab per non-empty typology', () => {
  const html = renderPage({
    kind: 'library', title: 'Ma librairie', owner: 'David',
    games: [{ name: 'Hades', status: 'COMPLETED', coverImageId: null, url: null, rank: null }],
    movies: [{ name: 'Dune', status: 'WATCHED', series: false, year: 2021, posterPath: null, url: null, rating: 9 }],
  });
  assert.ok(html.includes('id="tab-games"') && html.includes('id="tab-movies"'));
  assert.ok(html.includes('Hades') && html.includes('Dune'));
  const gamesOnly = renderPage({ kind: 'library', title: 't', games: [{ name: 'Hades', status: 'PLAYED', coverImageId: null, url: null, rank: null }], movies: [] });
  assert.ok(!gamesOnly.includes('id="tab-movies"'));
});

test('books payload: only sane cover / url / note / status survive', () => {
  const p = validatePayload({
    kind: 'books',
    items: [
      { name: 'Dune', authors: 'Frank Herbert', status: 'READ', year: 1965, coverUrl: 'https://covers.openlibrary.org/b/id/8231856-M.jpg', url: 'https://openlibrary.org/works/OL893415W', rating: 5, favorite: true },
      { name: 'X', status: 'NOPE', coverUrl: 'https://evil.example/a.jpg', url: 'javascript:alert(1)', rating: 6, year: 'y', favorite: 'yes' },
      { name: 'Y', coverUrl: 'https://books.google.com/books/content?id=abc&zoom=1&source=gbs_api', url: 'https://books.google.com/books?id=abc' },
    ],
  });
  assert.equal(p.kind, 'books');
  assert.deepEqual(p.items[0], { name: 'Dune', authors: 'Frank Herbert', status: 'READ', year: 1965, coverUrl: 'https://covers.openlibrary.org/b/id/8231856-M.jpg', url: 'https://openlibrary.org/works/OL893415W', rating: 5, favorite: true });
  assert.deepEqual(p.items[1], { name: 'X', authors: '', status: 'TO_READ', year: null, coverUrl: null, url: null, rating: null, favorite: false });
  assert.ok(p.items[2].coverUrl && p.items[2].url);
  assert.throws(() => validatePayload({ kind: 'books', items: [{ name: '' }] }));
});

test('books page: rated covers best note first, unrated grouped by status, all escaped', () => {
  const html = renderPage({
    kind: 'books', title: 'Mes livres', owner: 'David',
    items: [
      { name: 'Bof', authors: 'A', status: 'READ', year: 2001, coverUrl: null, url: null, rating: 2, favorite: false },
      { name: 'Top', authors: 'B', status: 'READ', year: null, coverUrl: 'https://covers.openlibrary.org/b/id/1-M.jpg', url: null, rating: 5, favorite: true },
      { name: '<i>Plus tard</i>', authors: '', status: 'TO_READ', year: null, coverUrl: null, url: null, rating: null, favorite: false },
    ],
  });
  assert.ok(html.includes('Les notes de David'));
  assert.ok(html.indexOf('Top') < html.indexOf('Bof'));
  assert.ok(html.includes('★ 5') && html.includes('♥ Top'));
  assert.ok(html.indexOf('Bof') < html.indexOf('À lire <small>'));
  assert.ok(!html.includes('<i>Plus tard'));
});

test('single book: exactly one book, shown on its own page', () => {
  const book = { name: 'Dune', authors: 'Frank Herbert', status: 'READING', year: 1965, coverUrl: null, url: 'https://openlibrary.org/works/OL1W', rating: 4, favorite: false };
  const p = validatePayload({ kind: 'book', title: 'Dune', items: [book] });
  assert.equal(p.kind, 'book');
  assert.throws(() => validatePayload({ kind: 'book', items: [book, book] }));
  assert.throws(() => validatePayload({ kind: 'book', items: [] }));
  const html = renderPage(p);
  assert.ok(html.includes('class="single"') && html.includes('★★★★☆') && html.includes('En cours'));
  assert.ok(html.includes('Frank Herbert') && html.includes('Voir la fiche'));
});

test('library payload and page include a Livres tab, and still accept clients that send no books', () => {
  const p = validatePayload({ kind: 'library', games: { items: [{ name: 'Hades' }] }, books: { items: [{ name: 'Dune', status: 'READ', rating: 5 }] } });
  assert.equal(p.books[0].rating, 5);
  assert.deepEqual(validatePayload({ kind: 'library', games: { items: [{ name: 'Hades' }] } }).books, []);
  const html = renderPage({ kind: 'library', title: 'L', games: p.games, movies: [], books: p.books });
  assert.ok(html.includes('id="tab-books"') && html.includes('Livres'));
  const noBooks = renderPage({ kind: 'library', title: 'L', games: p.games, movies: [] });
  assert.ok(!noBooks.includes('id="tab-books"'));
});

test('CSP lets the book covers load through Open Library\'s redirect to archive.org, and still forbids scripts', () => {
  const imgSrc = CSP.match(/img-src ([^;]+)/)[1].split(' ');
  for (const host of ['https://covers.openlibrary.org', 'https://archive.org', 'https://*.archive.org', 'https://books.google.com', 'https://images.igdb.com', 'https://image.tmdb.org']) {
    assert.ok(imgSrc.includes(host), host);
  }
  assert.ok(CSP.startsWith("default-src 'none'") && !CSP.includes('script-src') && !CSP.includes("'unsafe-eval'"));
});

test('book pages may link to an Open Library edition by ISBN, but not to anything else', () => {
  const urlOf = (url) => validatePayload({ kind: 'books', items: [{ name: 'Dune', url }] }).items[0].url;
  assert.equal(urlOf('https://openlibrary.org/isbn/9782070368228'), 'https://openlibrary.org/isbn/9782070368228');
  assert.equal(urlOf('https://openlibrary.org/isbn/207036822X'), 'https://openlibrary.org/isbn/207036822X');
  assert.equal(urlOf('https://openlibrary.org/isbn/978220?x=1'), null);
  assert.equal(urlOf('https://evil.example/isbn/9782070368228'), null);
});
