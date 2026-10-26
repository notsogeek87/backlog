import { KINDS, OPEN_CSP, renderOpenPage } from '../lib/openPage.js';

/**
 * GET /open/<game|movie|book>/<id>[?t=&y=&c=&g=&s=&r=&m=&u=]  — landing page of a detail page shared from the app.
 * On Android with the app installed the link opens the app directly; otherwise this page offers the
 * backlog:// twin ("Ouvrir dans l'app") and shows the title's details carried by the link's query string
 * (see lib/openPage.js). Nothing is stored or looked up.
 */
export default function handler(req, res) {
  const kind = String(req.query.kind ?? '');
  const id = String(req.query.id ?? '');
  if (!KINDS.has(kind) || !id || id.length > 100 || /[^\w:.-]/.test(id)) {
    res.status(404).setHeader('Content-Type', 'text/html; charset=utf-8');
    return res.send('<!doctype html><meta charset="utf-8"><title>Introuvable</title><p>Ce lien n\'existe pas.</p>');
  }
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.setHeader('Cache-Control', 'public, max-age=0, s-maxage=300');
  res.setHeader('Content-Security-Policy', OPEN_CSP);
  res.setHeader('Referrer-Policy', 'no-referrer');
  return res.status(200).send(renderOpenPage(kind, id, req.query));
}
