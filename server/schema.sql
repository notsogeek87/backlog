CREATE TABLE IF NOT EXISTS shares (
  id         text PRIMARY KEY,
  token_hash text NOT NULL,
  payload    jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
