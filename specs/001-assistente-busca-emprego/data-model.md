# Data Model — Assistente pessoal de busca de emprego

Persistência: SQLite (research D-03). Convenções:

- `id` = UUIDv7 em texto; datas em UTC ISO-8601 (`*_at`) e datas de calendário locais em `America/Sao_Paulo` (`*_local_date`).
- Toda tabela mutável tem `created_at`, `updated_at`. Entidades com histórico usam tabela de eventos append-only em vez de sobrescrever.
- `author` ∈ {`user`, `bot:<botId>`, `system`} em toda mudança registrada (FR-011, NFR-007).
- Nenhuma coluna guarda segredo em texto claro.

## Perfil

### ProfileVersion
| Campo | Tipo | Regra |
| --- | --- | --- |
| id | text PK | |
| source_file | text | ex.: `curriculo-en-draft.md` |
| source_hash | text | detecta reimportação |
| status | enum | `imported` → `reviewed` (somente usuário revisa) |
| reviewed_at | datetime? | |

### ProfileFact
Fato profissional atômico.
| Campo | Tipo | Regra |
| --- | --- | --- |
| id | text PK | |
| profile_version_id | FK | |
| kind | enum | `experience`, `skill`, `education`, `language`, `preference` |
| statement | text | |
| origin | enum | `documented` (currículo), `declared` (usuário), `study_recommended` |
| origin_ref | text | trecho/linha do currículo ou id da mensagem do usuário |

Invariante (FR-020, RB-006): `study_recommended` nunca é usado como evidência de competência em materiais nem em ranking; só `documented`/`declared`.

### SearchCriteria (linha única, versionada por `version`)
Campos: `target_roles[]`, `stack_keywords[]`, `remote_preferred`, `hybrid_regions[]` (default `RM Porto Alegre`), `clt_min_monthly_brl` (6000), `pj_min_monthly_brl` (9000), `accept_junior_if_justified`, `default_search_window_days` (30), `daily_search_enabled` (false por padrão), `daily_search_time_local` (configurável quando habilitada), `deadline_highlight_days` (7), `urgent_hours` (48), `email_digest_enabled`, `version`.

### Company
| Campo | Tipo | Regra |
| --- | --- | --- |
| id | text PK | |
| name | text unique | |
| tier | enum | `priority` (PicPay, BV, CWI, DB, CI&T, SAP) ou `suggested` |
| promoted_by_user_at | datetime? | `suggested` só vira `priority` por ação do usuário (FR-002) |
| notes | text | ex.: "experiência prévia na CWI; interesse em retornar" |
| careers_adapter | text? | id do adaptador de fonte (D-08) |

## Bots e execução

### Bot
| Campo | Tipo | Regra |
| --- | --- | --- |
| id | text PK | |
| name | text unique | |
| role | enum | `coordinator`, `search`, `tracking`, `preparation`, `custom` |
| openbot_agent_name | text | ex.: `curriculotron-busca` |
| state | enum | ver transições |
| active_config_id | FK? | versão aprovada em vigor |

Estados: `draft` → `in_review` → `active` ⇄ `paused`; `active|paused` → `in_review` quando há nova `BotConfig` proposta (a ativa continua em vigor até aprovação; se o usuário preferir, pausar). `rejected` é terminal para propostas fora do domínio emprego (US2-4).

### BotConfig (imutável após criada)
| Campo | Tipo | Regra |
| --- | --- | --- |
| id | text PK | |
| bot_id | FK | |
| version | int | sequencial por bot |
| purpose | text | |
| instructions | text | enviadas como preâmbulo de cada tarefa |
| capabilities[] | enum[] | subconjunto do catálogo fixo: `job.search`, `job.rank`, `mail.classify`, `draft.prepare`, `interview.simulate`, `profile.read` |
| limits | json | ex.: `{maxTasksPerDay, maxSelectionsPerDay:10}` |
| routines | json | `[{kind:"daily", time:"08:00"}]` |
| content_hash | text | SHA-256 dos campos acima |
| approved_at / approved_by | datetime?, text? | preenchidos só por `ConfigApproval` |

Invariante: bot só executa se `state = active` e a tarefa usa exatamente `active_config_id`; capacidades fora da config ⇒ recusa registrada.

### Conversation / ChatMessage
`Conversation(id, bot_id, topic)`; `ChatMessage(id, conversation_id, role∈{user,bot}, text, openbot_delivery_id?, created_at)`.

### TaskRun
| Campo | Tipo | Regra |
| --- | --- | --- |
| id | text PK | |
| bot_id, bot_config_id | FK | config usada |
| trigger | enum | `routine`, `manual`, `coordinator` |
| idempotency_key | text unique | ex.: `routine:<id>:<local_date>` |
| state | enum | `queued` → `running` → `completed` \| `failed` \| `paused` → (`running` na retomada) |
| pause_reason | enum? | `provider_limit`, `needs_person`, `source_unavailable`, `gmail_disconnected`, `machine_offline` |
| checkpoint | json | etapa concluída + ids produzidos |
| sources_consulted[] / sources_failed[] | json | |
| error_summary | text? | sem segredos |

### ActivityLog (append-only, NFR-007)
`(id, task_run_id?, author, action, target_type, target_id, evidence_ref?, result, error?, at)`.

### IntegrationStatus
`(integration ∈ {openbot, gmail, source:<id>}, last_attempt_at, last_success_at, state ∈ {ok, degraded, disconnected, paused}, detail, tracking_started_at?)`. Invariante FR-017: falha atualiza só `last_attempt_at`.

## Vagas

### Opportunity
| Campo | Tipo | Regra |
| --- | --- | --- |
| id | text PK | |
| title, company_name, location, work_mode | text/enum | `work_mode` ∈ `remote`, `hybrid`, `onsite`, `unknown` |
| published_at | datetime? | data de publicação fornecida pela fonte; nula/desconhecida nunca é tratada como dentro da janela pedida |
| contract_type | enum | `clt`, `pj`, `other`, `unknown` |
| salary_min, salary_max, salary_unit | number?, enum | `salary_unit` ∈ `monthly`, `hourly`, `yearly`, `unknown`; ausente ⇒ UI "salário a confirmar" |
| requirements_facts[] / requirements_inferred[] | json | fatos separados de inferências (FR-006) |
| availability | enum | `open`, `closed`, `unconfirmed` |
| fit_score | number | |
| fit_reasons[] | json | justificativas objetivas |
| user_decision | enum | `none`, `discarded`, `applied` |
| decision_reverted_at | datetime? | reversão explícita (FR-005) |

### OpportunitySource
`(id, opportunity_id, source, external_id?, canonical_url, fetched_at, raw_excerpt)` — unique `(source, external_id)` e unique `canonical_url`.

### OpportunityMatchCandidate
Possível duplicata por identidade fraca: `(opportunity_a, opportunity_b, signal, state ∈ {open, merged_by_user, rejected})`. Nunca fusão automática.

### DailySelection
`(local_date, opportunity_id, rank, reason)` — PK `(local_date, opportunity_id)`. Invariante (FR-004, SC-003): `count(local_date) ≤ 10`, verificado na mesma transação de inserção; oportunidades com `user_decision ≠ none` ou já presentes em qualquer seleção anterior não são elegíveis. Excedentes ficam acessíveis na lista secundária (consulta sobre `Opportunity` sem seleção).

## Candidaturas e e-mail

### Application
| Campo | Tipo | Regra |
| --- | --- | --- |
| id | text PK | |
| opportunity_id | FK unique | |
| confirmed_at | datetime | só via ação "Me candidatei" (RB-007) |
| confirmation_origin | enum | `manual` (V1) |
| status | enum | `submitted_awaiting`, `contact_received`, `interview`, `offer`, `closed_rejected`, `withdrawn` |
| next_action, next_action_due_at, due_precision | text?, datetime?, enum | `due_precision` ∈ `exact`, `date_only`, `ambiguous` |

Invariantes: tempo sem resposta não muda status (US3-4); `confirmed_at ≥ IntegrationStatus(gmail).tracking_started_at` para ser acompanhada por e-mail (RB-009).

### StatusChange (append-only)
`(id, application_id, from_status, to_status, author, evidence_id?, reason, at)`. Mudança `author=bot:*` exige `evidence_id`.

### MailMessage
| Campo | Tipo | Regra |
| --- | --- | --- |
| gmail_message_id | text PK | idempotência |
| thread_id, from_addr, subject, received_at | | metadados |
| body_excerpt | text? | só se relevante (NFR-002) |
| relevance | enum | `pending`, `relevant`, `irrelevant` |
| label_applied_at | datetime? | marcador dedicado |
| retain_until | date? | D-11 |

### MailAssociation
`(gmail_message_id, application_id?, kind ∈ {confirmation, recruiter_contact, interview_invite, deadline, result, job_alert, other}, confidence ∈ {explicit, probable, ambiguous}, state ∈ {applied, needs_review, rejected_by_user})`. Mais de uma candidatura plausível ⇒ `needs_review`, sem alterar candidatura.

### Evidence
`(id, kind ∈ {mail, user_action, source_page}, ref, quote, captured_at)`.

### ReviewItem
Fila única de pendências do usuário: `(id, kind ∈ {ambiguous_mail, dedupe_candidate, conflict, bot_config, salary_ambiguous}, target_ref, created_at, resolved_at?, resolution)`.

### Draft / Approval / OutboxMessage
- `Draft(id, application_id?, purpose ∈ {recruiter_reply, digest}, to[], cc[], subject, body, content_hash, version)`. Toda edição incrementa `version` e muda `content_hash`.
- `Approval(id, draft_id, draft_version, content_hash, approved_at)`. Válida só se `content_hash` coincide com o draft atual (US4-5).
- `OutboxMessage(id, draft_id, approval_id, state ∈ {pending, sending, sent, uncertain, failed}, gmail_message_id?, attempts)`. `uncertain` ⇒ reconciliar em "Enviados" por cabeçalho `X-Curriculotron-Outbox-Id` antes de qualquer nova tentativa.
- Resumo diário ao próprio usuário: `purpose=digest`, destinatário fixo = conta conectada; autorizado pela habilitação da rotina, nunca reutilizável para terceiros. Sem conteúdo útil ⇒ nenhum `Draft` criado (US4-6).

## Preparação

### InterviewSession
`(id, opportunity_id, language ∈ {pt, en}, kind ∈ {technical, behavioral}, profile_version_id, state ∈ {active, finished}, feedback?, recommendations json)`.
### InterviewTurn
`(session_id, seq, question, answer?)` — uma pergunta por vez: nova pergunta só após `answer` do turno anterior.

## Segurança

### AppCredential
Linha única: `password_hash` (Argon2id), `failed_attempts`, `locked_until`. Sessões em memória do servidor (invalidadas ao reiniciar).
