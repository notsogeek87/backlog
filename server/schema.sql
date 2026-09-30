CREATE TABLE IF NOT EXISTS shares (
  id         text PRIMARY KEY,
  token_hash text NOT NULL,
  payload    jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  -- Rows are deleted 24 h after created_at (SHARE_TTL_HOURS in lib/shareCore.js; daily cron + on every write)
  updated_at timestamptz NOT NULL DEFAULT now()
);
