import { sql } from '../lib/db.js';
import { SHARE_TTL_HOURS, renderPage } from '../lib/shareCore.js';

export default async function handler(req, res) {
  const id = String(req.query.id ?? '');
  const rows = /^[A-Za-z0-9_-]{6,32}$/.test(id) ? await sql`SELECT payload FROM shares WHERE id = ${id} AND created_at > now() - make_interval(hours => ${SHARE_TTL_HOURS})` : [];
  if (!rows.length) {
    res.status(404).setHeader('Content-Type', 'text/html; charset=utf-8');
    return res.send('<!doctype html><meta charset="utf-8"><title>Introuvable</title><p>Ce lien n\'existe pas ou a expiré.</p>');
  }
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.setHeader('Cache-Control', 'public, max-age=0, s-maxage=60');
  res.setHeader('Content-Security-Policy', "default-src 'none'; img-src https://images.igdb.com https://image.tmdb.org https://covers.openlibrary.org https://*.archive.org https://books.google.com; style-src 'unsafe-inline'");
  res.setHeader('Referrer-Policy', 'no-referrer');
  return res.status(200).send(renderPage(rows[0].payload));
}
