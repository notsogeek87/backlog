import { sql } from './db.js';
import { SHARE_TTL_HOURS } from './shareCore.js';

/** Physically deletes every link older than the TTL. Reads also filter on age, so an undeleted one is never served. */
export const purgeExpired = () => sql`DELETE FROM shares WHERE created_at <= now() - make_interval(hours => ${SHARE_TTL_HOURS})`;
