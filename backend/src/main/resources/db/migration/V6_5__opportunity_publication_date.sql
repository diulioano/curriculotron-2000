ALTER TABLE opportunity ADD COLUMN published_at TEXT;
UPDATE routine SET enabled=0 WHERE purpose='search';
