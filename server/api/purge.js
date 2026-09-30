import { purgeExpired } from '../lib/purge.js';

/** Daily Vercel cron (see vercel.json). When CRON_SECRET is set, Vercel sends it as a bearer token. */
export default async function handler(req, res) {
  const secret = process.env.CRON_SECRET;
  if (secret && req.headers.authorization !== `Bearer ${secret}`) return res.status(401).json({ error: 'non autorisé' });
  await purgeExpired();
  return res.status(204).end();
}
