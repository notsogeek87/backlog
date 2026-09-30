import { sql } from '../lib/db.js';
import { PURGE_AFTER_DAYS, hashToken, newId, newToken, tokenMatches, validatePayload } from '../lib/shareCore.js';

const baseUrl = (req) => `https://${req.headers['x-forwarded-host'] ?? req.headers.host}`;

/**
 * POST /api/share            body {title?, items[]}  → 201 {id, token, url}
 * PUT  /api/share?id=<id>    header x-share-token    → 200 {id, url}   (same link, new content, expiry renewed)
 * Links expire after SHARE_TTL_HOURS (lib/shareCore.js) without a publish.
 * DELETE /api/share?id=<id>  header x-share-token    → 204            (link stops working)
 */
export default async function handler(req, res) {
  try {
    if (req.method === 'POST') {
      const payload = validatePayload(req.body);
      // Housekeeping: drop links that expired long ago (recent ones can still be renewed by their owner).
      await sql`DELETE FROM shares WHERE updated_at < now() - make_interval(days => ${PURGE_AFTER_DAYS})`;
      const id = newId();
      const token = newToken();
      await sql`INSERT INTO shares (id, token_hash, payload) VALUES (${id}, ${hashToken(token)}, ${JSON.stringify(payload)}::jsonb)`;
      return res.status(201).json({ id, token, url: `${baseUrl(req)}/b/${id}` });
    }
    if (req.method === 'PUT' || req.method === 'DELETE') {
      const id = String(req.query.id ?? '');
      const token = String(req.headers['x-share-token'] ?? '');
      const rows = await sql`SELECT token_hash FROM shares WHERE id = ${id}`;
      if (!rows.length || !token || !tokenMatches(token, rows[0].token_hash)) return res.status(404).json({ error: 'introuvable' });
      if (req.method === 'DELETE') {
        await sql`DELETE FROM shares WHERE id = ${id}`;
        return res.status(204).end();
      }
      const payload = validatePayload(req.body);
      await sql`UPDATE shares SET payload = ${JSON.stringify(payload)}::jsonb, updated_at = now() WHERE id = ${id}`;
      return res.status(200).json({ id, url: `${baseUrl(req)}/b/${id}` });
    }
    res.setHeader('Allow', 'POST, PUT, DELETE');
    return res.status(405).json({ error: 'méthode non supportée' });
  } catch (e) {
    const clientError = e instanceof Error && !e.message.includes('DATABASE') && e.constructor === Error;
    return res.status(clientError ? 400 : 500).json({ error: clientError ? e.message : 'erreur serveur' });
  }
}
