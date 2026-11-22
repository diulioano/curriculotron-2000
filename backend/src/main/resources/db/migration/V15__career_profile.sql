-- US006: perfil de carreira (linha única, versão otimista). Listas em JSON.
CREATE TABLE career_profile (
  id                           INTEGER PRIMARY KEY CHECK (id = 1),
  version                      INTEGER NOT NULL CHECK (version > 0),
  role                         TEXT    NOT NULL,
  primary_technology           TEXT    NOT NULL,
  secondary_technologies       TEXT    NOT NULL DEFAULT '[]',
  flexible_technologies        TEXT    NOT NULL DEFAULT '[]',
  current_seniority            TEXT    NOT NULL CHECK (current_seniority IN ('junior', 'pleno', 'senior')),
  target_levels                TEXT    NOT NULL,
  priorities                   TEXT    NOT NULL DEFAULT '[]',
  junior_floor_clt_monthly_brl TEXT CHECK (junior_floor_clt_monthly_brl IS NULL OR CAST(junior_floor_clt_monthly_brl AS NUMERIC) > 0),
  junior_floor_pj_monthly_brl  TEXT CHECK (junior_floor_pj_monthly_brl IS NULL OR CAST(junior_floor_pj_monthly_brl AS NUMERIC) > 0),
  origin                       TEXT    NOT NULL CHECK (origin IN ('markdown', 'manual')),
  created_at                   TEXT    NOT NULL,
  updated_at                   TEXT    NOT NULL
);
