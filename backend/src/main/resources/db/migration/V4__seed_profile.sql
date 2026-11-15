-- RB-001 a RB-005: critérios padrão e empresas prioritárias iniciais.
INSERT INTO search_criteria (
  id, target_roles, stack_keywords, remote_preferred, hybrid_regions,
  clt_min_monthly_brl, pj_min_monthly_brl, accept_junior_if_justified,
  daily_search_time_local, deadline_highlight_days, urgent_hours, email_digest_enabled,
  version, created_at, updated_at)
VALUES (
  1,
  '["Desenvolvedor Fullstack Pleno Java","Desenvolvedor Backend Pleno Java","Software Engineer Java"]',
  '["Java","Java 17","Spring Boot","Angular","React","REST","Kafka","Microsserviços","JUnit","Mockito"]',
  1,
  '["RM Porto Alegre"]',
  6000, 9000, 1,
  '07:30', 7, 48, 0,
  1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));

INSERT INTO company (id, name, tier, notes, created_at, updated_at) VALUES
  ('01930000-0000-7000-8000-000000000001', 'PicPay',           'priority', NULL, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
  ('01930000-0000-7000-8000-000000000002', 'BV',               'priority', NULL, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
  ('01930000-0000-7000-8000-000000000003', 'CWI',              'priority', 'experiência prévia na CWI; interesse em retornar', strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
  ('01930000-0000-7000-8000-000000000004', 'Digital Business', 'priority', NULL, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
  ('01930000-0000-7000-8000-000000000005', 'CI&T',             'priority', NULL, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
  ('01930000-0000-7000-8000-000000000006', 'SAP',              'priority', NULL, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));
