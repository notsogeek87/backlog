import { sql } from '../lib/db.js';
import { purgeAll, purgeExpired } from '../lib/purge.js';
import { allowHit, clientKey } from '../lib/rateLimit.js';
import {
  MAX_BODY_BYTES, MAX_LIVE_SHARES, SHARE_TTL_HOURS, ValidationError, hashToken, newId, newToken, tokenMatches, validatePayload,
} from '../lib/shareCore.js';

const baseUrl = (req) => `https://${req.headers['x-forwarded-host'] ?? req.headers.host}`;

/**
 * POST /api/share            body {title?, items[]}  → 201 {id, token, url}
 * PUT  /api/share?id=<id>    header x-share-token    → 200 {id, url}   (same link, new content; expiry NOT renewed)
 * A link is deleted SHARE_TTL_HOURS (lib/shareCore.js) after its creation, whatever happens in between;
 * a PUT / DELETE on an expired link answers 404 and the app then creates a new one.
 * DELETE /api/share?id=<id>  header x-share-token    → 204            (link stops working)
 * Creations are limited per client (lib/rateLimit.js, 429) and globally (MAX_LIVE_SHARES, 503).
 */
export default async function handler(req, res) {
  try {
    if (Number(req.headers['content-length'] ?? 0) > MAX_BODY_BYTES) return res.status(413).json({ error: 'liste trop volumineuse' });
    if (req.method === 'POST') {
      const payload = validatePayload(req.body);
      if (!(await allowHit(sql, 'share', clientKey(req)))) return res.status(429).json({ error: 'trop de liens créés, réessaie plus tard' });
      // Also clears old rate-limit counters, so housekeeping does not depend on the daily cron alone.
      await purgeAll();
      const [{ live }] = await sql`SELECT count(*)::int AS live FROM shares`;
      if (live >= MAX_LIVE_SHARES) return res.status(503).json({ error: 'service saturé, réessaie plus tard' });
      const id = newId();
      const token = newToken();
      await sql`INSERT INTO shares (id, token_hash, payload) VALUES (${id}, ${hashToken(token)}, ${JSON.stringify(payload)}::jsonb)`;
      return res.status(201).json({ id, token, url: `${baseUrl(req)}/b/${id}` });
    }
    if (req.method === 'PUT' || req.method === 'DELETE') {
      const id = String(req.query.id ?? '');
      const token = String(req.headers['x-share-token'] ?? '');
      await purgeExpired();
      const rows = await sql`SELECT token_hash FROM shares WHERE id = ${id} AND created_at > now() - make_interval(hours => ${SHARE_TTL_HOURS})`;
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
    if (e instanceof ValidationError) return res.status(400).json({ error: e.message });
    console.error('share', e instanceof Error ? e.message : e);
    return res.status(500).json({ error: 'erreur serveur' });
  }
}
