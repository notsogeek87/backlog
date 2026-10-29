import { BOOK_COVER, BOOK_URL, CSP, IGDB_URL, IMAGE_ID, TMDB_POSTER, escapeHtml } from './shareCore.js';

export const KINDS = new Set(['game', 'movie', 'book']);
const KIND_LABELS = { game: 'Jeu vidéo', movie: 'Film', book: 'Livre' };
const MOVIE_ID = /^(movie|tv):(\d+)$/;

const text = (value, max) => (typeof value === 'string' ? value.trim().slice(0, max) : '');
const first = (v) => (Array.isArray(v) ? v[0] : v);

/**
 * What the app put in the link's query string to describe the title (t, y, c, g, s, r, m, u).
 * Nothing is stored server side: everything is cleaned here and anything unknown or unsafe is dropped.
 * Returns null when the link carries no title (an older app's link): the page then stays the plain "open in app" one.
 */
export function parsePreview(kind, id, query = {}) {
  const q = (key) => first(query[key]);
  const title = text(q('t'), 200);
  if (!title) return null;
  const year = Number(q('y'));
  const rating = Number(q('r'));
  const cover = text(q('c'), 300);
  const movieId = MOVIE_ID.exec(id);
  const catalogUrl = text(q('u'), 300);

  let image = null;
  if (kind === 'game' && IMAGE_ID.test(cover)) image = `https://images.igdb.com/igdb/image/upload/t_cover_big/${cover}.jpg`;
  if (kind === 'movie' && TMDB_POSTER.test(`https://image.tmdb.org/t/p/w342${cover}`)) image = `https://image.tmdb.org/t/p/w342${cover}`;
  if (kind === 'book' && BOOK_COVER.test(cover)) image = cover;

  const links = [];
  const search = encodeURIComponent(title);
  if (kind === 'movie') {
    if (movieId) links.push({ label: 'Fiche TMDB', url: `https://www.themoviedb.org/${movieId[1]}/${movieId[2]}` });
    links.push({ label: 'Chercher sur IMDb', url: `https://www.imdb.com/find/?s=tt&q=${search}` });
  } else if (kind === 'game') {
    links.push(IGDB_URL.test(catalogUrl)
      ? { label: 'Fiche IGDB', url: catalogUrl }
      : { label: 'Chercher sur IGDB', url: `https://www.igdb.com/search?type=1&q=${search}` });
  } else if (BOOK_URL.test(catalogUrl)) {
    links.push({ label: 'Voir la fiche', url: catalogUrl });
  }

  return {
    title,
    year: Number.isInteger(year) && year >= 0 && year <= 2200 && year > 0 ? year : null,
    rating: Number.isFinite(rating) && rating > 0 && rating <= 10 ? Math.round(rating * 10) / 10 : null,
    genres: text(q('g'), 150),
    summary: text(q('s'), 800),
    meta: text(q('m'), 250),
    image,
    links,
  };
}

const STYLE = `body{font-family:system-ui,sans-serif;background:#0b0d14;color:#eee;margin:0;padding:24px 16px 48px}
main{max-width:34rem;margin:0 auto;text-align:center}h1{margin:.4rem 0}
a.b,a.l{display:inline-block;margin:.3rem;padding:.7rem 1.2rem;border-radius:999px;text-decoration:none;font-weight:600}
a.b{background:#7c5cff;color:#fff}a.l{border:1px solid #7c5cff;color:#cfc6ff}
p{color:#aab;padding:0 .5rem}.card{background:#151b2c;border-radius:16px;padding:16px;margin:1rem 0;text-align:left}
.card img{display:block;width:min(220px,60%);margin:0 auto 12px;border-radius:12px}
.card h2{margin:.2rem 0;font-size:1.4rem}.kind{opacity:.7;margin:0;padding:0}.meta{margin:.2rem 0;padding:0;opacity:.85}
.stars{color:#fbbf24;font-weight:600;padding:0;margin:.4rem 0}.sum{color:#d6dbe8;white-space:pre-line}.links{text-align:center}`;

export const APP_PACKAGE = 'com.davidgcd.backlog';
export const REPO_URL = 'https://github.com/notsogeek87/backlog';

/**
 * Chrome-on-Android intent link: opens the app when installed, otherwise sends the browser to the repository
 * (browser_fallback_url). Pure markup, so it works under the page's no-script CSP.
 */
export function androidIntent(kind, id) {
  return `intent://open/${kind}/${encodeURIComponent(id)}#Intent;scheme=backlog;package=${APP_PACKAGE};S.browser_fallback_url=${encodeURIComponent(REPO_URL)};end`;
}

/** The HTML of /open/<kind>/<id>: the "open in app" button, plus the shared title's details when the link carries them. */
export function renderOpenPage(kind, id, query = {}) {
  const appLink = androidIntent(kind, id);
  const preview = parsePreview(kind, id, query);
  const details = preview
    ? `<section class="card">${preview.image ? `<img src="${escapeHtml(preview.image)}" alt="">` : ''}
<p class="kind">${KIND_LABELS[kind]}${preview.year ? ` · ${preview.year}` : ''}</p><h2>${escapeHtml(preview.title)}</h2>
${preview.meta ? `<p class="meta">${escapeHtml(preview.meta)}</p>` : ''}${preview.genres ? `<p class="meta">${escapeHtml(preview.genres)}</p>` : ''}
${preview.rating ? `<p class="stars">★ ${preview.rating}/10</p>` : ''}${preview.summary ? `<p class="sum">${escapeHtml(preview.summary)}</p>` : ''}
${preview.links.length ? `<div class="links">${preview.links.map((l) => `<a class="l" href="${escapeHtml(l.url)}" rel="noopener noreferrer">${escapeHtml(l.label)}</a>`).join('')}</div>` : ''}</section>`
    : '';
  const title = preview ? `${preview.title} — Backlog` : 'Ouvrir dans Backlog';
  return `<!doctype html>
<html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex">
<title>${escapeHtml(title)}</title>
<style>${STYLE}</style></head>
<body><main><h1>Backlog</h1>
<p>Un ami t'a partagé une fiche. Ouvre-la dans l'app pour l'ajouter à ta liste.</p>
<a class="b" href="${escapeHtml(appLink)}">Ouvrir dans l'app</a>
${details}
<p>L'app n'est pas installée ? Le bouton t'envoie sur la page de téléchargement. Sur ordinateur : <a class="l" href="${REPO_URL}" rel="noopener noreferrer">Backlog sur GitHub</a></p></main></body></html>`;
}

export const OPEN_CSP = CSP;
