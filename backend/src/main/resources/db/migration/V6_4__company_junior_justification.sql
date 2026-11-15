-- RB-004: empresas em que o usuário aceita vaga júnior, com o motivo dado por ele (2026-11-14).
ALTER TABLE company ADD COLUMN junior_justification TEXT;

UPDATE company SET
  junior_justification = 'na SAP o júnior compensa: carga de trabalho menor e salário maior (critério do usuário)',
  updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
  WHERE name = 'SAP';
