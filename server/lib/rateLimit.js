import { createHash } from 'node:crypto';

/**
 * Fixed-window rate limiting stored in Postgres (table `rate_limits`, see schema.sql), so every serverless
 * instance shares the same counters. The client is identified by a hash of its IP: the raw address is never stored.
 */
export const LIMITS = {
  /** Link creations per client and per hour. A normal user republishes the same link (PUT), which is not counted. */
  share: { max: 20, windowMinutes: 60 },
  /** IGDB token requests per client and per hour (the app asks once per launch, or after a 401). */
  igdbToken: { max: 30, windowMinutes: 60 },
};

/** The calling client's IP as Vercel reports it (first hop of x-forwarded-for), hashed. */
export function clientKey(req) {
  const forwarded = String(req.headers?.['x-forwarded-for'] ?? '').split(',')[0].trim();
  const ip = forwarded || String(req.headers?.['x-real-ip'] ?? '') || req.socket?.remoteAddress || 'unknown';
  return createHash('sha256').update(`backlog-rate:${ip}`).digest('hex').slice(0, 32);
}

/** Start of the fixed window that [now] falls in. */
export function windowStart(now, windowMinutes) {
  const ms = windowMinutes * 60_000;
  return new Date(Math.floor(now.getTime() / ms) * ms);
}

/**
 * Counts one hit for [client] in [scope] and tells whether it stays within the limit.
 * [sql] is the Neon tagged-template function (injected so tests can fake it).
 */
export async function allowHit(sql, scope, client, now = new Date()) {
  const { max, windowMinutes } = LIMITS[scope];
  const bucket = windowStart(now, windowMinutes).toISOString();
  const rows = await sql`INSERT INTO rate_limits (scope, client, bucket, hits) VALUES (${scope}, ${client}, ${bucket}, 1)
    ON CONFLICT (scope, client, bucket) DO UPDATE SET hits = rate_limits.hits + 1 RETURNING hits`;
  return Number(rows[0]?.hits ?? 1) <= max;
}

/** Counters older than a day are useless (windows are at most an hour). */
export const purgeRateLimits = (sql) => sql`DELETE FROM rate_limits WHERE bucket < now() - interval '1 day'`;
