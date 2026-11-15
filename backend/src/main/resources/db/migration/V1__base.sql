-- Convenções (data-model.md): id = UUIDv7 em texto; *_at = UTC ISO-8601; *_local_date = America/Sao_Paulo.
-- Nenhuma coluna guarda segredo em texto claro.

-- Linha única: senha do app (Argon2id) e controle de tentativas (D-06).
CREATE TABLE app_credential (
  id              INTEGER PRIMARY KEY CHECK (id = 1),
  password_hash   TEXT    NOT NULL,
  failed_attempts INTEGER NOT NULL DEFAULT 0,
  locked_until    TEXT,
  created_at      TEXT    NOT NULL,
  updated_at      TEXT    NOT NULL
);

-- Append-only (NFR-007). Triggers impedem UPDATE/DELETE.
CREATE TABLE activity_log (
  id           TEXT PRIMARY KEY,
  task_run_id  TEXT,
  author       TEXT NOT NULL CHECK (author IN ('user', 'system') OR author LIKE 'bot:%'),
  action       TEXT NOT NULL,
  target_type  TEXT,
  target_id    TEXT,
  evidence_ref TEXT,
  result       TEXT NOT NULL CHECK (result IN ('ok', 'failed', 'refused', 'skipped')),
  error        TEXT,
  at           TEXT NOT NULL
);
CREATE INDEX activity_log_at ON activity_log (at);
CREATE INDEX activity_log_task_run ON activity_log (task_run_id);

CREATE TRIGGER activity_log_no_update BEFORE UPDATE ON activity_log
BEGIN SELECT RAISE(ABORT, 'activity_log e append-only'); END;
CREATE TRIGGER activity_log_no_delete BEFORE DELETE ON activity_log
BEGIN SELECT RAISE(ABORT, 'activity_log e append-only'); END;

-- FR-017: falha atualiza só last_attempt_at.
CREATE TABLE integration_status (
  integration         TEXT PRIMARY KEY CHECK (integration IN ('openbot', 'gmail') OR integration LIKE 'source:%'),
  last_attempt_at     TEXT,
  last_success_at     TEXT,
  state               TEXT NOT NULL CHECK (state IN ('ok', 'degraded', 'disconnected', 'paused')),
  detail              TEXT,
  tracking_started_at TEXT,
  created_at          TEXT NOT NULL,
  updated_at          TEXT NOT NULL
);
