ALTER TABLE opportunity ADD COLUMN evidence_revision INTEGER NOT NULL DEFAULT 1 CHECK(evidence_revision > 0);

CREATE TABLE opportunity_evidence (
  id TEXT PRIMARY KEY,
  opportunity_id TEXT NOT NULL REFERENCES opportunity(id),
  source_id TEXT UNIQUE REFERENCES opportunity_source(id),
  source_kind TEXT NOT NULL,
  title TEXT,
  location TEXT,
  description_text TEXT CHECK(description_text IS NULL OR length(description_text) <= 100000),
  description_completeness TEXT NOT NULL CHECK(description_completeness IN ('complete','partial','unknown')),
  completeness_reason TEXT,
  content_revision INTEGER NOT NULL DEFAULT 1 CHECK(content_revision > 0),
  fetched_at TEXT
);
CREATE INDEX opportunity_evidence_opportunity ON opportunity_evidence(opportunity_id);

INSERT INTO opportunity_evidence(id,opportunity_id,source_id,source_kind,title,location,description_text,description_completeness,completeness_reason,content_revision,fetched_at)
SELECT 'legacy-' || s.id,s.opportunity_id,s.id,s.source,o.title,o.location,substr(s.raw_excerpt,1,100000),'unknown','legacy_excerpt',1,s.fetched_at
FROM opportunity_source s JOIN opportunity o ON o.id=s.opportunity_id;

INSERT INTO opportunity_evidence(id,opportunity_id,source_id,source_kind,title,location,description_text,description_completeness,completeness_reason,content_revision,fetched_at)
SELECT 'manual-' || o.id,o.id,NULL,'manual',o.title,o.location,NULL,'unknown','legacy_manual',1,o.created_at
FROM opportunity o WHERE NOT EXISTS(SELECT 1 FROM opportunity_source s WHERE s.opportunity_id=o.id);

CREATE TABLE opportunity_compatibility (
  opportunity_id TEXT PRIMARY KEY REFERENCES opportunity(id),
  criteria_version INTEGER NOT NULL CHECK(criteria_version > 0),
  evaluator_version INTEGER NOT NULL CHECK(evaluator_version > 0),
  evidence_revision INTEGER NOT NULL CHECK(evidence_revision > 0),
  assessed_at TEXT NOT NULL,
  role_status TEXT NOT NULL CHECK(role_status IN ('matched','mismatched','unknown')),
  technology_status TEXT NOT NULL CHECK(technology_status IN ('matched','mismatched','unknown','unrestricted')),
  reason_codes TEXT NOT NULL DEFAULT '[]',
  criteria_snapshot TEXT NOT NULL,
  evidence_items TEXT NOT NULL DEFAULT '[]',
  fit TEXT NOT NULL CHECK(fit IN ('strong','medium','weak')),
  fit_score INTEGER NOT NULL DEFAULT 0,
  fit_reasons TEXT NOT NULL DEFAULT '[]'
);
CREATE INDEX opportunity_compatibility_versions ON opportunity_compatibility(criteria_version,evaluator_version,evidence_revision);

CREATE TABLE compatibility_refresh (
  id INTEGER PRIMARY KEY CHECK(id=1),
  target_criteria_version INTEGER NOT NULL CHECK(target_criteria_version > 0),
  evaluator_version INTEGER NOT NULL CHECK(evaluator_version > 0),
  run_id TEXT,
  state TEXT NOT NULL CHECK(state IN ('pending','running','ready','failed')),
  started_at TEXT,
  finished_at TEXT,
  error_code TEXT,
  assessed_count INTEGER NOT NULL DEFAULT 0 CHECK(assessed_count >= 0),
  remaining_count INTEGER NOT NULL DEFAULT 0 CHECK(remaining_count >= 0)
);
INSERT INTO compatibility_refresh(id,target_criteria_version,evaluator_version,state,remaining_count)
SELECT 1,version,1,'pending',(SELECT count(*) FROM opportunity WHERE merged_into IS NULL) FROM search_criteria WHERE id=1;
