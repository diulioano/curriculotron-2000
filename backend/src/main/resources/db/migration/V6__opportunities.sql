CREATE TABLE opportunity (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  company_name TEXT NOT NULL,
  location TEXT,
  work_mode TEXT NOT NULL DEFAULT 'unknown' CHECK(work_mode IN ('remote','hybrid','onsite','unknown')),
  contract_type TEXT NOT NULL DEFAULT 'unknown' CHECK(contract_type IN ('clt','pj','other','unknown')),
  salary_min NUMERIC CHECK(salary_min >= 0),
  salary_max NUMERIC CHECK(salary_max >= 0),
  salary_unit TEXT NOT NULL DEFAULT 'unknown' CHECK(salary_unit IN ('monthly','hourly','yearly','unknown')),
  requirements_facts TEXT NOT NULL DEFAULT '[]',
  requirements_inferred TEXT NOT NULL DEFAULT '[]',
  availability TEXT NOT NULL DEFAULT 'unconfirmed' CHECK(availability IN ('open','closed','unconfirmed')),
  fit TEXT NOT NULL DEFAULT 'weak' CHECK(fit IN ('strong','medium','weak')),
  fit_score INTEGER NOT NULL DEFAULT 0,
  fit_reasons TEXT NOT NULL DEFAULT '[]',
  user_decision TEXT NOT NULL DEFAULT 'none' CHECK(user_decision IN ('none','discarded','applied')),
  decision_reverted_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  CHECK(salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max)
);
CREATE TABLE opportunity_source (
  id TEXT PRIMARY KEY,
  opportunity_id TEXT NOT NULL REFERENCES opportunity(id),
  source TEXT NOT NULL,
  external_id TEXT,
  canonical_url TEXT NOT NULL UNIQUE,
  fetched_at TEXT NOT NULL,
  raw_excerpt TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE(source, external_id)
);
CREATE INDEX opportunity_source_opportunity ON opportunity_source(opportunity_id);
CREATE TABLE opportunity_match_candidate (
  opportunity_a TEXT NOT NULL REFERENCES opportunity(id),
  opportunity_b TEXT NOT NULL REFERENCES opportunity(id),
  signal TEXT NOT NULL,
  state TEXT NOT NULL DEFAULT 'open' CHECK(state IN ('open','merged_by_user','rejected')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  PRIMARY KEY(opportunity_a, opportunity_b),
  CHECK(opportunity_a < opportunity_b)
);
CREATE TABLE daily_selection (
  local_date TEXT NOT NULL,
  opportunity_id TEXT NOT NULL REFERENCES opportunity(id),
  rank INTEGER NOT NULL CHECK(rank BETWEEN 1 AND 10),
  reason TEXT NOT NULL,
  PRIMARY KEY(local_date, opportunity_id),
  UNIQUE(local_date, rank),
  UNIQUE(opportunity_id)
);
