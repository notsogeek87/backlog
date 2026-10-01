import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';

export const STATUSES = ['BACKLOG', 'PLAYED', 'COMPLETED'];
export const STATUS_LABELS = { BACKLOG: 'Backlog', PLAYED: 'Joué', COMPLETED: 'Terminé' };
export const MAX_ITEMS = 2000;
/** A link lives this long after its creation, then it is deleted. Republishing does not extend it. */
export const SHARE_TTL_HOURS = 24;

export const MOVIE_STATUSES = ['TO_WATCH', 'WATCHING', 'WATCHED'];
export const MOVIE_STATUS_LABELS = { TO_WATCH: 'À voir', WATCHING: 'En cours', WATCHED: 'Vu' };
export const BOOK_STATUSES = ['TO_READ', 'READING', 'READ', 'ABANDONED'];
export const BOOK_STATUS_LABELS = { TO_READ: 'À lire', READING: 'En cours', READ: 'Lu', ABANDONED: 'Abandonné' };
// Covers come from Open Library (or Google Books when it was the fallback); pages are Open Library / Google Books ones.
const BOOK_COVER = /^https:\/\/covers\.openlibrary\.org\/b\/(?:id|olid)\/[A-Za-z0-9]+-[SML]\.jpg(?:\?default=false)?$|^https:\/\/books\.google\.com\/books\/content\?[A-Za-z0-9=&_%.\-]+$/;
const BOOK_URL = /^https:\/\/openlibrary\.org\/(?:works|books)\/OL\d+[WM]$|^https:\/\/books\.google\.com\/books\?id=[A-Za-z0-9_-]+$/;
const TMDB_URL = /^https:\/\/www\.themoviedb\.org\/(movie|tv)\/\d+$/;
const TMDB_POSTER = /^https:\/\/image\.tmdb\.org\/t\/p\/(?:w\d+|original)(\/[A-Za-z0-9_-]+\.(?:jpg|jpeg|png))$/;

const IGDB_URL = /^https:\/\/www\.igdb\.com\/games\/[a-z0-9\-_.]+$/i;
const IMAGE_ID = /^[a-z0-9]{1,40}$/i;

function validateMovieItems(rawItems) {
  return rawItems.map((raw) => {
    const name = typeof raw?.name === 'string' ? raw.name.trim().slice(0, 200) : '';
    if (!name) throw new Error('titre manquant');
    const poster = typeof raw.posterUrl === 'string' ? TMDB_POSTER.exec(raw.posterUrl) : null;
    return {
      name,
      status: MOVIE_STATUSES.includes(raw.status) ? raw.status : 'TO_WATCH',
      series: raw.series === true,
      year: Number.isInteger(raw.year) && raw.year >= 1800 && raw.year <= 2200 ? raw.year : null,
      posterPath: poster ? poster[1] : null,
      url: typeof raw.url === 'string' && TMDB_URL.test(raw.url) ? raw.url : null,
      rating: Number.isInteger(raw.rating) && raw.rating >= 1 && raw.rating <= 10 ? raw.rating : null,
    };
  });
}

function validateBookItems(rawItems) {
  return rawItems.map((raw) => {
    const name = typeof raw?.name === 'string' ? raw.name.trim().slice(0, 200) : '';
    if (!name) throw new Error('titre manquant');
    return {
      name,
      authors: typeof raw.authors === 'string' ? raw.authors.trim().slice(0, 200) : '',
      status: BOOK_STATUSES.includes(raw.status) ? raw.status : 'TO_READ',
      year: Number.isInteger(raw.year) && raw.year >= 0 && raw.year <= 2200 ? raw.year : null,
      coverUrl: typeof raw.coverUrl === 'string' && BOOK_COVER.test(raw.coverUrl) ? raw.coverUrl : null,
      url: typeof raw.url === 'string' && BOOK_URL.test(raw.url) ? raw.url : null,
      rating: Number.isInteger(raw.rating) && raw.rating >= 1 && raw.rating <= 5 ? raw.rating : null,
      favorite: raw.favorite === true,
    };
  });
}

function validateGameItems(rawItems) {
  return rawItems.map((raw) => {
    const name = typeof raw?.name === 'string' ? raw.name.trim().slice(0, 200) : '';
    if (!name) throw new Error('nom de jeu manquant');
    return {
      name,
      status: STATUSES.includes(raw.status) ? raw.status : 'BACKLOG',
      coverImageId: typeof raw.coverImageId === 'string' && IMAGE_ID.test(raw.coverImageId) ? raw.coverImageId : null,
      url: typeof raw.url === 'string' && IGDB_URL.test(raw.url) ? raw.url : null,
      rank: Number.isInteger(raw.rank) && raw.rank >= 1 && raw.rank <= MAX_ITEMS ? raw.rank : null,
    };
  });
}

const cleanTitle = (body, fallback) => {
  const title = typeof body.title === 'string' ? body.title.trim().slice(0, 80) : '';
  return title || fallback;
};
const cleanOwner = (body) => (typeof body.owner === 'string' ? body.owner.trim().slice(0, 40) : '');

/** The whole library: one tab per typology (games, films & séries, books), each with its own items. */
function validateLibrary(body) {
  const section = (raw) => (Array.isArray(raw?.items) ? raw.items : []);
  const games = section(body.games);
  const movies = section(body.movies);
  // Older app versions send no books section: it is simply empty.
  const books = section(body.books);
  if (games.length > MAX_ITEMS || movies.length > MAX_ITEMS || books.length > MAX_ITEMS) throw new Error('trop de titres');
  return {
    kind: 'library',
    title: cleanTitle(body, 'Ma librairie'),
    owner: cleanOwner(body),
    games: validateGameItems(games),
    movies: validateMovieItems(movies),
    books: validateBookItems(books),
  };
}

/** Returns a cleaned payload, or throws Error(message) — never trusts anything the client sent. */
export function validatePayload(body) {
  if (!body || typeof body !== 'object') throw new Error('items manquant');
  if (body.kind === 'library') return validateLibrary(body);
  if (!Array.isArray(body.items)) throw new Error('items manquant');
  if (body.items.length > MAX_ITEMS) throw new Error('trop de titres');
  if (body.kind === 'books') {
    return { kind: 'books', title: cleanTitle(body, 'Mes livres'), owner: cleanOwner(body), items: validateBookItems(body.items) };
  }
  if (body.kind === 'book') {
    // One book to show off: a page of its own, never a list.
    if (body.items.length !== 1) throw new Error('un seul livre attendu');
    return { kind: 'book', title: cleanTitle(body, 'Un livre'), owner: cleanOwner(body), items: validateBookItems(body.items) };
  }
  if (body.kind === 'movies') {
    return { kind: 'movies', title: cleanTitle(body, 'Mes films & séries'), owner: cleanOwner(body), items: validateMovieItems(body.items) };
  }
  return { title: cleanTitle(body, 'Mon backlog'), owner: cleanOwner(body), items: validateGameItems(body.items) };
}

export const newId = () => randomBytes(8).toString('base64url');
export const newToken = () => randomBytes(32).toString('hex');
export const hashToken = (token) => createHash('sha256').update(String(token)).digest('hex');

export function tokenMatches(token, storedHash) {
  const a = Buffer.from(hashToken(token), 'hex');
  const b = Buffer.from(String(storedHash), 'hex');
  return a.length === b.length && timingSafeEqual(a, b);
}

export const escapeHtml = (s) =>
  String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

const card = (g, badge = '') => {
  const img = g.coverImageId
    ? `<img src="https://images.igdb.com/igdb/image/upload/t_cover_big/${escapeHtml(g.coverImageId)}.jpg" alt="" loading="lazy">`
    : '<div class="noimg"></div>';
  const inner = `<div class="cover">${img}${badge}</div><span>${escapeHtml(g.name)}</span>`;
  return g.url
    ? `<a class="card" href="${escapeHtml(g.url)}" rel="noopener noreferrer">${inner}</a>`
    : `<div class="card">${inner}</div>`;
};

const movieCard = (m, badge = '') => {
  const img = m.posterPath
    ? `<img src="https://image.tmdb.org/t/p/w342${escapeHtml(m.posterPath)}" alt="" loading="lazy">`
    : '<div class="noimg"></div>';
  const sub = [m.series ? 'Série' : 'Film', m.year].filter(Boolean).join(' · ');
  const inner = `<div class="cover">${img}${badge}</div><span>${escapeHtml(m.name)}<em>${escapeHtml(sub)}</em></span>`;
  return m.url
    ? `<a class="card" href="${escapeHtml(m.url)}" rel="noopener noreferrer">${inner}</a>`
    : `<div class="card">${inner}</div>`;
};

const bookCard = (b, badge = '') => {
  const img = b.coverUrl ? `<img src="${escapeHtml(b.coverUrl)}" alt="" loading="lazy">` : '<div class="noimg"></div>';
  const sub = [b.authors, b.year].filter(Boolean).join(' · ');
  const inner = `<div class="cover">${img}${badge}</div><span>${b.favorite ? '♥ ' : ''}${escapeHtml(b.name)}<em>${escapeHtml(sub)}</em></span>`;
  return b.url
    ? `<a class="card book" href="${escapeHtml(b.url)}" rel="noopener noreferrer">${inner}</a>`
    : `<div class="card book">${inner}</div>`;
};

/** Books: the covers the owner rated come first, best note first (★ out of 5); the unrated ones follow by status. */
function booksSections(owner, items) {
  const byName = (a, b) => a.name.localeCompare(b.name, 'fr');
  const rated = items.filter((b) => b.rating != null).sort((a, b) => b.rating - a.rating || byName(a, b));
  const unrated = items.filter((b) => b.rating == null);
  const ratedSection = rated.length
    ? `<h2>${owner ? `Les notes de ${escapeHtml(owner)}` : 'Mes notes'} <small>${rated.length}</small></h2><div class="grid">${rated
        .map((b) => bookCard(b, `<b class="rank">★ ${b.rating}</b>`))
        .join('')}</div>`
    : '';
  const sections = BOOK_STATUSES.map((status) => {
    const list = unrated.filter((b) => b.status === status).sort(byName);
    if (!list.length) return '';
    const label = status === 'READ' ? 'Lu, sans note' : BOOK_STATUS_LABELS[status];
    return `<h2>${label} <small>${list.length}</small></h2><div class="grid">${list.map((b) => bookCard(b)).join('')}</div>`;
  }).join('');
  return `${ratedSection}${sections || (ratedSection ? '' : '<p>Cette liste est vide.</p>')}`;
}

/** A single book: big cover, title, authors, status and note. */
function singleBookSection(b) {
  const img = b.coverUrl ? `<img src="${escapeHtml(b.coverUrl)}" alt="">` : '<div class="noimg"></div>';
  const stars = b.rating ? `<p class="stars">${'★'.repeat(b.rating)}${'☆'.repeat(5 - b.rating)}</p>` : '';
  const meta = [BOOK_STATUS_LABELS[b.status], b.year].filter(Boolean).join(' · ');
  const body = `<div class="single"><div class="cover">${img}</div><div><h2>${b.favorite ? '♥ ' : ''}${escapeHtml(b.name)}</h2>${b.authors ? `<p>${escapeHtml(b.authors)}</p>` : ''}<p class="owner">${escapeHtml(meta)}</p>${stars}${b.url ? `<p><a href="${escapeHtml(b.url)}" rel="noopener noreferrer">Voir la fiche</a></p>` : ''}</div></div>`;
  return body;
}

/** Films & séries: the posters the owner rated come first, best note first; the unrated ones follow by status. */
function moviesSections(owner, items) {
  const byName = (a, b) => a.name.localeCompare(b.name, 'fr');
  const rated = items.filter((m) => m.rating != null).sort((a, b) => b.rating - a.rating || byName(a, b));
  const unrated = items.filter((m) => m.rating == null);
  const ratedSection = rated.length
    ? `<h2>${owner ? `Les notes de ${escapeHtml(owner)}` : 'Mes notes'} <small>${rated.length}</small></h2><div class="grid">${rated
        .map((m) => movieCard(m, `<b class="rank">★ ${m.rating}</b>`))
        .join('')}</div>`
    : '';
  const sections = MOVIE_STATUSES.map((status) => {
    const list = unrated.filter((m) => m.status === status).sort(byName);
    if (!list.length) return '';
    const label = status === 'WATCHED' ? 'Vu, sans note' : MOVIE_STATUS_LABELS[status];
    return `<h2>${label} <small>${list.length}</small></h2><div class="grid">${list.map((m) => movieCard(m)).join('')}</div>`;
  }).join('');
  return `${ratedSection}${sections || (ratedSection ? '' : '<p>Cette liste est vide.</p>')}`;
}

export function renderMoviesPage({ title, owner = '', items }) {
  return page({ title, owner, body: moviesSections(owner, items) });
}

/** Ranked games lead as "Mon classement" (1 = most loved, in rank order); the rest are grouped by status. */
function gamesSections(owner, items) {
  const ranked = items.filter((g) => g.rank != null).sort((a, b) => a.rank - b.rank);
  const rest = items.filter((g) => g.rank == null);
  const rankingSection = ranked.length
    ? `<h2>${owner ? `Le top de ${escapeHtml(owner)}` : 'Mon classement'} <small>${ranked.length}</small></h2><div class="grid">${ranked
        .map((g, i) => card(g, `<b class="rank">${i + 1}</b>`))
        .join('')}</div>`
    : '';
  const sections = STATUSES.map((status) => {
    const games = rest.filter((g) => g.status === status).sort((a, b) => a.name.localeCompare(b.name, 'fr'));
    if (!games.length) return '';
    return `<h2>${STATUS_LABELS[status]} <small>${games.length}</small></h2><div class="grid">${games.map((g) => card(g)).join('')}</div>`;
  }).join('');
  return `${rankingSection}${sections || (rankingSection ? '' : '<p>Ce backlog est vide.</p>')}`;
}

/**
 * Library page: one tab per typology. Pure CSS (radio inputs + :checked) because the page's CSP forbids scripts.
 * A tab only exists when it has something in it; the first non-empty one opens by default.
 */
function renderLibraryPage({ title, owner = '', games, movies, books = [] }) {
  const tabs = [
    { id: 'games', label: 'Jeux', count: games.length, html: gamesSections(owner, games) },
    { id: 'movies', label: 'Films & séries', count: movies.length, html: moviesSections(owner, movies) },
    { id: 'books', label: 'Livres', count: books.length, html: booksSections(owner, books) },
  ];
  const shown = tabs.filter((t) => t.count > 0);
  if (!shown.length) return page({ title, owner, body: '<p>Cette librairie est vide.</p>' });
  const nav = shown
    .map((t, i) => `<input class="tab" type="radio" name="tab" id="tab-${t.id}"${i === 0 ? ' checked' : ''}><label for="tab-${t.id}">${t.label} <small>${t.count}</small></label>`)
    .join('');
  const panels = shown.map((t) => `<section class="panel" id="panel-${t.id}">${t.html}</section>`).join('');
  return page({ title, owner, body: `<div class="tabs">${nav}${panels}</div>` });
}

export function renderPage(payload) {
  if (payload.kind === 'library') return renderLibraryPage(payload);
  if (payload.kind === 'movies') return renderMoviesPage(payload);
  if (payload.kind === 'books') return page({ title: payload.title, owner: payload.owner, body: booksSections(payload.owner, payload.items) });
  if (payload.kind === 'book') return page({ title: payload.title, owner: payload.owner, body: singleBookSection(payload.items[0]) });
  const { owner = '', title, items } = payload;
  return page({ title, owner, body: gamesSections(owner, items) });
}

function page({ title, owner, body }) {
    return `<!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex"><title>${escapeHtml(owner ? `${title} — ${owner}` : title)}</title><style>
:root{color-scheme:dark}body{margin:0;background:#0b0f1a;color:#e8ecf5;font:16px system-ui,sans-serif;padding:16px 16px 48px;max-width:1000px;margin-inline:auto}
h1{font-size:1.6rem;margin-bottom:.2rem}.owner{margin:0;opacity:.75}h2{margin-top:2rem}small{opacity:.6;font-weight:400}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(130px,1fr));gap:12px}
.card{display:block;color:inherit;text-decoration:none;background:#151b2c;border-radius:12px;overflow:hidden}
.cover{position:relative}
.card img,.noimg{width:100%;aspect-ratio:3/4;object-fit:cover;background:#1e2740;display:block}
.rank{position:absolute;top:6px;left:6px;min-width:1.7em;padding:.15em .4em;border-radius:999px;background:#22d3ee;color:#04222a;font-size:.95rem;text-align:center}
.tabs{display:flex;flex-wrap:wrap;gap:8px;margin-top:1.2rem}.tab{position:absolute;opacity:0;pointer-events:none}
.tabs label{padding:.5em 1em;border-radius:999px;background:#151b2c;cursor:pointer}.tab:checked+label{background:#22d3ee;color:#04222a}.tab:focus-visible+label{outline:2px solid #22d3ee}
.panel{display:none;width:100%}
#tab-games:checked~#panel-games,#tab-movies:checked~#panel-movies,#tab-books:checked~#panel-books{display:block}.panel h2:first-child{margin-top:.6rem}
.book img,.book .noimg,.single img,.single .noimg{aspect-ratio:2/3}.single{display:flex;flex-wrap:wrap;gap:20px;margin-top:1.2rem}.single .cover{width:min(220px,100%)}.single img,.single .noimg{width:100%;object-fit:cover;background:#1e2740;display:block;border-radius:12px}.single h2{margin-top:0}.stars{color:#fbbf24;font-size:1.3rem;margin:.3rem 0}a{color:#22d3ee}
.card span{display:block;padding:8px;font-size:.85rem}.card em{display:block;font-style:normal;opacity:.6;font-size:.75rem;margin-top:2px}
</style></head><body><h1>${escapeHtml(title)}</h1>${owner ? `<p class="owner">par <strong>${escapeHtml(owner)}</strong></p>` : ''}${body}</body></html>`;
}
