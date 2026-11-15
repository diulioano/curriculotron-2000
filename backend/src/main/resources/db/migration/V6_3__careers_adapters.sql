-- Mapeamento das páginas públicas de carreira das empresas prioritárias (verificado em 2026-11-14).
-- Formato de careers_adapter: ver JobSourceFactory. Empresas sem fonte automática ficam NULL e
-- seguem pela entrada manual.
--   BV       -> jobs.lever.co/bv
--   CI&T     -> jobs.lever.co/ciandt
--   PicPay   -> picpay.com/carreiras (Oracle Recruiting Cloud, site CX_3001)
--   CWI      -> cwi.com.br/talentos/oportunidades
UPDATE company SET careers_adapter = 'lever:bv', updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
  WHERE name = 'BV';
UPDATE company SET careers_adapter = 'lever:ciandt', updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
  WHERE name = 'CI&T';
UPDATE company SET careers_adapter = 'oracle:epdm.fa.la1/CX_3001', updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
  WHERE name = 'PicPay';
UPDATE company SET careers_adapter = 'cwi:oportunidades', updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
  WHERE name = 'CWI';

UPDATE company SET
  notes = COALESCE(notes || '; ', '') || 'jobs.sap.com bloqueia acesso automatizado; usar entrada manual',
  updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
  WHERE name = 'SAP';
-- "Digital Business" era o nome provisório; a empresa é a DB (db.tec.br, Randoncorp).
-- Vagas em db.gupy.io (link da página "Pessoas"). LinkedIn fica só como referência: a busca não
-- acessa o LinkedIn diretamente (D-08; alertas por e-mail entram em T082).
UPDATE company SET
  name = 'DB',
  careers_adapter = 'gupy:db',
  notes = COALESCE(notes || '; ', '') || 'site: db.tec.br; LinkedIn: linkedin.com/company/somosdb',
  updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
  WHERE id = '01930000-0000-7000-8000-000000000004';
