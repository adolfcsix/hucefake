CREATE TABLE fm05_idempotency_keys (
  actor_id BIGINT NOT NULL REFERENCES users ON DELETE CASCADE,
  key TEXT NOT NULL,
  fingerprint TEXT NOT NULL,
  response JSONB NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '24 hours'),
  PRIMARY KEY (actor_id, key)
);
CREATE INDEX fm05_idempotency_expiry_idx ON fm05_idempotency_keys(expires_at);
