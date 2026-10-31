import { createHash } from 'node:crypto';

/**
 * Twitch app access token for IGDB (client-credentials grant). The client SECRET only lives in the Vercel
 * environment (TWITCH_CLIENT_ID / TWITCH_CLIENT_SECRET): the app asks this server for the token instead of
 * shipping the secret in its APK. One token is shared by every instance through the `app_tokens` table, so
 * cold starts do not mint a new one each time.
 */
export const TOKEN_NAME = 'igdb';
/** Renew a little before Twitch's expiry, so a handed-out token is always valid for at least this long. */
export const MIN_REMAINING_SECONDS = 24 * 3600;

export const tokenFingerprint = (token) => createHash('sha256').update(String(token)).digest('hex').slice(0, 16);

export class ConfigError extends Error {}

async function requestTwitchToken(fetchImpl, clientId, clientSecret) {
  const url = `https://id.twitch.tv/oauth2/token?client_id=${encodeURIComponent(clientId)}&client_secret=${encodeURIComponent(clientSecret)}&grant_type=client_credentials`;
  const res = await fetchImpl(url, { method: 'POST' });
  if (!res.ok) throw new Error(`twitch token ${res.status}`);
  const body = await res.json();
  if (typeof body.access_token !== 'string' || !Number.isFinite(body.expires_in)) throw new Error('twitch token: réponse invalide');
  return { token: body.access_token, expiresAt: new Date(Date.now() + body.expires_in * 1000) };
}

/** Whether Twitch still accepts [token] (GET /oauth2/validate answers 401 for a revoked or expired one). */
async function isStillValid(fetchImpl, token) {
  const res = await fetchImpl('https://id.twitch.tv/oauth2/validate', { headers: { Authorization: `OAuth ${token}` } });
  return res.ok;
}

/**
 * Returns {clientId, token, expiresIn}. [staleFingerprint] is sent by an app that just got a 401 from IGDB with
 * the token it holds: when it is the current one and Twitch confirms it is dead, a new token is minted.
 */
export async function currentToken({ sql, env = process.env, fetchImpl = fetch, staleFingerprint = '', now = new Date() }) {
  const clientId = env.TWITCH_CLIENT_ID;
  const clientSecret = env.TWITCH_CLIENT_SECRET;
  if (!clientId || !clientSecret) throw new ConfigError('TWITCH_CLIENT_ID / TWITCH_CLIENT_SECRET manquants');

  const rows = await sql`SELECT token, expires_at FROM app_tokens WHERE name = ${TOKEN_NAME}`;
  let stored = rows[0] ? { token: rows[0].token, expiresAt: new Date(rows[0].expires_at) } : null;

  const fresh = stored && stored.expiresAt.getTime() - now.getTime() > MIN_REMAINING_SECONDS * 1000;
  const reportedStale = stored && staleFingerprint && staleFingerprint === tokenFingerprint(stored.token);
  if (!fresh || (reportedStale && !(await isStillValid(fetchImpl, stored.token)))) {
    stored = await requestTwitchToken(fetchImpl, clientId, clientSecret);
    await sql`INSERT INTO app_tokens (name, token, expires_at) VALUES (${TOKEN_NAME}, ${stored.token}, ${stored.expiresAt.toISOString()})
      ON CONFLICT (name) DO UPDATE SET token = EXCLUDED.token, expires_at = EXCLUDED.expires_at`;
  }
  return {
    clientId,
    token: stored.token,
    expiresIn: Math.max(0, Math.floor((stored.expiresAt.getTime() - now.getTime()) / 1000)),
  };
}
