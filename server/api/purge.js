import { purgeAll } from '../lib/purge.js';

/**
 * Daily Vercel cron (see vercel.json). Vercel sends CRON_SECRET as a bearer token; without that variable the
 * endpoint refuses every call (it must never be an open, free database call).
 */
export default async function handler(req, res) {
  const secret = process.env.CRON_SECRET;
  if (!secret || req.headers.authorization !== `Bearer ${secret}`) return res.status(401).json({ error: 'non autorisé' });
  await purgeAll();
  return res.status(204).end();
}
