import { sql } from '../lib/db.js';
import { ConfigError, currentToken } from '../lib/igdbToken.js';
import { allowHit, clientKey } from '../lib/rateLimit.js';

/**
 * GET /api/igdb-token[?stale=<fingerprint>] → 200 {client_id, access_token, expires_in}
 * The app's only way to call IGDB: the Twitch client secret never leaves the server (see lib/igdbToken.js).
 * `stale` = first 16 hex chars of sha256(token) the app got a 401 with, to force a renewal of a revoked token.
 */
export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  if (req.method !== 'GET') {
    res.setHeader('Allow', 'GET');
    return res.status(405).json({ error: 'méthode non supportée' });
  }
  try {
    if (!(await allowHit(sql, 'igdbToken', clientKey(req)))) return res.status(429).json({ error: 'trop de demandes' });
    const stale = /^[0-9a-f]{16}$/.test(String(req.query?.stale ?? '')) ? String(req.query.stale) : '';
    const { clientId, token, expiresIn } = await currentToken({ sql, staleFingerprint: stale });
    return res.status(200).json({ client_id: clientId, access_token: token, expires_in: expiresIn });
  } catch (e) {
    console.error('igdb-token', e instanceof Error ? e.message : e);
    return res.status(e instanceof ConfigError ? 503 : 502).json({ error: 'jeton IGDB indisponible' });
  }
}
