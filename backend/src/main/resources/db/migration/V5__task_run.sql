-- TaskRun (data-model.md). bot_id/bot_config_id ficam sem FK: tarefas de sistema (busca por
-- regras, backup) não têm bot, e a tabela `bot` só nasce em V7.
CREATE TABLE task_run (
  id                TEXT PRIMARY KEY,
  bot_id            TEXT,
  bot_config_id     TEXT,
  kind              TEXT NOT NULL,
  trigger_kind      TEXT NOT NULL CHECK (trigger_kind IN ('routine', 'manual', 'coordinator')),
  idempotency_key   TEXT NOT NULL UNIQUE,
  state             TEXT NOT NULL CHECK (state IN ('queued', 'running', 'completed', 'failed', 'paused')),
  pause_reason      TEXT CHECK (pause_reason IN ('provider_limit', 'needs_person', 'source_unavailable', 'gmail_disconnected', 'machine_offline')),
  checkpoint        TEXT NOT NULL DEFAULT '{}',
  sources_consulted TEXT NOT NULL DEFAULT '[]',
  sources_failed    TEXT NOT NULL DEFAULT '[]',
  error_summary     TEXT,
  started_at        TEXT,
  finished_at       TEXT,
  created_at        TEXT NOT NULL,
  updated_at        TEXT NOT NULL,
  CHECK ((state = 'paused') = (pause_reason IS NOT NULL))
);
CREATE INDEX task_run_state ON task_run (state);
CREATE INDEX task_run_bot ON task_run (bot_id);

-- Rotinas agendadas pelo backend (D-10). Rotinas de bots apontam para o bot em owner_bot_id (V7).
CREATE TABLE routine (
  id           TEXT PRIMARY KEY,
  purpose      TEXT    NOT NULL,
  owner_bot_id TEXT,
  kind         TEXT    NOT NULL CHECK (kind IN ('daily')),
  time_local   TEXT    NOT NULL,
  enabled      INTEGER NOT NULL DEFAULT 1,
  next_run_at  TEXT    NOT NULL,
  last_run_at  TEXT,
  created_at   TEXT    NOT NULL,
  updated_at   TEXT    NOT NULL
);

-- Ocorrências não executadas (computador desligado) — exibidas na Início (missedRoutines).
CREATE TABLE routine_missed (
  routine_id    TEXT    NOT NULL REFERENCES routine (id),
  scheduled_for TEXT    NOT NULL,
  recorded_at   TEXT    NOT NULL,
  acknowledged  INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (routine_id, scheduled_for)
);
