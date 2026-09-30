import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';

export const STATUSES = ['BACKLOG', 'PLAYED', 'COMPLETED'];
export const STATUS_LABELS = { BACKLOG: 'Backlog', PLAYED: 'Joué', COMPLETED: 'Terminé' };
export const MAX_ITEMS = 2000;

const IGDB_URL = /^https:\/\/www\.igdb\.com\/games\/[a-z0-9\-_.]+$/i;
const IMAGE_ID = /^[a-z0-9]{1,40}$/i;

/** Returns a cleaned payload, or throws Error(message) — never trusts anything the client sent. */
export function validatePayload(body) {
  if (!body || typeof body !== 'object' || !Array.isArray(body.items)) throw new Error('items manquant');
  if (body.items.length > MAX_ITEMS) throw new Error('trop de jeux');
  const items = body.items.map((raw) => {
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
  const title = typeof body.title === 'string' ? body.title.trim().slice(0, 80) : '';
  const owner = typeof body.owner === 'string' ? body.owner.trim().slice(0, 40) : '';
  return { title: title || 'Mon backlog', owner, items };
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

/** Ranked games lead as "Mon classement" (1 = most loved, in rank order); the rest are grouped by status. */
export function renderPage({ title, owner = '', items }) {
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
  return `<!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex"><title>${escapeHtml(owner ? `${title} — ${owner}` : title)}</title><style>
:root{color-scheme:dark}body{margin:0;background:#0b0f1a;color:#e8ecf5;font:16px system-ui,sans-serif;padding:16px 16px 48px;max-width:1000px;margin-inline:auto}
h1{font-size:1.6rem;margin-bottom:.2rem}.owner{margin:0;opacity:.75}h2{margin-top:2rem}small{opacity:.6;font-weight:400}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(130px,1fr));gap:12px}
.card{display:block;color:inherit;text-decoration:none;background:#151b2c;border-radius:12px;overflow:hidden}
.cover{position:relative}
.card img,.noimg{width:100%;aspect-ratio:3/4;object-fit:cover;background:#1e2740;display:block}
.rank{position:absolute;top:6px;left:6px;min-width:1.7em;padding:.15em .4em;border-radius:999px;background:#22d3ee;color:#04222a;font-size:.95rem;text-align:center}
.card span{display:block;padding:8px;font-size:.85rem}
</style></head><body><h1>${escapeHtml(title)}</h1>${owner ? `<p class="owner">par <strong>${escapeHtml(owner)}</strong></p>` : ''}${rankingSection}${sections || (rankingSection ? '' : '<p>Ce backlog est vide.</p>')}</body></html>`;
}
