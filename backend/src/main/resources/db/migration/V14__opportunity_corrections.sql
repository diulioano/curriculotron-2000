-- Correções manuais de campos da vaga (feature 005). opportunity_source e opportunity_evidence
-- nunca são alterados por uma correção; cada mudança vira uma linha append-only aqui.

-- Prazo de inscrição passa a ser dado persistido para poder ser corrigido (antes era sempre nulo).
ALTER TABLE opportunity ADD COLUMN deadline_at TEXT
  CHECK(deadline_at IS NULL OR deadline_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]');

CREATE TABLE opportunity_correction (
  id TEXT PRIMARY KEY,
  opportunity_id TEXT NOT NULL REFERENCES opportunity(id),
  field_name TEXT NOT NULL CHECK(field_name IN ('title','company_name','location','work_mode','contract_type','salary_min','salary_max','salary_unit','availability','deadline_at','published_at')),
  previous_value TEXT,
  corrected_value TEXT,
  reason TEXT CHECK(reason IS NULL OR length(reason) <= 500),
  author TEXT NOT NULL DEFAULT 'user' CHECK(author = 'user'),
  corrected_at TEXT NOT NULL
);
CREATE INDEX opportunity_correction_opp_at ON opportunity_correction (opportunity_id, corrected_at DESC);

CREATE TRIGGER opportunity_correction_no_update BEFORE UPDATE ON opportunity_correction
BEGIN SELECT RAISE(ABORT, 'opportunity_correction e append-only'); END;
CREATE TRIGGER opportunity_correction_no_delete BEFORE DELETE ON opportunity_correction
BEGIN SELECT RAISE(ABORT, 'opportunity_correction e append-only'); END;
