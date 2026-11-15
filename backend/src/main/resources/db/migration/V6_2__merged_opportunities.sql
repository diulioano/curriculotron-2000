ALTER TABLE opportunity ADD COLUMN merged_into TEXT REFERENCES opportunity(id);
CREATE INDEX opportunity_merged_into ON opportunity(merged_into);
