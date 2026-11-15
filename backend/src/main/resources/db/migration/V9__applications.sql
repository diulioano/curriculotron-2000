CREATE TABLE application (
 id TEXT PRIMARY KEY,
 opportunity_id TEXT NOT NULL UNIQUE REFERENCES opportunity(id),
 confirmed_at TEXT NOT NULL,
 confirmation_origin TEXT NOT NULL CHECK(confirmation_origin = 'manual'),
 status TEXT NOT NULL CHECK(status IN ('submitted_awaiting','contact_received','interview','offer','closed_rejected','withdrawn')),
 next_action TEXT,
 next_action_due_at TEXT,
 due_precision TEXT NOT NULL DEFAULT 'ambiguous' CHECK(due_precision IN ('exact','date_only','ambiguous')),
 created_at TEXT NOT NULL,
 updated_at TEXT NOT NULL
);
CREATE TABLE evidence (
 id TEXT PRIMARY KEY,
 kind TEXT NOT NULL CHECK(kind IN ('mail','user_action','source_page')),
 ref TEXT NOT NULL,
 quote TEXT,
 captured_at TEXT NOT NULL
);
CREATE TABLE status_change (
 id TEXT PRIMARY KEY,
 application_id TEXT NOT NULL REFERENCES application(id),
 from_status TEXT,
 to_status TEXT NOT NULL CHECK(to_status IN ('submitted_awaiting','contact_received','interview','offer','closed_rejected','withdrawn')),
 author TEXT NOT NULL,
 evidence_id TEXT REFERENCES evidence(id),
 reason TEXT NOT NULL,
 at TEXT NOT NULL,
 CHECK(author = 'user' OR (author LIKE 'bot:%' AND evidence_id IS NOT NULL))
);
CREATE INDEX status_change_application ON status_change(application_id, at, id);
CREATE TRIGGER status_change_no_update BEFORE UPDATE ON status_change BEGIN SELECT RAISE(ABORT, 'status_change is append-only'); END;
CREATE TRIGGER status_change_no_delete BEFORE DELETE ON status_change BEGIN SELECT RAISE(ABORT, 'status_change is append-only'); END;
CREATE TABLE review_item (
 id TEXT PRIMARY KEY,
 kind TEXT NOT NULL CHECK(kind IN ('ambiguous_mail','dedupe_candidate','conflict','bot_config','salary_ambiguous')),
 target_ref TEXT NOT NULL,
 summary TEXT NOT NULL,
 created_at TEXT NOT NULL,
 resolved_at TEXT,
 resolution TEXT
);
