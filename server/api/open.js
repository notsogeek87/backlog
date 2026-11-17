import { lookupPreview } from '../lib/catalogLookup.js';
import { sql } from '../lib/db.js';
import { KINDS, OPEN_CSP, renderOpenPage } from '../lib/openPage.js';

/**
 * GET /open/<game|movie|book>/<id>[?t=&y=&c=&g=&s=&r=&m=&u=]  — landing page of a detail page shared from the app.
 * On Android with the app installed the link opens the app directly; otherwise this page offers the
 * backlog:// twin ("Ouvrir dans l'app") and shows the title's details. Current apps send the bare id and the
 * details are read from the catalogue (lib/catalogLookup.js, cached a day by the CDN); older links still carry
 * them in the query string (see lib/openPage.js). Nothing is stored.
 */
export default async function handler(req, res) {
  const kind = String(req.query.kind ?? '');
  const id = String(req.query.id ?? '');
  if (!KINDS.has(kind) || !id || id.length > 100 || /[^\w:.-]/.test(id)) {
    res.status(404).setHeader('Content-Type', 'text/html; charset=utf-8');
    return res.send('<!doctype html><meta charset="utf-8"><title>Introuvable</title><p>Ce lien n\'existe pas.</p>');
  }
  let query = req.query;
  let maxAge = 300;
  if (!query.t) {
    const found = await lookupPreview(kind, id, { sql });
    // A found title is cached a day; a miss (catalogue down) is retried sooner.
    if (found) { query = { ...query, ...found }; maxAge = 86400; } else maxAge = 60;
  }
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.setHeader('Cache-Control', `public, max-age=0, s-maxage=${maxAge}, stale-while-revalidate=86400`);
  res.setHeader('Content-Security-Policy', OPEN_CSP);
  res.setHeader('Referrer-Policy', 'no-referrer');
  return res.status(200).send(renderOpenPage(kind, id, query));
}
