-- Idempotency-Key em mutações (contracts/panel-api.md, NFR-004).
CREATE TABLE idempotency_key (
  idem_key      TEXT PRIMARY KEY,
  method        TEXT    NOT NULL,
  path          TEXT    NOT NULL,
  status        INTEGER NOT NULL,
  content_type  TEXT,
  response_body BLOB,
  created_at    TEXT    NOT NULL
);
CREATE INDEX idempotency_key_created ON idempotency_key (created_at);
