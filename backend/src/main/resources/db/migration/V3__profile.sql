CREATE TABLE profile_version (
  id          TEXT PRIMARY KEY,
  source_file TEXT NOT NULL,
  source_hash TEXT NOT NULL,
  status      TEXT NOT NULL CHECK (status IN ('imported', 'reviewed')),
  reviewed_at TEXT,
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL
);

-- Invariante (FR-020, RB-006): study_recommended nunca é evidência de competência.
CREATE TABLE profile_fact (
  id                 TEXT PRIMARY KEY,
  profile_version_id TEXT NOT NULL REFERENCES profile_version (id),
  kind               TEXT NOT NULL CHECK (kind IN ('experience', 'skill', 'education', 'language', 'preference')),
  statement          TEXT NOT NULL,
  origin             TEXT NOT NULL CHECK (origin IN ('documented', 'declared', 'study_recommended')),
  origin_ref         TEXT,
  created_at         TEXT NOT NULL,
  updated_at         TEXT NOT NULL
);
CREATE INDEX profile_fact_version ON profile_fact (profile_version_id);

-- Linha única, versionada por `version`. Listas em JSON.
CREATE TABLE search_criteria (
  id                         INTEGER PRIMARY KEY CHECK (id = 1),
  target_roles               TEXT    NOT NULL DEFAULT '[]',
  stack_keywords             TEXT    NOT NULL DEFAULT '[]',
  remote_preferred           INTEGER NOT NULL DEFAULT 1,
  hybrid_regions             TEXT    NOT NULL DEFAULT '[]',
  clt_min_monthly_brl        INTEGER NOT NULL,
  pj_min_monthly_brl         INTEGER NOT NULL,
  accept_junior_if_justified INTEGER NOT NULL DEFAULT 1,
  daily_search_time_local    TEXT    NOT NULL DEFAULT '07:30',
  deadline_highlight_days    INTEGER NOT NULL DEFAULT 7,
  urgent_hours               INTEGER NOT NULL DEFAULT 48,
  email_digest_enabled       INTEGER NOT NULL DEFAULT 0,
  version                    INTEGER NOT NULL DEFAULT 1,
  created_at                 TEXT    NOT NULL,
  updated_at                 TEXT    NOT NULL
);

CREATE TABLE company (
  id                  TEXT PRIMARY KEY,
  name                TEXT NOT NULL UNIQUE COLLATE NOCASE,
  tier                TEXT NOT NULL CHECK (tier IN ('priority', 'suggested')),
  promoted_by_user_at TEXT,
  notes               TEXT,
  careers_adapter     TEXT,
  created_at          TEXT NOT NULL,
  updated_at          TEXT NOT NULL
);
