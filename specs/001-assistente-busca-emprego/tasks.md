---
description: "Task list — Assistente pessoal de busca de emprego"
---

# Tasks: Assistente pessoal de busca de emprego

**Input**: `specs/001-assistente-busca-emprego/` — [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: incluídos. A spec exige testes controlados explícitos (cenários de aceitação, NFR-001/003/004/005/006, SC-003/004/007/008) e o quickstart §3 os define. Em cada história, os testes vêm antes da implementação e devem falhar primeiro.

**Organização**: por história de usuário. US1, US3 e US5 **não dependem do OpenBot**. US2, US4 (classificação) e US6 dependem da **Fase 3 — Gate OpenBot**.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizável (arquivos diferentes, sem dependência de tarefa incompleta)
- **[USn]**: história da spec
- Caminhos: `backend/src/main/java/dev/curriculotron/<pacote>/`, abreviado como `B/<pacote>/`; testes em `backend/src/test/java/dev/curriculotron/<pacote>/` = `BT/<pacote>/`; migrações em `backend/src/main/resources/db/migration/` = `MIG/`; frontend em `frontend/src/` = `F/`; e2e em `frontend/tests/e2e/` = `E2E/`.

---

## Phase 0: Piloto no OpenBot (decisão do usuário em 2026-11-14 — antes da F1)

**Purpose**: usar os quatro bots diretamente no OpenBot antes de construir o painel. Fonte versionada em `openbot-agents/`; o zip é gerado por `openbot-agents/build-import.ps1` e importado em **Server settings → Import → Hermes Agent** (formato oficial `openbot-agent-import` v1). Rotinas vêm **pausadas**. Gmail fica fora do piloto (mensagens são coladas no chat). Os resultados alimentam T025 e o desenho dos bots (D-04/D-05).

- [x] T000a Importar `openbot-import-curriculotron-<data>.zip` no OpenBot, escolher Codex em cada agente e revisar descrições, skills e memórias
- [x] T000b Ligar Local scripts no `Dohko de Libra` (Coordenador) e executar a prova de T025 com ele em vez de `curriculotron-teste`
- [ ] T000c Pilotar: busca com `Aiolos de Sagitário` realizada e validada pelo usuário; faltam registrar candidatura com `Mu de Áries` e simulação com `Camus de Aquário`; anotar falhas, limites e lacunas em `specs/001-assistente-busca-emprego/research.md`

---

## Phase 1: Setup

**Purpose**: ambiente e esqueleto dos dois projetos

- [x] T001 Instalar JDK 25 (Temurin) e Node LTS no Windows e registrar as versões verificadas em `specs/001-assistente-busca-emprego/research.md` (seção D-01/D-02)
- [x] T002 Criar projeto Gradle Kotlin DSL com Spring Boot 4.x (web, security, validation), sqlite-jdbc, Flyway, Jackson, networknt json-schema-validator, WireMock e AssertJ em `backend/build.gradle.kts` e `backend/settings.gradle.kts`, com wrapper `backend/gradlew.bat`
- [x] T003 [P] Criar app Vite + React + TypeScript com React Router e TanStack Query, Vitest, Testing Library, Playwright e @axe-core/playwright em `frontend/package.json`, `frontend/vite.config.ts`, `frontend/tsconfig.json`
- [x] T004 [P] Configurar Spotless (google-java-format) em `backend/build.gradle.kts` e ESLint + Prettier em `frontend/eslint.config.js` e `frontend/.prettierrc`
- [x] T005 Configurar task Gradle `copyFrontend` que copia `frontend/dist` para `backend/build/resources/main/static` e source set `integrationTest` em `backend/build.gradle.kts`
- [x] T006 [P] Atualizar `.gitignore` com `backend/build/`, `backend/.gradle/`, `frontend/node_modules/`, `frontend/dist/`, `*.db`, `*.pem`, `certs/`

---

## Phase 2: Foundational (bloqueia todas as histórias)

**Purpose**: segurança, persistência, auditoria, perfil base e casca do painel

### Testes fundamentais

- [x] T007 [P] Teste de integração: rotas `/api/**` sem sessão retornam `401` sem dados privados e mutações sem `X-CSRF-Token` retornam `403` em `BT/app/SecurityIntegrationTest.java` (NFR-001)
- [x] T008 [P] Teste: `POST /api/setup` só funciona uma vez (`409` depois), login com senha errada repetida retorna `429` em `BT/app/SessionIntegrationTest.java`
- [x] T009 [P] Teste: `IntegrationStatus` falho atualiza só `last_attempt_at`, nunca `last_success_at` em `BT/audit/IntegrationStatusRepositoryTest.java` (FR-017)

### Infraestrutura

- [x] T010 Configurar diretório de dados `%LOCALAPPDATA%\Curriculotron\data\curriculotron.db`, SQLite em modo WAL e executor único de escrita em `B/app/DataSourceConfig.java` e `B/app/WriteExecutor.java` (D-03)
- [x] T011 Criar migração base com convenções (`id` UUIDv7 texto, `created_at`, `updated_at` UTC ISO-8601) e tabelas `app_credential`, `activity_log`, `integration_status` em `MIG/V1__base.sql`
- [x] T012 [P] Implementar gerador UUIDv7 e relógio injetável com fuso `America/Sao_Paulo` para `*_local_date` em `B/app/Ids.java` e `B/app/AppClock.java`
- [x] T013 Implementar `AppCredential` com hash Argon2id, `failed_attempts`, `locked_until` e rotas `POST /api/setup`, `POST|GET|DELETE /api/session` (cookie `HttpOnly; Secure; SameSite=Strict`, expiração por inatividade, CSRF sincronizado) em `B/app/security/` (contracts/panel-api.md §Sessão)
- [x] T014 Configurar HTTPS na porta 8443 com keystore PEM gerado por mkcert (caminho configurável em `backend/src/main/resources/application.yml`) em `B/app/TlsConfig.java` (D-06)
- [x] T015 Implementar tratamento global de erros `{error:{code,message}}` sem stack trace nem segredos e suporte a `Idempotency-Key` (tabela `idempotency_key` em `MIG/V2__idempotency.sql`) em `B/app/web/ApiErrorHandler.java` e `B/app/web/IdempotencyFilter.java`
- [x] T016 [P] Implementar `ActivityLog` append-only `(id, task_run_id?, author, action, target_type, target_id, evidence_ref?, result, error?, at)` com `author ∈ {user, bot:<botId>, system}` em `B/audit/ActivityLogRepository.java`
- [x] T017 [P] Implementar `IntegrationStatus` `(integration ∈ {openbot, gmail, source:<id>}, last_attempt_at, last_success_at, state ∈ {ok, degraded, disconnected, paused}, detail, tracking_started_at?)` em `B/audit/IntegrationStatusRepository.java`
- [x] T018 Criar migração de perfil: `profile_version`, `profile_fact` (`origin ∈ documented, declared, study_recommended`), `search_criteria` (linha única versionada), `company` (`tier ∈ priority, suggested`) em `MIG/V3__profile.sql`
- [x] T019 Implementar importador de `curriculo-en-draft.md` que cria `ProfileVersion(status=imported)` com `source_hash` e `ProfileFact(origin=documented, origin_ref=<linha>)` em `B/profile/ResumeImporter.java` (FR-001)
- [x] T020 Semear `SearchCriteria` padrão (`clt_min_monthly_brl` 6000, `pj_min_monthly_brl` 9000, `hybrid_regions` RM Porto Alegre, `remote_preferred`, `deadline_highlight_days` 7, `urgent_hours` 48) e empresas prioritárias PicPay, BV, CWI, Digital Business, CI&T, SAP (nota CWI: "experiência prévia na CWI; interesse em retornar") em `MIG/V4__seed_profile.sql` (RB-001–RB-005)
- [x] T021 Implementar tabela e serviço `TaskRun` (`state`: `queued` → `running` → `completed | failed | paused`; `pause_reason ∈ provider_limit, needs_person, source_unavailable, gmail_disconnected, machine_offline`; `idempotency_key` unique; `checkpoint` json) em `MIG/V5__task_run.sql` e `B/orchestration/TaskRunService.java`
- [x] T022 Implementar agendador de rotinas com tick de 1 min, `next_run_at` local, colapso de ocorrências perdidas e registro de rotinas não executadas em `B/orchestration/RoutineScheduler.java` (D-10). Tabelas `routine` e `routine_missed` criadas já em `MIG/V5__task_run.sql` (o agendador precisa delas); T053 só acrescenta o vínculo com `bot`
- [x] T023 [P] Criar casca do painel: roteamento, layout responsivo sem rolagem horizontal em 360 px, tela de setup/login, cliente HTTP com CSRF e tratamento de 401 em `F/app/App.tsx`, `F/app/Layout.tsx`, `F/pages/Login.tsx`, `F/api/client.ts`
- [x] T024 [P] Criar componente de estado não dependente de cor (ícone + rótulo) e de "dados de <data>" em `F/components/StatusBadge.tsx` e `F/components/DataAsOf.tsx` (NFR-005)

**Checkpoint**: login funciona por HTTPS; banco migra; testes T007–T009 passam.

---

## Phase 3: Gate OpenBot (bloqueia US2, US4-classificação, US6)

**Purpose**: prova ao vivo PT-001/PT-002 e adaptador Local scripts

- [ ] T025 Executar quickstart §1 (agente `curriculotron-teste`, Local scripts ligado, `openbot-cli list/run` com `--id prova-001` duas vezes, recusa com Local scripts desligado) e registrar data, versão, exit codes e restrições em `specs/001-assistente-busca-emprego/research.md` (PT-001/PT-002). **Se falhar: parar e levar ao usuário como decisão de escopo.**
- [ ] T026 [P] Teste WireMock do adaptador: descoberta de `url`/`token`, `401` ⇒ relê e repete uma vez, `403` ⇒ `needs_person`, `429` ⇒ `provider_limit`, `pending` não vazio ⇒ `needs_person`, mesma `clientMessageId` não duplica, token ausente de logs em `BT/openbot/OpenBotClientTest.java`
- [ ] T027 [P] Teste de contrato: fixtures válidas/ inválidas de `job.rank@1`, `mail.classify@1`, `draft.prepare@1`, `bot.propose@1`, `task.interpret@1`, `interview.turn@1`, `interview.feedback@1` contra os schemas em `BT/openbot/BotOutputSchemaTest.java`
- [ ] T028 Criar JSON Schemas 2020-12 de contracts/bot-task-outputs.md em `backend/src/main/resources/schemas/*.schema.json`
- [ ] T029 Implementar cliente Local scripts (`listAgents`, `submit`, `delivery`, `pending`, `applySettings`, `newChat`; timeout 30 s; polling 2 s; teto 30 min) lendo `%APPDATA%\OpenBot\automation` ou `OPENBOT_AUTOMATION_DIR`, token só em memória, em `B/openbot/OpenBotClient.java` (contracts/openbot-adapter.md)
- [ ] T030 Implementar montagem da mensagem `[CURRICULOTRON TASK v1]` com preâmbulo da `BotConfig` aprovada e bloco `<<<EXTERNAL_DATA … EXTERNAL_DATA>>>` em `B/openbot/TaskPromptBuilder.java`
- [ ] T031 Implementar validação de resposta (extrai JSON, uma repetição `:retry1`, schema, descarte de campos extras, recusa de ação fora das capacidades) em `B/openbot/BotOutputValidator.java`
- [ ] T032 Integrar adaptador ao `TaskRunService` (`clientMessageId = taskRunId:step`, mapeamento de estados e `IntegrationStatus(openbot)`) em `B/orchestration/OpenBotTaskExecutor.java`

**Checkpoint**: tarefa real de teste roda pelo backend contra o OpenBot e retorna JSON validado.

---

## Phase 4: User Story 1 — Revisar oportunidades e próximas ações (P1) 🎯 MVP

**Goal**: seleção diária ≤10 com dedupe, lista secundária, detalhe com fatos/inferências e página inicial.
**Independent Test**: carregar vagas de fixtures (duplicadas, descartadas, vinculadas a candidaturas) e completar uma revisão; ranking por regras objetivas, sem OpenBot.

### Tests (US1)

- [x] T033 [P] [US1] Teste: 15 compatíveis ⇒ 10 na seleção e 5 na secundária; 3 fortes + 7 fracas ⇒ só 3; buscas manuais no mesmo dia nunca passam de 10 em `BT/opportunities/DailySelectionServiceTest.java` (US1-1, US1-2, SC-003)
- [X] T034 [P] [US1] Teste: vagas repetidas por `(source, external_id)`/URL canônica, descartadas e com candidatura não reaparecem; identidade fraca gera `OpportunityMatchCandidate` sem fusão em `BT/opportunities/DedupeServiceTest.java` (US1-3, D-09)
- [x] T035 [P] [US1] Teste dos filtros objetivos: CLT < R$ 6.000 ou PJ < R$ 9.000 explícitos fora da seleção principal; salário ausente entra como "a confirmar"; faixa que cruza o mínimo gera pendência; `salary_unit` ambíguo não é convertido em `BT/opportunities/ObjectiveFilterTest.java` (RB-002, RB-003)
- [x] T036 [P] [US1] Teste e2e J1: seleção, detalhe com "salário a confirmar", inferência separada, fonte, link, data; descartar; página inicial com prazos destacados em 360 e 1280 px em `E2E/j1-review.spec.ts` — validado junto à J3 com próxima ação real de candidatura

### Implementation (US1)

- [x] T037 [US1] Criar migração `opportunity` (`work_mode ∈ remote, hybrid, onsite, unknown`; `contract_type ∈ clt, pj, other, unknown`; `salary_unit ∈ monthly, hourly, yearly, unknown`; `availability ∈ open, closed, unconfirmed`; `user_decision ∈ none, discarded, applied`; `decision_reverted_at?`), `opportunity_source` (unique `(source, external_id)` e unique `canonical_url`), `opportunity_match_candidate` (`state ∈ open, merged_by_user, rejected`), `daily_selection` (PK `(local_date, opportunity_id)`) em `MIG/V6__opportunities.sql`
- [x] T038 [P] [US1] Implementar repositórios `OpportunityRepository`, `OpportunitySourceRepository`, `MatchCandidateRepository` em `B/opportunities/`
- [X] T039 [US1] Implementar normalização de URL canônica e `DedupeService` (identidade forte funde fontes; fraca só sugere) em `B/opportunities/DedupeService.java`
- [x] T040 [US1] Implementar `ObjectiveFilter` e `RuleBasedRanker` (fit `strong|medium|weak`, `fit_reasons` objetivos, empresas prioritárias, júnior só com justificativa registrada) em `B/opportunities/ObjectiveFilter.java` e `B/opportunities/RuleBasedRanker.java`
- [x] T041 [US1] Implementar `DailySelectionService` com verificação `count(local_date) ≤ 10` na mesma transação, elegibilidade (`user_decision = none` e nunca selecionada) e lista secundária em `B/opportunities/DailySelectionService.java` (FR-004, FR-005)
- [x] T042 [P] [US1] Definir interface `JobSource` (`fetch(): SourceResult{items, failures, limitations}`) e adaptador de entrada manual (URL ou texto) em `B/sources/JobSource.java` e `B/sources/ManualSource.java`
- [x] T043 [P] [US1] Implementar adaptador de carreiras públicas Gupy por empresa com HTTP simples, User-Agent identificado, 3 tentativas com backoff em `B/sources/GupySource.java` (D-08; registrar como restrito)
- [x] T044 [P] [US1] Implementar adaptador genérico de boards públicos de ATS (Greenhouse/Lever/Workable) configurável por `company.careers_adapter` em `B/sources/AtsBoardSource.java`
- [x] T045 [US1] Implementar `SearchService` sob demanda, registrando fontes consultadas/falhas em `TaskRun` e `IntegrationStatus(source:<id>)` em `B/sources/SearchService.java` (FR-003)
- [x] T045a [US1] Persistir `published_at`, extrair data quando disponível, aplicar janela padrão de 30 dias e aceitar `publishedFrom`/`publishedTo` em buscas manuais; datas indisponíveis ficam sinalizadas como desconhecidas em `MIG/V6_5__opportunity_publication_date.sql`, `B/sources/` e `F/pages/Opportunities.tsx` (FR-003, SC-009)
- [x] T045b [US1] Deixar rotina diária de busca desabilitada por padrão e só agendá-la quando habilitada pelo usuário (FR-003)
- [x] T046 [US1] Implementar rotas `GET /api/opportunities`, `GET /api/opportunities/{id}` (facts/inferred/missing), `POST …/discard`, `…/restore`, `POST /api/opportunities/manual`, `POST /api/searches` (`202 {taskRunId}`), `GET|POST /api/dedupe-candidates…` em `B/opportunities/OpportunityController.java`
- [x] T047 [US1] Implementar `GET /api/home` (`dataAsOf` = último sucesso real ou `null`, `selectionToday`, `applicationsWithNextActions` por prazo, `mailNeedingAttention`, `reviewQueueCount`, `missedRoutines`) em `B/app/web/HomeController.java` (FR-016)
- [x] T048 [P] [US1] Criar páginas Início, Vagas (seleção/secundária/descartadas) e Detalhe da vaga com destaque de prazos ≤7 dias e urgência ≤48 h/vencidos em `F/pages/Home.tsx`, `F/pages/Opportunities.tsx`, `F/pages/OpportunityDetail.tsx`
- [x] T049 [P] [US1] Criar tela de revisão de possíveis duplicatas em `F/pages/DedupeReview.tsx`

**Checkpoint**: US1 validável isoladamente (quickstart §3, linhas J1).

---

## Phase 5: User Story 2 — Criar, revisar e controlar bots por conversa (P1)

**Goal**: quatro bots iniciais revisáveis, criador conversacional restrito a emprego, coordenador, atividades.
**Independent Test**: pedir bot de busca Java, revisar proposta, ativar, pausar e consultar atividade.
**Depende de**: Fase 3.

### Tests (US2)

- [ ] T050 [P] [US2] Teste: bot em `draft`/`in_review` com rotina vencida não executa; após aprovação executa só capacidades aprovadas; mudança de config volta a `in_review` mantendo a ativa em `BT/bots/BotLifecycleTest.java` (US2-2, US2-3, SC-008)
- [ ] T051 [P] [US2] Teste: `bot.propose@1` com `inScope=false` não cria nada; capacidade fora do catálogo é recusada em `BT/bots/BotCreatorServiceTest.java` (US2-4)
- [ ] T052 [P] [US2] Teste e2e J2: conversa com criador, revisão, ativação, pausa, consulta de atividade em `E2E/j2-bots.spec.ts`

### Implementation (US2)

- [ ] T053 [US2] Criar migração `bot` (`role ∈ coordinator, search, tracking, preparation, custom`; estados `draft → in_review → active ⇄ paused`, `rejected` terminal), `bot_config` imutável (`capabilities ⊆ job.search, job.rank, mail.classify, draft.prepare, interview.simulate, profile.read`; `content_hash` SHA-256), `conversation`, `chat_message`, `routine` em `MIG/V7__bots.sql`
- [ ] T054 [US2] Semear propostas iniciais (estado `in_review`) de Coordenador, Busca, Acompanhamento, Preparação com `openbot_agent_name` `curriculotron-coordenador|-busca|-acompanhamento|-preparacao` em `MIG/V8__seed_bots.sql` (FR-008)
- [ ] T055 [US2] Implementar `BotService` (aprovação por `contentHash`, ativação, pausa, retomada, invariante "só executa se `state = active` e usa `active_config_id`") em `B/bots/BotService.java`
- [ ] T056 [US2] Implementar `CapabilityGuard` que bloqueia e registra em `ActivityLog` qualquer ação fora da config ativa em `B/bots/CapabilityGuard.java`
- [ ] T057 [US2] Implementar `BotCreatorService` (conversa via agente coordenador, `bot.propose@1`, perguntas de esclarecimento, `BotConfig` em `in_review`) em `B/bots/BotCreatorService.java` (FR-007)
- [ ] T058 [US2] Implementar `CoordinatorService` (`task.interpret@1` ⇒ operações permitidas; distribuição determinística para bots ativos; `adjust_criteria` gera proposta a confirmar) em `B/orchestration/CoordinatorService.java` (FR-009)
- [ ] T059 [US2] Implementar `ModelRanker` usando `job.rank@1` do bot Busca, aplicando `ObjectiveFilter` antes e depois e mantendo `RuleBasedRanker` como padrão quando o bot estiver inativo em `B/opportunities/ModelRanker.java`
- [ ] T060 [US2] Implementar verificação de agentes OpenBot por nome (existência, Local scripts) exibida em `IntegrationStatus(openbot)` em `B/bots/AgentBindingService.java`
- [ ] T061 [US2] Implementar rotas `GET /api/bots…`, `POST /api/bot-creator/messages`, `POST /api/bots/{id}/configs/{configId}/approve`, `…/pause|resume`, `POST /api/conversations/{id}/messages`, `GET /api/task-runs…`, `POST /api/task-runs/{id}/resume`, `GET /api/activity` em `B/bots/BotController.java` e `B/orchestration/TaskRunController.java`
- [ ] T062 [P] [US2] Criar páginas Bots (lista, diff entre config ativa e proposta, aprovar/pausar), Criador/Conversa e Atividades (responsável, ação, fontes, resultado, falhas, pendências) em `F/pages/Bots.tsx`, `F/pages/Conversation.tsx`, `F/pages/Activity.tsx`

**Checkpoint**: US2 validável (quickstart §3, linhas J2; §4.1).

---

## Phase 6: User Story 3 — Registrar e corrigir candidaturas (P1)

**Goal**: "Me candidatei", histórico com autoria, correção manual, materiais sem invenção.
**Independent Test**: abrir link, confirmar candidatura, registrar evidência e corrigir status.

### Tests (US3)

- [x] T063 [P] [US3] Teste: nenhuma rota cria candidatura por clique; `POST …/applied` cria `Application(confirmation_origin=manual)` e segundo envio retorna `409`; tempo sem resposta não altera status em `BT/applications/ApplicationServiceTest.java` (US3-1, US3-2, US3-4)
- [ ] T064 [P] [US3] Teste: correção manual cria `StatusChange(author=user)` e evidência automática conflitante posterior gera `ReviewItem(kind=conflict)` sem sobrescrever em `BT/applications/StatusHistoryTest.java` (US3-3, D-09)
- [x] T065 [P] [US3] Teste e2e J3 em `E2E/j3-applications.spec.ts`

### Implementation (US3)

- [x] T066 [US3] Criar migração `application` (`opportunity_id` unique; `status ∈ submitted_awaiting, contact_received, interview, offer, closed_rejected, withdrawn`; `due_precision ∈ exact, date_only, ambiguous`), `status_change` append-only, `evidence` (`kind ∈ mail, user_action, source_page`), `review_item` (`kind ∈ ambiguous_mail, dedupe_candidate, conflict, bot_config, salary_ambiguous`) em `MIG/V9__applications.sql`
- [ ] T067 [US3] Implementar `ApplicationService` (criação manual marca `opportunity.user_decision=applied`; mudança `author=bot:*` exige `evidence_id`) em `B/applications/ApplicationService.java` (FR-011)
- [ ] T068 [P] [US3] Implementar `ReviewQueueService` e rotas `GET /api/review-items`, `POST /api/review-items/{id}/resolve` em `B/applications/ReviewQueueService.java` e `B/applications/ReviewController.java`
- [x] T069 [US3] Implementar rotas `POST /api/opportunities/{id}/applied`, `GET /api/applications…`, `POST …/status`, `POST …/next-action` em `B/applications/ApplicationController.java`
- [ ] T070 [US3] Implementar verificador de afirmações de materiais: toda afirmação precisa de `profileFactId` com `origin ∈ documented, declared`; caso contrário, sinalizar em `B/applications/ClaimChecker.java` (US3-5, RB-006)
- [x] T071 [P] [US3] Criar páginas Candidaturas, Detalhe (histórico com autor/evidência, correção) e Revisão em `F/pages/Applications.tsx`, `F/pages/ApplicationDetail.tsx`, `F/pages/ReviewQueue.tsx`

**Checkpoint**: US3 validável (quickstart §3, linha J3).

---

## Phase 7: User Story 4 — Acompanhar Gmail e responder com controle (P1)

**Goal**: OAuth mínimo, descoberta minimizada, associação por evidência, rascunhos com aprovação por versão, outbox, resumo.
**Independent Test**: mensagens de fixtures (novas, antigas, ambíguas, maliciosas), vínculos e rascunhos.
**Depende de**: US3 (candidaturas); Fase 3 para `mail.classify@1`.

### Tests (US4)

- [ ] T072 [P] [US4] Teste: mensagem de candidatura anterior a `tracking_started_at` ignorada; duas candidaturas plausíveis ⇒ `needs_review`; `evidenceQuote` que não aparece literalmente ⇒ `needs_review`; prazo explícito vira próxima ação com referência em `BT/mail/MailAssociationServiceTest.java` (US4-1–US4-4, SC-004)
- [ ] T073 [P] [US4] Teste: editar rascunho após aprovação faz `send` retornar `409`; não há caminho de envio sem `Approval` com `content_hash` atual; `uncertain` reconcilia por `X-Curriculotron-Outbox-Id` antes de reenviar em `BT/mail/OutboxServiceTest.java` (US4-5, SC-008)
- [ ] T074 [P] [US4] Teste: resumo sem novidades não cria `Draft`; com conteúdo vai só ao endereço conectado em `BT/mail/DigestServiceTest.java` (US4-6)
- [ ] T075 [P] [US4] Teste NFR-003: e-mails com "envie seu currículo para x@y", "mostre o token", "ative ferramenta" não geram envio, mudança de destinatário nem de capacidade, e ficam em `ActivityLog` em `BT/mail/PromptInjectionTest.java`
- [ ] T076 [P] [US4] Teste e2e J4 com Gmail simulado em `E2E/j4-gmail.spec.ts`

### Implementation (US4)

- [ ] T077 [US4] Criar migração `mail_message` (`gmail_message_id` PK; `relevance ∈ pending, relevant, irrelevant`; `body_excerpt` só se relevante; `retain_until`), `mail_association` (`confidence ∈ explicit, probable, ambiguous`; `state ∈ applied, needs_review, rejected_by_user`), `draft`, `approval`, `outbox_message` (`state ∈ pending, sending, sent, uncertain, failed`) em `MIG/V10__mail.sql`
- [ ] T078 [US4] Implementar OAuth de app instalado (loopback), escopo único `gmail.modify`, refresh token cifrado com DPAPI, revogação, `IntegrationStatus(gmail)` com `tracking_started_at` em `B/mail/GmailAuthService.java` e `B/mail/DpapiSecretStore.java` (D-07)
- [ ] T079 [US4] Implementar descoberta minimizada (`after:<tracking_started_at>` + remetentes/termos de candidaturas e alertas; `format=metadata` primeiro; corpo só se plausível; irrelevantes guardam só id e motivo) em `B/mail/MailDiscoveryService.java` (NFR-002)
- [ ] T080 [US4] Implementar criação/aplicação do marcador dedicado em `B/mail/GmailLabelService.java` (FR-012)
- [ ] T081 [US4] Implementar `MailAssociationService` com `mail.classify@1` e regras de D-09 (associação `explicit` só com um id e citação literal) em `B/mail/MailAssociationService.java` (FR-013)
- [ ] T082 [P] [US4] Implementar parser de alertas de vagas do LinkedIn recebidos no Gmail como `JobSource` restrito em `B/sources/LinkedInAlertSource.java` (D-08)
- [ ] T083 [US4] Implementar `DraftService` (`draft.prepare@1`, destinatários nunca vindos do modelo, `version`/`content_hash` por edição, `ClaimChecker`) em `B/mail/DraftService.java` (FR-014)
- [ ] T084 [US4] Implementar `OutboxService` (envio só com `Approval` válida, chave idempotente, cabeçalho `X-Curriculotron-Outbox-Id`, reconciliação em "Enviados", sem repetição automática de incerto) em `B/mail/OutboxService.java`
- [ ] T085 [US4] Implementar `DigestService` como rotina opcional (`purpose=digest`, destinatário = conta conectada, suprime vazio, destaca prazos/entrevistas) em `B/mail/DigestService.java` (FR-015)
- [ ] T086 [US4] Implementar rotas `POST|DELETE /api/integrations/gmail…`, `GET|PUT /api/drafts/{id}`, `POST …/approve`, `POST …/send`, `PUT /api/mail-associations/{gmailMessageId}`, `PUT /api/settings/digest` em `B/mail/MailController.java`
- [ ] T087 [P] [US4] Criar páginas Integrações (Gmail conectar/revogar/estado), Mensagens que exigem atenção e Rascunho (diff, destinatários, aprovar, enviar) em `F/pages/Integrations.tsx`, `F/pages/Mail.tsx`, `F/pages/DraftEditor.tsx`

**Checkpoint**: US4 validável (quickstart §3 linhas J4; §4.2 manual).

---

## Phase 8: User Story 5 — Configurar perfil e operar com falhas visíveis (P1)

**Goal**: edição de perfil/critérios/empresas, estados de integração reais, pausa por limite, retomada sem duplicar, privacidade.
**Independent Test**: editar perfil e empresas; simular fonte indisponível, desligamento e limite do provedor.

### Tests (US5)

- [ ] T088 [P] [US5] Teste: uma fonte falha e outra responde ⇒ resultado parcial com fonte não consultada e sem indicação de cobertura completa em `BT/sources/PartialSearchTest.java` (US5-2)
- [ ] T089 [P] [US5] Teste: `429`/limite ⇒ `paused(provider_limit)` com concluído/pendente e sem chamada a `applySettings` em `BT/orchestration/ProviderLimitTest.java` (US5-3, FR-018)
- [ ] T090 [P] [US5] Teste de interrupção: matar o processo antes e depois de gravar seleção/candidatura/mensagem e reiniciar ⇒ nenhum registro duplicado, rotinas perdidas listadas em `BT/orchestration/RestartResilienceTest.java` (US5-4, NFR-004, SC-007)
- [ ] T091 [P] [US5] Teste e2e de privacidade: visitante sem sessão em 360 px não obtém dados, arquivos nem mensagens em `E2E/j5-privacy.spec.ts` (US5-5)

### Implementation (US5)

- [ ] T092 [US5] Implementar rotas `GET|PUT /api/profile`, `POST /api/profile/import`, `GET|PUT /api/criteria`, `GET|POST|PUT /api/companies`, `POST /api/companies/{id}/promote` (sugeridas só viram prioridade por ação do usuário) em `B/profile/ProfileController.java` (FR-001, FR-002)
- [ ] T093 [US5] Implementar sugestões de empresas em lista secundária `tier=suggested` em `B/profile/CompanySuggestionService.java` (RB-005)
- [ ] T094 [US5] Implementar retomada de `TaskRun` pausado a partir do `checkpoint` reutilizando chaves idempotentes em `B/orchestration/ResumeService.java`
- [ ] T095 [US5] Implementar backup diário rotativo (7 cópias) do SQLite em `%LOCALAPPDATA%\Curriculotron\backups` em `B/app/BackupRoutine.java` (D-11)
- [ ] T096 [US5] Implementar `GET /api/data-inventory`, `DELETE /api/applications/{id}/mail-content`, `POST /api/data/erase-all` (exige senha; revoga Gmail) e expiração por `retain_until` em `B/app/web/DataController.java` e `B/mail/RetentionRoutine.java` (PT-008)
- [ ] T097 [P] [US5] Criar páginas Perfil (fatos com origem documentada/declarada), Critérios, Empresas (prioritárias/sugeridas) e Dados/Privacidade em `F/pages/Profile.tsx`, `F/pages/Criteria.tsx`, `F/pages/Companies.tsx`, `F/pages/DataPrivacy.tsx`
- [x] T098 [P] [US5] Exibir em Integrações a última atualização bem-sucedida, última tentativa, falhas, pausas com motivo, botão de retomada e aviso "acesso só na Wi-Fi de casa" em `F/pages/Integrations.tsx`

**Checkpoint**: US5 validável (quickstart §3 linhas J5).

---

## Phase 9: User Story 6 — Preparar entrevista para uma vaga (P2)

**Goal**: simulação textual técnica/comportamental em PT/EN, uma pergunta por vez, feedback ligado à vaga.
**Independent Test**: uma simulação curta em PT e outra em EN.
**Depende de**: Fase 3; US1 (vagas).

### Tests (US6)

- [ ] T099 [P] [US6] Teste: nova pergunta só após resposta do turno anterior; feedback gera `ProfileFact(origin=study_recommended)` apenas em recomendações e nunca altera fatos `documented|declared` em `BT/interview/InterviewServiceTest.java` (US6-1, US6-3, SC-006)
- [ ] T100 [P] [US6] Teste e2e J6 (PT técnica, EN comportamental) em `E2E/j6-interview.spec.ts`

### Implementation (US6)

- [ ] T101 [US6] Criar migração `interview_session` (`language ∈ pt, en`; `kind ∈ technical, behavioral`; `state ∈ active, finished`) e `interview_turn` `(session_id, seq, question, answer?)` em `MIG/V11__interview.sql`
- [ ] T102 [US6] Implementar `InterviewService` com `interview.turn@1` e `interview.feedback@1` via bot Preparação em `B/interview/InterviewService.java` (FR-019, FR-020)
- [ ] T103 [US6] Implementar rotas `POST /api/interviews`, `POST /api/interviews/{id}/answers`, `POST /api/interviews/{id}/finish` em `B/interview/InterviewController.java`
- [ ] T104 [P] [US6] Criar página de simulação (uma pergunta por vez, feedback com origem explícita de cada recomendação) em `F/pages/Interview.tsx`

**Checkpoint**: US6 validável (quickstart §3 linha J6).

---

## Phase 10: Polish & Cross-Cutting

- [ ] T105 [P] Teste de acessibilidade axe-core e navegação só por teclado em todas as páginas, larguras 360 e 1280 px, sem rolagem horizontal em `E2E/a11y.spec.ts` (NFR-005)
- [ ] T106 [P] Teste de desempenho: 20 aberturas da Início com dados locais, p95 ≤ 3 s em `frontend/tests/e2e/perf/home.spec.ts` (NFR-006)
- [ ] T107 [P] Teste que varre logs, `activity_log`, respostas de erro e mensagens ao modelo procurando token OpenBot, refresh token e senha em `BT/audit/SecretLeakTest.java` (NFR-001, NFR-007)
- [ ] T108 Criar scripts de operação: gerar certificado mkcert para o IP da LAN e regra de firewall TCP 8443 só no perfil Privado em `scripts/setup-lan-https.ps1` (quickstart §5)
- [ ] T109 [P] Criar script de inicialização do serviço no logon do Windows em `scripts/install-startup.ps1`
- [ ] T110 [P] Documentar instalação e operação (pré-requisitos, agentes OpenBot, conexão Gmail, limitações LinkedIn/Gmail/fora de casa) em `README.md`
- [ ] T111 Executar quickstart §2–§6 completo e registrar resultados (incluindo expiração do token Gmail em modo Testing) em `specs/001-assistente-busca-emprego/research.md`

---

## Dependencies & Execution Order

```text
Setup (F1) → Foundational (F2) ─┬─→ US1 (F4) ─→ US3 (F6) ─→ US5 (F8)
                                │                    │
                                └─→ Gate OpenBot (F3) ┼─→ US2 (F5)
                                                      ├─→ US4 (F7)  [requer US3]
                                                      └─→ US6 (F9)  [requer US1]
Todas as histórias → Polish (F10)
```

- **F3 (Gate)** pode rodar em paralelo a US1/US3/US5 assim que F2 terminar; T025 depende só do usuário e do OpenBot aberto.
- **US1** é independente do OpenBot (ranking por regras). US2 acrescenta `ModelRanker` sem quebrar US1.
- **US3** usa `opportunity` de US1 (T037).
- **US4** usa `application`/`review_item` de US3 (T066) e o adaptador da F3.
- **US5** usa `TaskRun` (F2) e as fontes de US1; T089 usa o adaptador da F3 (via WireMock, não exige OpenBot real).
- Dentro de cada história: testes → migração → repositórios → serviços → rotas → UI.

## Parallel Examples

```text
# F2 após T010–T011:
T012, T016, T017, T023, T024

# US1 testes juntos:
T033, T034, T035, T036
# US1 adaptadores de fonte juntos (após T042):
T043, T044

# US4 testes juntos:
T072, T073, T074, T075, T076

# Entre histórias (após F2 e F3):
US1 (dev A) | Gate + US2 (dev B)
```

## Implementation Strategy

### MVP

1. F1 Setup → F2 Foundational.
2. F4 **US1** com ranking por regras e fontes manual/Gupy/ATS.
3. **Parar e validar** J1 (quickstart §3) e acesso pelo celular (quickstart §5).

### Entrega incremental

1. MVP (US1) → US3 (candidaturas) → US5 (perfil e falhas visíveis): produto útil sem OpenBot nem Gmail.
2. Gate OpenBot (T025) → US2 (bots e ranking por modelo).
3. US4 (Gmail) → US6 (entrevista) → Polish.

## Notes

- Nenhuma tarefa envia candidatura, preenche portal ou manda e-mail a terceiros sem `Approval` (SC-008).
- Falha no T025 interrompe F3, US2, US4-classificação e US6 até decisão de escopo do usuário.
- Commitar por tarefa ou grupo lógico; validar cada checkpoint antes de avançar.

## Incremento frontend — 2026-11-14

T048, T049, T071 e T098 implementadas no cliente React com chamadas à API tipada, CSRF, estados de erro/ausência/carregamento, fatos e inferências separados, inscrição explícita, histórico e resolução de revisão. T097 parcialmente implementada: perfil com origens, critérios, empresas prioritárias/sugeridas e inventário; faltam edição de empresas existentes e exclusão de dados. Atividades implementada como suporte à busca e retomada.

Verificação isolada do frontend em `frontend/tests/e2e/frontend.spec.ts`, executada com `npm run test:frontend`: API simulada exclusivamente no teste. Cobertura de interface para T036/T065/T091/T105; essas tarefas permanecem abertas porque os checkpoints de domínio e todas as jornadas integradas ainda não estão concluídos. Sem execução do gate OpenBot, integração Gmail ou envio a terceiros. A disponibilidade real das telas depende dos endpoints T046/T047/T068/T069/T092/T094/T096 e demais serviços do backend.

## Incremento backend — filtro salarial

T035 concluída com quatro testes de regras salariais. T040 parcialmente implementada em opportunities/domain/ObjectiveFilter.java: mínimos CLT/PJ configuráveis, salário ausente e faixa que cruza o mínimo exigem confirmação, unidades não mensais não são convertidas e faixas inválidas são rejeitadas. Ranking e demais filtros permanecem pendentes; o filtro ainda não está ligado às rotas de vagas.

Validação: teste inicialmente falhou pela ausência da implementação; após implementação, gradlew spotlessApply test check passou (49 testes, zero falhas). integrationTest permanece sem fontes; isso não valida J1 de ponta a ponta.

## Incremento backend — seleção persistida e ranking

T033/T037/T040/T041: migração V6 de vagas, fontes, possíveis duplicatas e seleção; seleção transacional pelo WriteExecutor, teto diário de dez, somente fit forte, exclusão de vagas fechadas/descartadas/com candidatura e nunca repetir seleção anterior. Lista secundária mantém excedentes e vagas fracas. Ranking puro por fatos normalizados de função/tecnologia, localização, empresa prioritária e salário, com justificativa obrigatória para júnior. A ingestão ainda precisa fornecer fatos normalizados e persistir o resultado do ranking; não há integração das rotas/UI neste incremento.

Testes escritos antes da implementação: três cenários com SQLite real para seleção (incluindo concorrência) e três de ranking. Validação: gradlew spotlessApply test check, 55 testes sem falhas; integrationTest segue sem fontes. T038/T039 e integração de fontes/rotas permanecem pendentes.

## Incremento backend — deduplicação

Implementados CanonicalUrl, DedupeService, porta DedupeRepository e adaptador JPA com SQL nativo. URL canônica remove apenas parâmetros de tracking conhecidos, preservando parâmetros de identidade. Identidade forte reutiliza vaga sem alterar decisão; identidade fraca por título/empresa gera candidato de revisão. Identidades fortes conflitantes também geram revisão. Evidência original é preservada; aliases adicionais de URL para um mesmo external_id ainda não são armazenados devido à unicidade da origem.

T034/T038/T039 permanecem abertas até completar aliases, demais repositórios e cenários de integração. Entrada manual e rotas T042/T046 ainda pendentes. Validação: gradlew spotlessApply test check; 60 testes passaram, incluindo cinco novos testes de URL/deduplicação. integrationTest segue sem fontes.

## Incremento backend — aliases de identidade

Persistência independente de aliases de URL e de identidade externa em V6_1, com backfill das fontes existentes. Uma vaga reconhecida por ID externo agora conserva URLs adicionais; uma URL reconhecida também conserva IDs de outras fontes. A evidência original e a decisão do usuário são preservadas. Teste de regressão com SQLite real falhou antes da correção e passou depois.

Validação: gradlew spotlessApply test check aprovado; integrationTest continua sem fontes. T034/T038/T039 permanecem abertas para completar os cenários e repositórios previstos; entrada manual e integração das rotas seguem pendentes.


## Incremento backend — fonte manual

T042 concluída: contrato JobSource com itens, falhas e limitações; ManualSource aceita URL e/ou texto, valida e normaliza URLs e preserva texto original. Não navega URLs nem infere fatos. Entrada somente textual mantém URL ausente; a integração de persistência e a rota permanecem em T046.

Validação: três testes novos (texto, URL e entradas inválidas); gradlew spotlessApply test check aprovado. integrationTest continua sem fontes.

## Validação da deduplicação — 2026-11-14

T034/T039 concluídas: identidade forte por URL e identificador externo preserva decisões e aliases; identidade fraca somente sugere revisão. Corrigida a comparação fraca para incluir localização conforme D-09. Teste de regressão falhou antes da correção e passou depois. Validação: gradlew spotlessApply test check aprovado; integrationTest continua sem fontes. T038 e integração ponta a ponta do MVP continuam pendentes.

## Incremento backend — fontes de carreira e busca ponta a ponta — 2026-11-14

T043/T044/T045 concluídas. Mapeamento das empresas prioritárias (migração V6_3, verificado ao vivo):
BV → `lever:bv`; CI&T → `lever:ciandt`; PicPay → `oracle:epdm.fa.la1/CX_3001` (picpay.com/carreiras, Oracle Recruiting Cloud); CWI → `cwi:oportunidades` (cwi.com.br/talentos/oportunidades, fonte restrita); DB (antes "Digital Business", db.tec.br) → `gupy:db` (fonte restrita; LinkedIn guardado só como referência, sem acesso direto — D-08). SAP sem fonte automática: jobs.sap.com bloqueia acesso automatizado (Cloudflare); entrada manual.

Novos adaptadores: GupySource, OracleRecruitingSource, CwiCareersSource e JobSourceFactory (aceita só `provider:conta` conhecidos, nunca URL arbitrária). JobFactExtractor extrai apenas fatos literais (título, local, modalidade, senioridade, tecnologias citadas); SearchService aplica ObjectiveFilter + RuleBasedRanker antes de persistir e roda a seleção diária; rotina diária `search` criada no início no horário dos critérios. Teste de integração com SQLite real: fontes com falha ficam visíveis no TaskRun, vaga forte entra na seleção, fraca na secundária, segunda busca não duplica. Validação: gradlew spotlessApply test check aprovado; smoke ao vivo leu BV 12, CI&T 114, PicPay 1, CWI 13 e DB 20 vagas.

## Fechamento do MVP US1 — 2026-11-14

T038/T046/T047 concluídas após conferência com `contracts/panel-api.md`. Os três repositórios (em `opportunities/application/port/out` + `adapter/out/persistence`) agora têm testes de criação a partir de fonte, descarte/restauração e recusa de decisão fora da rota. Correções em T046: `date` inválido em `GET /api/opportunities` passou a responder `400 invalid_parameter` (antes caía no 500 genérico); o detalhe lista em `missing` também "Tipo de contrato a confirmar" e "Localização a confirmar". `POST /api/searches` está em `sources/SearchController`. T047: `HomeApiTest` cobre `dataAsOf = null` sem sucesso real, `selectionToday`, `reviewQueueCount` e integrações. `applicationsWithNextActions` e `mailNeedingAttention` continuam vazios até US3/US4, sem dados sintéticos.

T036 implementada em `frontend/tests/e2e/j1-review.spec.ts`: 8 cenários passaram (4 em 360 px e 4 em 1280 px) contra o app real. `scripts/e2e-backend.mjs` gera o build do frontend e o jar e sobe o app em um diretório descartável (`backend/build/e2e-home`), só com HTTP em 127.0.0.1 e agendador desligado, sem acessar portais reais. Os testes semeiam o SQLite via `node:sqlite` (`tests/e2e/support/seed.ts`). Cobertos: seleção com motivos, detalhe com "salário a confirmar", fatos separados das inferências, fonte, link e data, descartar/restaurar, lista secundária e entrada manual. **T036 continua aberta só pelo item "prazos destacados na página inicial"**: os prazos vêm de candidaturas (US3), que ainda não existem no backend. O botão "Me candidatei" chama `/applied`, também de US3. `playwright.frontend.config.ts` agora substitui o `webServer` em vez de somar ao do app real.

Validação: `gradlew spotlessApply test check` passou. No frontend passaram eslint, tsc, vitest (23 testes), `test:frontend` (32 testes) e J1 e2e (8 testes). Capturas manuais de Início e Detalhe em 360/1280 px sem rolagem horizontal.

## Incremento: candidaturas manuais e prazos (2026-11-14)

Concluídas T063, T065, T066 e T069. A migração V9 acrescenta candidaturas, evidências, histórico append-only e estrutura da fila de revisão. As rotas reais permitem confirmação manual, lista/detalhe, correção de etapa com motivo e próxima ação com prazo. A confirmação é atômica com a decisão da vaga, recusa duplicatas e remove a vaga das recomendações. O início usa as próximas ações persistidas, por ordem de prazo, sem alterar etapas por tempo decorrido. T036 concluída: os destaques de prazo foram verificados na jornada J3 em 360/1280 px.

T067 parcialmente implementada: fluxo manual pronto; aplicação de mudanças automáticas com evidência e tratamento de conflitos ainda pendentes junto a T064/T068. O banco já recusa histórico de bot sem evidência. T070 (verificação de afirmações) permanece pendente. O gate OpenBot continua pendente; nenhuma integração foi declarada validada.

Validação: backend `gradlew test check` passou; frontend eslint e Vitest (23 testes) passaram; build TypeScript/Vite e J1/J3 no app isolado passaram (10 cenários). Ajustadas as bordas decorativas dos botões para não criar rolagem horizontal e a exibição dos motivos no celular. Nenhuma consulta a portais reais ou uso de dados da instalação pessoal nos testes.