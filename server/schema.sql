CREATE TABLE IF NOT EXISTS shares (
  id         text PRIMARY KEY,
  token_hash text NOT NULL,
  payload    jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  -- Links expire 24 h after updated_at (see SHARE_TTL_HOURS in lib/shareCore.js)
  updated_at timestamptz NOT NULL DEFAULT now()
);
