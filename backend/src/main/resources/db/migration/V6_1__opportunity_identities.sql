CREATE TABLE opportunity_url_identity (
  canonical_url TEXT PRIMARY KEY,
  opportunity_id TEXT NOT NULL REFERENCES opportunity(id)
);
CREATE TABLE opportunity_external_identity (
  source TEXT NOT NULL,
  external_id TEXT NOT NULL,
  opportunity_id TEXT NOT NULL REFERENCES opportunity(id),
  PRIMARY KEY(source, external_id)
);
INSERT INTO opportunity_url_identity SELECT canonical_url, opportunity_id FROM opportunity_source;
INSERT INTO opportunity_external_identity
  SELECT source, external_id, opportunity_id FROM opportunity_source WHERE external_id IS NOT NULL;
