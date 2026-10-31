-- Idempotent : se rejoue tel quel sur la base Neon (psql -f schema.sql, ou console SQL Neon).
CREATE TABLE IF NOT EXISTS shares (
  id         text PRIMARY KEY,
  token_hash text NOT NULL,
  payload    jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  -- Rows are deleted 24 h after created_at (SHARE_TTL_HOURS in lib/shareCore.js; daily cron + on every write)
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS shares_created_at_idx ON shares (created_at);

-- Compteurs de limite de débit (lib/rateLimit.js) : fenêtre fixe par client (hash de l'IP, jamais l'IP brute).
CREATE TABLE IF NOT EXISTS rate_limits (
  scope  text NOT NULL,
  client text NOT NULL,
  bucket timestamptz NOT NULL,
  hits   integer NOT NULL DEFAULT 0,
  PRIMARY KEY (scope, client, bucket)
);

-- Jeton d'application partagé entre les instances (lib/igdbToken.js : jeton Twitch pour IGDB).
CREATE TABLE IF NOT EXISTS app_tokens (
  name       text PRIMARY KEY,
  token      text NOT NULL,
  expires_at timestamptz NOT NULL
);
