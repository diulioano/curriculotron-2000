CREATE TABLE salary_estimate (
 id TEXT PRIMARY KEY, profile_key TEXT NOT NULL, company TEXT NOT NULL, role TEXT NOT NULL,
 seniority TEXT NOT NULL, location TEXT NOT NULL, work_mode TEXT NOT NULL,
 contract_type TEXT NOT NULL CHECK(contract_type IN ('clt','pj','unknown')),
 original_min TEXT NOT NULL, original_max TEXT NOT NULL,
 original_unit TEXT NOT NULL CHECK(original_unit IN ('monthly','yearly','hourly','daily','unknown')),
 sample_size INTEGER CHECK(sample_size IS NULL OR sample_size >= 0),
 source TEXT NOT NULL, source_url TEXT NOT NULL, consulted_at TEXT NOT NULL,
 source_updated_on TEXT, evidence TEXT NOT NULL,
 CHECK(CAST(original_min AS NUMERIC) >= 0 AND CAST(original_max AS NUMERIC) >= CAST(original_min AS NUMERIC))
);
CREATE INDEX salary_profile_date ON salary_estimate(profile_key,consulted_at);
CREATE TRIGGER salary_estimate_no_update BEFORE UPDATE ON salary_estimate BEGIN SELECT RAISE(ABORT,'salary_estimate is append-only'); END;
CREATE TRIGGER salary_estimate_no_delete BEFORE DELETE ON salary_estimate BEGIN SELECT RAISE(ABORT,'salary_estimate is append-only'); END;
CREATE TABLE opportunity_salary_link (
 id TEXT PRIMARY KEY, opportunity_id TEXT NOT NULL REFERENCES opportunity(id) ON DELETE CASCADE,
 estimate_id TEXT NOT NULL REFERENCES salary_estimate(id), applied_at TEXT NOT NULL, superseded_at TEXT
);
CREATE UNIQUE INDEX salary_current_link ON opportunity_salary_link(opportunity_id) WHERE superseded_at IS NULL;
CREATE TABLE salary_lookup_attempt (
 id TEXT PRIMARY KEY, profile_key TEXT NOT NULL, task_run_id TEXT NOT NULL REFERENCES task_run(id),
 requested_at TEXT NOT NULL, state TEXT NOT NULL CHECK(state IN ('running','succeeded','not_configured','not_found','invalid','unavailable','blocked','provider_limit','interrupted')),
 completed_at TEXT, estimate_id TEXT REFERENCES salary_estimate(id),
 UNIQUE(task_run_id,profile_key)
);
CREATE UNIQUE INDEX salary_running_profile ON salary_lookup_attempt(profile_key) WHERE state='running';
