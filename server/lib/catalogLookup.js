import { currentToken } from './igdbToken.js';

/**
 * Fills the preview of /open/<kind>/<id> from the catalogue when the link carries nothing but the id, so the app
 * can share a short link (library.lielu.eu/open/movie/movie%3A603) instead of the whole title in the query string.
 * Returns the same fields an older app put in the query string (t, y, c, g, s, r, m, u): lib/openPage.js cleans them
 * as it always did. Null when the title can't be found or the catalogue doesn't answer: the page stays the plain one.
 *
 * - films / séries: TMDB, with TMDB_API_KEY (a v3 key or a v4 read token) from the Vercel environment;
 * - jeux: IGDB, with the server's Twitch token (lib/igdbToken.js);
 * - livres: Open Library, for `isbn:`, `ol:` and `work:` keys (other keys are not catalogue ids).
 */
export const TIMEOUT_MS = 3500;
const SUMMARY_MAX = 300;

const MOVIE_ID = /^(movie|tv):(\d{1,10})$/;
const GAME_ID = /^\d{1,12}$/;
const BOOK_ID = /^(?:isbn:[0-9X]{10,13}|ol:OL\d{1,12}M|work:OL\d{1,12}W)$/;

/** Whether [id] of [kind] can be looked up here (the app sends a short link only for those). */
export function isLookupable(kind, id) {
  if (kind === 'movie') return MOVIE_ID.test(id);
  if (kind === 'game') return GAME_ID.test(id);
  if (kind === 'book') return BOOK_ID.test(id);
  return false;
}

export function shorten(text, max = SUMMARY_MAX) {
  const clean = String(text ?? '').replace(/\s+/g, ' ').trim();
  return clean.length <= max ? clean : `${clean.slice(0, max - 1).trimEnd()}…`;
}

const yearOf = (date) => {
  const y = Number(String(date ?? '').match(/\d{4}/)?.[0]);
  return Number.isInteger(y) && y > 0 ? String(y) : '';
};

async function getJson(fetchImpl, url, init = {}) {
  const res = await fetchImpl(url, { ...init, signal: AbortSignal.timeout(TIMEOUT_MS) });
  if (!res.ok) throw new Error(`${new URL(url).host} ${res.status}`);
  return res.json();
}

async function lookupMovie(id, { env, fetchImpl }) {
  const [, type, tmdbId] = MOVIE_ID.exec(id);
  const key = env.TMDB_API_KEY;
  if (!key) return null;
  // A v4 read token is a JWT (eyJ…) sent as a bearer; a v3 key goes in the query string.
  const bearer = key.startsWith('eyJ');
  const url = `https://api.themoviedb.org/3/${type}/${tmdbId}?language=fr-FR&append_to_response=credits${bearer ? '' : `&api_key=${encodeURIComponent(key)}`}`;
  const m = await getJson(fetchImpl, url, bearer ? { headers: { Authorization: `Bearer ${key}` } } : {});
  const people = type === 'movie'
    ? (m.credits?.crew ?? []).filter((c) => c.job === 'Director').map((c) => c.name)
    : (m.created_by ?? []).map((c) => c.name);
  const length = type === 'movie'
    ? (m.runtime > 0 ? `${m.runtime} min` : '')
    : (m.number_of_seasons > 0 ? `${m.number_of_seasons} saison${m.number_of_seasons > 1 ? 's' : ''}` : '');
  return {
    t: m.title ?? m.name,
    y: yearOf(m.release_date ?? m.first_air_date),
    c: m.poster_path ?? '',
    g: (m.genres ?? []).map((g) => g.name).join(', '),
    s: shorten(m.overview),
    r: m.vote_average > 0 ? String(m.vote_average) : '',
    m: [people.slice(0, 3).join(', '), length].filter(Boolean).join(' · '),
  };
}

async function lookupGame(id, { sql, env, fetchImpl }) {
  if (!sql) return null;
  const { clientId, token } = await currentToken({ sql, env, fetchImpl });
  const body = `fields name,first_release_date,cover.image_id,genres.name,summary,total_rating,platforms.name,url; where id = ${id};`;
  const [g] = await getJson(fetchImpl, 'https://api.igdb.com/v4/games', {
    method: 'POST',
    headers: { 'Client-ID': clientId, Authorization: `Bearer ${token}`, 'Content-Type': 'text/plain' },
    body,
  });
  if (!g) return null;
  return {
    t: g.name,
    y: g.first_release_date ? String(new Date(g.first_release_date * 1000).getUTCFullYear()) : '',
    c: g.cover?.image_id ?? '',
    g: (g.genres ?? []).map((x) => x.name).join(', '),
    s: shorten(g.summary),
    r: g.total_rating > 0 ? String(g.total_rating / 10) : '',
    m: (g.platforms ?? []).map((p) => p.name).join(', '),
    u: g.url ?? '',
  };
}

const description = (d) => (typeof d === 'string' ? d : d?.value ?? '');

async function lookupBook(id, { fetchImpl }) {
  const [prefix, value] = id.split(':');
  const OL = 'https://openlibrary.org';
  let edition = null;
  let work = null;
  if (prefix === 'work') work = await getJson(fetchImpl, `${OL}/works/${value}.json`);
  else edition = await getJson(fetchImpl, prefix === 'isbn' ? `${OL}/isbn/${value}.json` : `${OL}/books/${value}.json`);
  const workKey = edition?.works?.[0]?.key;
  if (!work && workKey) work = await getJson(fetchImpl, `${OL}${workKey}.json`).catch(() => null);

  const authorKeys = (edition?.authors ?? work?.authors?.map((a) => a.author) ?? [])
    .map((a) => a?.key).filter((k) => /^\/authors\/OL\d+A$/.test(k ?? '')).slice(0, 3);
  const authors = (await Promise.all(authorKeys.map((k) => getJson(fetchImpl, `${OL}${k}.json`).then((a) => a.name).catch(() => null))))
    .filter(Boolean);
  const coverId = (edition?.covers ?? work?.covers ?? []).find((c) => c > 0);
  const page = prefix === 'isbn' ? `${OL}/isbn/${value}` : workKey ? `${OL}${workKey}` : prefix === 'work' ? `${OL}/works/${value}` : `${OL}/books/${value}`;
  return {
    t: edition?.title ?? work?.title,
    y: yearOf(edition?.publish_date ?? work?.first_publish_date),
    c: coverId ? `https://covers.openlibrary.org/b/id/${coverId}-L.jpg` : '',
    g: (work?.subjects ?? []).slice(0, 4).join(', '),
    s: shorten(description(edition?.description) || description(work?.description)),
    m: authors.join(', '),
    u: page,
  };
}

const LOOKUPS = { movie: lookupMovie, game: lookupGame, book: lookupBook };

export async function lookupPreview(kind, id, { sql = null, env = process.env, fetchImpl = fetch } = {}) {
  if (!isLookupable(kind, id)) return null;
  try {
    const found = await LOOKUPS[kind](id, { sql, env, fetchImpl });
    return found?.t ? found : null;
  } catch (e) {
    console.error('catalog lookup', kind, e instanceof Error ? e.message : e);
    return null;
  }
}
