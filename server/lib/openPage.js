import { EMBLEM_DATA_URI } from './brand.js';
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
    links.push({ label: 'IMDb', url: `https://www.imdb.com/find/?s=tt&q=${search}` });
  } else if (kind === 'game') {
    links.push(IGDB_URL.test(catalogUrl)
      ? { label: 'Fiche IGDB', url: catalogUrl }
      : { label: 'IGDB', url: `https://www.igdb.com/search?type=1&q=${search}` });
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

/**
 * "Glass night", the app's look (docs/architecture/design-system.md): night background with cyan / violet halos,
 * translucent glass surfaces, cyan→blue accent. No script and no web font (CSP): the system font, like the app.
 */
const STYLE = `:root{color-scheme:dark;--bg:#050a14;--text:#eef2fb;--muted:#9aa6bf;--faint:#6b7790;
--glass:rgba(255,255,255,.06);--glass-hi:rgba(255,255,255,.10);--border:rgba(255,255,255,.12);
--cyan:#38d6f5;--blue:#4f7bff;--on-accent:#04121f;--halo1:rgba(56,214,245,.16);--halo2:rgba(124,92,255,.16);--shadow:0 18px 40px rgba(0,0,0,.55)}
@media (prefers-color-scheme:light){:root{color-scheme:light;--bg:#eef2f9;--text:#0d1424;--muted:#4c5872;--faint:#6b7790;
--glass:rgba(255,255,255,.72);--glass-hi:#fff;--border:rgba(13,20,36,.12);--cyan:#0b8fb3;--blue:#2f5be0;--on-accent:#fff;
--halo1:rgba(11,143,179,.14);--halo2:rgba(124,92,255,.12);--shadow:0 14px 30px rgba(13,20,36,.18)}}
*{box-sizing:border-box}html{-webkit-text-size-adjust:100%}
body{margin:0;min-height:100vh;background:var(--bg);color:var(--text);font:16px/1.5 Roboto,"Segoe UI",system-ui,sans-serif;
background-image:radial-gradient(60rem 30rem at 110% -10%,var(--halo1),transparent 60%),radial-gradient(50rem 30rem at -20% 30%,var(--halo2),transparent 60%);background-attachment:fixed}
a{color:inherit}
.backdrop{position:fixed;inset:0 0 auto;height:70vh;z-index:0;overflow:hidden;pointer-events:none}
.backdrop img{width:100%;height:100%;object-fit:cover;filter:blur(36px) saturate(1.3);opacity:.45;transform:scale(1.2)}
.backdrop::after{content:"";position:absolute;inset:0;background:linear-gradient(to bottom,transparent 0%,var(--bg) 92%)}
@media (prefers-color-scheme:light){.backdrop img{opacity:.3}}
main{position:relative;z-index:1;max-width:34rem;margin:0 auto;padding:20px 16px 40px}
.brand{display:flex;align-items:center;gap:10px;text-decoration:none;font-weight:700;font-size:1.15rem;letter-spacing:-.01em;min-height:44px}
.brand img{width:36px;height:auto}
.brand b{background:linear-gradient(90deg,var(--cyan),var(--blue));-webkit-background-clip:text;background-clip:text;color:transparent}
.hero{display:grid;grid-template-columns:minmax(112px,40%) 1fr;gap:18px;align-items:end;margin-top:22px}
.cover{width:100%;aspect-ratio:2/3;object-fit:cover;border-radius:16px;border:1px solid var(--border);box-shadow:var(--shadow);background:var(--glass)}
.hero.solo{grid-template-columns:1fr}
.from{margin:0 0 6px;color:var(--muted);font-size:.95rem}
h1{margin:0;font-size:clamp(1.7rem,7vw,2.4rem);line-height:1.1;letter-spacing:-.02em;text-wrap:balance}
.facts{margin:8px 0 0;color:var(--muted);font-size:.95rem}
.score{display:flex;align-items:center;gap:10px;margin-top:12px;max-width:16rem}
.score .bar{flex:1;height:6px;border-radius:99px;background:var(--glass-hi);overflow:hidden}
.score .bar i{display:block;height:100%;border-radius:99px;background:linear-gradient(90deg,var(--cyan),var(--blue));box-shadow:0 0 12px var(--cyan)}
.score span{font-weight:700;font-variant-numeric:tabular-nums}
.chips{display:flex;flex-wrap:wrap;gap:8px;margin:20px 0 0;padding:0;list-style:none}
.chips li{padding:6px 12px;border-radius:99px;background:var(--glass);border:1px solid var(--border);font-size:.875rem;color:var(--muted)}
.sum{margin:18px 0 0;color:var(--text);opacity:.88;text-wrap:pretty}
.cta{display:flex;align-items:center;justify-content:center;min-height:52px;margin-top:24px;padding:0 24px;border-radius:16px;
background:linear-gradient(90deg,var(--cyan),var(--blue));color:var(--on-accent);font-weight:700;font-size:1.05rem;text-decoration:none;box-shadow:0 10px 28px rgba(56,214,245,.25)}
.cta:active{transform:scale(.98)}
.links{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px}
.links a{flex:1 1 9rem;display:flex;align-items:center;justify-content:center;gap:6px;min-height:44px;padding:0 14px;border-radius:14px;
background:var(--glass);border:1px solid var(--border);text-decoration:none;font-weight:600;font-size:.95rem}
.links a:hover,.get a:hover{background:var(--glass-hi)}
a:focus-visible{outline:2px solid var(--cyan);outline-offset:3px}
.get{margin-top:32px;padding:18px;border-radius:20px;background:var(--glass);border:1px solid var(--border);display:grid;grid-template-columns:auto 1fr;gap:4px 14px;align-items:center}
.get img{grid-row:span 2;width:44px;height:auto}
.get h2{margin:0;font-size:1.05rem}
.get p{margin:0;color:var(--muted);font-size:.9rem}
.get a{grid-column:1/-1;margin-top:12px;display:flex;align-items:center;justify-content:center;min-height:44px;border-radius:14px;border:1px solid var(--border);
text-decoration:none;font-weight:600;color:var(--cyan)}
@media (prefers-reduced-motion:reduce){.cta:active{transform:none}}`;

export const APP_PACKAGE = 'com.davidgcd.backlog';
export const REPO_URL = 'https://github.com/notsogeek87/backlog';
export const RELEASES_URL = `${REPO_URL}/releases/latest`;

/**
 * Chrome-on-Android intent link: opens the app when installed, otherwise sends the browser to the latest release (APK download)
 * (browser_fallback_url). Pure markup, so it works under the page's no-script CSP.
 */
export function androidIntent(kind, id) {
  return `intent://open/${kind}/${encodeURIComponent(id)}#Intent;scheme=backlog;package=${APP_PACKAGE};S.browser_fallback_url=${encodeURIComponent(RELEASES_URL)};end`;
}

const NOUNS = { game: 'ce jeu', movie: 'ce film', tv: 'cette série', book: 'ce livre' };
const frNumber = (n) => n.toFixed(1).replace('.', ',');

/** The HTML of /open/<kind>/<id>: the title's details when the link carries them (or the catalogue gave them), and the "open in app" button. */
export function renderOpenPage(kind, id, query = {}) {
  const appLink = escapeHtml(androidIntent(kind, id));
  const preview = parsePreview(kind, id, query);
  const series = kind === 'movie' && id.startsWith('tv:');
  const label = series ? 'Série' : KIND_LABELS[kind];
  const noun = NOUNS[series ? 'tv' : kind];
  const title = preview ? `${preview.title} — Backlog` : 'Ouvrir dans Backlog';
  // Link card in chat apps (WhatsApp, Signal…): title, one line and the poster.
  const card = preview ? [
    ['og:title', title],
    ['og:description', [preview.year, preview.meta].filter(Boolean).join(' · ') || label],
    ...(preview.image ? [['og:image', preview.image]] : []),
  ].map(([p, v]) => `<meta property="${p}" content="${escapeHtml(String(v))}">`).join('') : '';

  const body = preview ? `${preview.image ? `<div class="backdrop" aria-hidden="true"><img src="${escapeHtml(preview.image)}" alt=""></div>` : ''}
<main>${BRAND}
<section class="hero${preview.image ? '' : ' solo'}">${preview.image ? `<img class="cover" src="${escapeHtml(preview.image)}" alt="${escapeHtml(`Affiche de ${preview.title}`)}">` : ''}
<div><p class="from">On te recommande ${noun}</p><h1>${escapeHtml(preview.title)}</h1>
<p class="facts">${[label, preview.year].filter(Boolean).join(' · ')}${preview.meta ? `<br>${escapeHtml(preview.meta)}` : ''}</p>
${preview.rating ? `<div class="score" role="img" aria-label="Note ${frNumber(preview.rating)} sur 10"><div class="bar"><i style="width:${preview.rating * 10}%"></i></div><span>${frNumber(preview.rating)}</span></div>` : ''}</div></section>
${preview.genres ? `<ul class="chips">${preview.genres.split(',').map((g) => g.trim()).filter(Boolean).slice(0, 5).map((g) => `<li>${escapeHtml(g)}</li>`).join('')}</ul>` : ''}
${preview.summary ? `<p class="sum">${escapeHtml(preview.summary)}</p>` : ''}
<a class="cta" href="${appLink}">Ouvrir dans l'app Backlog</a>
${preview.links.length ? `<nav class="links" aria-label="Voir ailleurs">${preview.links.map((l) => `<a href="${escapeHtml(l.url)}" rel="noopener noreferrer">${escapeHtml(l.label)} <span aria-hidden="true">↗</span></a>`).join('')}</nav>` : ''}
${GET_APP}</main>`
    : `<main>${BRAND}
<section class="hero solo"><div><p class="from">On t'a partagé une fiche</p><h1>Ouvre-la dans Backlog</h1>
<p class="facts">Elle s'ouvrira directement dans l'app, prête à être ajoutée à ta liste.</p></div></section>
<a class="cta" href="${appLink}">Ouvrir dans l'app Backlog</a>
${GET_APP}</main>`;

  return `<!doctype html>
<html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex">${card}
<meta name="theme-color" content="#050a14"><link rel="icon" href="${EMBLEM_DATA_URI}">
<title>${escapeHtml(title)}</title>
<style>${STYLE}</style></head>
<body>${body}</body></html>`;
}

const BRAND = `<a class="brand" href="${REPO_URL}" rel="noopener noreferrer"><img src="${EMBLEM_DATA_URI}" alt=""><span>back<b>log</b></span></a>`;

/** Bottom card for someone without the app. */
const GET_APP = `<aside class="get"><img src="${EMBLEM_DATA_URI}" alt="">
<h2>Pas encore Backlog ?</h2>
<p>Les films, séries, jeux et livres qui t'attendent, rangés au même endroit. Sur Android.</p>
<a href="${RELEASES_URL}" rel="noopener noreferrer">Télécharger l'app</a></aside>`;

/** The shared CSP, plus inline images for the emblem. */
export const OPEN_CSP = CSP.replace('img-src ', 'img-src data: ');
