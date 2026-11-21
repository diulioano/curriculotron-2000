# Tasks: Alinhamento aos Padrões Visuais e Biblioteca de Componentes

**Input**: Design documents from `/specs/005-padroes-visuais-componentes/`  
**Prerequisites**: [plan.md](file:///C:/Users/Diuliano/Documents/curriculotron-2000/specs/005-padroes-visuais-componentes/plan.md) (required), [spec.md](file:///C:/Users/Diuliano/Documents/curriculotron-2000/specs/005-padroes-visuais-componentes/spec.md) (required), [research.md](file:///C:/Users/Diuliano/Documents/curriculotron-2000/specs/005-padroes-visuais-componentes/research.md), [data-model.md](file:///C:/Users/Diuliano/Documents/curriculotron-2000/specs/005-padroes-visuais-componentes/data-model.md), [visual-library-and-correction-api.md](file:///C:/Users/Diuliano/Documents/curriculotron-2000/specs/005-padroes-visuais-componentes/contracts/visual-library-and-correction-api.md), [quickstart.md](file:///C:/Users/Diuliano/Documents/curriculotron-2000/specs/005-padroes-visuais-componentes/quickstart.md)

**Organization**: Tasks are grouped by user story (`US1`..`US4`) in priority order (P1, P1, P2, P3) so each increment is independently testable. `design/` permanece estritamente local e nunca deve ser commitado ou empacotado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Pode rodar em paralelo (arquivos distintos, sem dependências de tarefas incompletas)
- **[Story]**: História de usuário correspondente (`[US1]`, `[US2]`, `[US3]`, `[US4]`)
- Todos os caminhos de arquivo são relativos à raiz do repositório (`C:\Users\Diuliano\Documents\curriculotron-2000`)

---

## Phase 1: Setup (Alinhamento e Base de Tokens)

**Purpose**: Garantir a linha de base sincronizada com `origin/main` (`e9afc88`+), proteção contínua de `design/` e tokens CSS centrais antes da implementação.

- [X] T001 Sincronizar/rebasear a branch de trabalho sobre `origin/main` (`e9afc88` ou mais recente) e confirmar que `/design/` permanece em `.gitignore` e ausente de `git ls-files design/` e de `frontend/dist`
- [X] T002 Atualizar tokens de contraste e texturas base (`--ok: #1F6B0E`, variáveis de hachura de ausência `#C9C2AE` e desatualizado `#E2DCCB`, borda de grafite `#6B6B73`, e `prefers-reduced-motion` para `.cloud`, `.bt` e transições interativas) em `frontend/src/index.css`
- [X] T003 [P] Atualizar folha de estilos base do painel com classes utilitárias dos 6 estados de superfície, 4 marcadores epistêmicos e regras responsivas sem overflow horizontal em `390px` e `1280px` em `frontend/src/app/panel.css`

---

## Phase 2: Foundational (Infraestrutura Compartilhada Backend & Frontend)

**Purpose**: Criar a base de persistência (`V14`), projeções compartilhadas, componente de estados e marcadores epistêmicos que bloqueiam as histórias `US1`–`US4`.

**⚠️ CRITICAL**: Nenhuma tarefa das fases `US1`–`US4` deve começar antes da conclusão desta fase.

- [X] T004 Criar migração Flyway `V14` com tabela append-only `opportunity_correction` (`id TEXT PRIMARY KEY`, `opportunity_id TEXT NOT NULL REFERENCES opportunity(id)`, `field_name TEXT NOT NULL CHECK(field_name IN ('title','company_name','location','work_mode','contract_type','salary_min','salary_max','salary_unit','availability','deadline_at','published_at'))`, `previous_value TEXT`, `corrected_value TEXT`, `reason TEXT CHECK(reason IS NULL OR length(reason) <= 500)`, `author TEXT NOT NULL DEFAULT 'user' CHECK(author = 'user')`, `corrected_at TEXT NOT NULL`), índice `opportunity_correction_opp_at` e triggers `BEFORE UPDATE`/`BEFORE DELETE` bloqueando alterações em `backend/src/main/resources/db/migration/V14__opportunity_corrections.sql`
- [X] T005 [P] Criar componentes compartilhados de padrão epistêmico (`FactItem` com `✓` e `profileMatch?: 'matched' | 'missing_from_profile' | null`, `InferenceBox` com `~` + `.dsh`/`.dsh-b` e fallback `'inferido pelo sistema'`, `AbsentValue` com `.hatch` `#C9C2AE` e faixa legível `--paper`/`--card`, e `EvidenceBlock` em `Space Mono` com `fonte:`, `consultado:`, `publicada:`, última correção e acionador `onCorrect`) em `frontend/src/components/Epistemic.tsx`
- [X] T006 [P] Criar componente compartilhado `SurfaceStateBlock` cobrindo os 6 estados oficiais (`empty` -> `VAZIO` `.sk`, `loading` -> `CARREGANDO` `.dsh` + barra `.hatch`, `partial` -> `PARCIAL` borda `2px solid var(--ink)` + contagem `X de Y`, `error` -> `FALHA` `.dsh` `--danger`, `stale` -> `DESATUALIZADO` `.hatch` `#E2DCCB` com faixas `--paper`/`--card`, `pending_review` -> `PENDENTE DE REVISÃO` `.hl` + borda `2px solid var(--ink)`) em `frontend/src/components/SurfaceState.tsx`
- [X] T007 [P] Criar enums/records de domínio `CorrectableOpportunityField`, `OpportunityCorrectionEntry`, `OpportunityTimelineEvent` (`TimelineEventKind`: `discovered | selected_today | external_opened | corrected | discarded | restored | applied`) e `SeniorityAssessment` (`status`: `fact | corrected_title | inferred | absent`) em `backend/src/main/java/dev/curriculotron/opportunities/domain/`
- [X] T008 Atualizar tipos e parsers runtime de API (`TaskRun` expondo `kind`, `sourcesConsulted: string[]`, `sourcesFailed: string[]`, `startedAt`, `finishedAt`, `createdAt`; `HomeData` com `pendingReviewItems`; `OpportunityDetailData` com `seniority`, `corrections`, `timeline`; funções `correctOpportunity` e `recordExternalVisit` usando `post()`) em `frontend/src/api/panel.ts`, `frontend/src/api/compatibility.ts` e `frontend/src/api/compatibility-detail.ts`

**Checkpoint**: Fundação pronta — as histórias `US1`, `US2`, `US3` e `US4` podem avançar de forma organizada e paralela por arquivos.

---

## Phase 3: User Story 1 - Distinguir fatos, inferências, ausências e evidências em todas as telas (Priority: P1) 🎯 MVP

**Goal**: Garantir que todo dado exibido no painel (fatos declarados `✓`, inferências `~` com contorno tracejado e motivo, ausências hachuradas `#C9C2AE` e evidências `Space Mono` com acionador de correção) siga rigorosamente os 4 padrões epistêmicos da biblioteca visual, permitindo correção manual direta com preservação do histórico original.

**Independent Test**: Abrir cartões de vaga, o detalhe de uma vaga com salário ausente/estimado e requisitos inferidos, referências salariais e candidaturas, verificando que cada um dos quatro tipos usa seu marcador próprio e que o acionador da evidência salva uma correção direta preservando a fonte original e exibindo `"corrigido por você"`.

### Backend (Correção Manual a partir da Evidência)

- [X] T009 [P] [US1] Implementar porta `OpportunityCorrectionRepository`, entidade JPA `OpportunityCorrectionEntity`, repositório Spring Data e adaptador de persistência em `backend/src/main/java/dev/curriculotron/opportunities/application/port/out/OpportunityCorrectionRepository.java` e `backend/src/main/java/dev/curriculotron/opportunities/adapter/out/persistence/`
- [X] T010 [US1] Implementar caso de uso `OpportunityCorrectionService` executando no `WriteExecutor` (validando `min <= max` e invariantes de `salary`, preservando `opportunity_source` e `opportunity_evidence` intactos, gravando linhas append-only em `opportunity_correction` + entrada `opportunity.corrected` em `activity_log` via `AppClock`, e invalidando/reavaliando via `AssessOpportunityService` fora da transação de escrita quando `title` ou `location` mudarem) em `backend/src/main/java/dev/curriculotron/opportunities/application/OpportunityCorrectionService.java`
- [X] T011 [US1] Criar DTO validado `OpportunityCorrectionRequest` (`title` `@NotBlank` `@Size(max = 240)`, `companyName` `@NotBlank` `@Size(max = 160)`, `location` `@Size(max = 160)`, enums fechados `workMode`/`contractType`/`availability`/`salary.unit`, `BigDecimal >= 0` com `min <= max`, `clearSalary`, `deadlineAt`/`publishedAt`, `reason` `@Size(max = 500)`, validação `400 invalid_correction` sem alteração) e adicionar `POST /api/opportunities/{id}/corrections` + campo `corrections` em `OpportunityDetailResponse` em `backend/src/main/java/dev/curriculotron/opportunities/adapter/in/web/`
- [X] T012 [P] [US1] Adicionar testes unitários e de integração em SQLite real validando triggers append-only da `V14`, preservação de `opportunity_source`/`opportunity_evidence`, validações Bean Validation (`400`), `404` e replay/conflito de `Idempotency-Key` (`200`/`422`) em `backend/src/test/java/dev/curriculotron/opportunities/OpportunityCorrectionTest.java`

### Frontend (4 Padrões Epistêmicos & Formulário de Correção)

- [X] T013 [P] [US1] Padronizar estimativa salarial de referência como inferência (`~` + `.dsh` `--d: #6B6B73` + critério/fonte entre parênteses) e ausência de referência como `AbsentValue` em `frontend/src/components/SalaryEstimate.tsx`
- [X] T014 [P] [US1] Aplicar marcadores epistêmicos (`✓` fato, `AbsentValue` para salário/prazo/local/empresa ausentes, e evidência em `Space Mono`) nos cartões e listagens em `frontend/src/components/OpportunityCard.tsx` e `frontend/src/pages/Applications.tsx`
- [X] T015 [US1] Criar formulário inline `OpportunityCorrectionForm` para edição direta dos campos da vaga (`title`, `companyName`, `location`, `workMode`, `contractType`, `salary`, `availability`, `deadlineAt`, `publishedAt`, `reason`) com feedback de erro/sucesso e invalidação das queries TanStack Query em `frontend/src/components/OpportunityCorrectionForm.tsx`
- [X] T016 [US1] Atualizar `OpportunityDetail` para renderizar `FactItem`, `InferenceBox`, `AbsentValue` (mantendo `"Salário a confirmar"` visível mesmo quando houver estimativa inferida), `EvidenceBlock` completo (`fonte`, `consultado`, `publicada` e histórico `"corrigido por você"`) abrindo `OpportunityCorrectionForm` ao clicar na evidência ou em `"Corrigir algum dado →"` em `frontend/src/pages/OpportunityDetail.tsx`
- [X] T017 [P] [US1] Atualizar e ampliar testes unitários dos marcadores epistêmicos, estimativa salarial inferida e fluxo do formulário de correção em `frontend/tests/unit/salary-estimate.test.tsx` e `frontend/tests/unit/components.test.tsx`

**Checkpoint**: US1 completa e testável de forma independente — todos os 4 padrões epistêmicos visíveis e correção direta a partir da evidência funcional ponta a ponta.

---

## Phase 4: User Story 2 - Exibir os seis estados padronizados de tela e carregamento (Priority: P1)

**Goal**: Garantir que `VAZIO`, `CARREGANDO`, `PARCIAL`, `FALHA`, `DESATUALIZADO` e `PENDENTE DE REVISÃO` utilizem exclusivamente `SurfaceStateBlock` em todas as telas do painel, expondo progresso por fontes, cobertura parcial (`sourcesConsulted`/`sourcesFailed`), timestamps reais em dados antigos e ações diretas de recuperação/revisão.

**Independent Test**: Simular os seis estados (lista vazia, busca em andamento, execução com falhas parciais de fonte, erro de API/rotina, dados/referências antigos e itens na fila de revisão/duplicatas) e confirmar que cada bloco exibe o rótulo oficial, textura/contorno, contexto e botão/link de ação.

- [X] T018 [P] [US2] Refatorar `QueryState` e `Empty` para delegar ao `SurfaceStateBlock` (`CARREGANDO`, `FALHA` e `VAZIO`) e atualizar o carregamento/erro inicial da sessão em `frontend/src/components/Panel.tsx` e `frontend/src/app/App.tsx`
- [X] T019 [P] [US2] Atualizar `DataAsOf` para usar `AbsentValue` quando `at` for nulo (`sem atualização bem-sucedida ainda`) e expor variante/alerta `DESATUALIZADO` (`.hatch` `#E2DCCB` + faixas `--paper`/`--card` + atalho `Atualizar →`) em `frontend/src/components/DataAsOf.tsx`
- [X] T020 [US2] Atualizar `Activities` para usar `SurfaceStateBlock` nos estados `CARREGANDO` (com contagem de fontes quando disponível), `PARCIAL` (quando `sourcesFailed.length > 0`, exibindo `X de Y fontes consultadas` e fontes com falha), `FALHA` (`--danger`) e `VAZIO` em `frontend/src/pages/Activities.tsx`
- [X] T021 [US2] Atualizar `Integrations` para exibir alertas/tarefas em estado `FALHA`, `PARCIAL` (com contagem de fontes consultadas/falhas), `VAZIO` e ausências hachuradas (`Nenhum detalhe adicional`) em `frontend/src/pages/Integrations.tsx`
- [X] T022 [P] [US2] Atualizar `SalaryReferences` para exibir `VAZIO`, `CARREGANDO`, `FALHA` e bloco `DESATUALIZADO` quando `estimate.stale === true` com atalho de atualização em `frontend/src/pages/SalaryReferences.tsx`
- [X] T023 [P] [US2] Aplicar destaque `PENDENTE DE REVISÃO` (`SurfaceStateBlock` kind `pending_review`) nos itens aguardando decisão humana em `frontend/src/pages/ReviewQueue.tsx` e `frontend/src/pages/DedupeReview.tsx`
- [X] T024 [P] [US2] Cobrir os 6 estados do `SurfaceStateBlock` e exibição de execução `PARCIAL` em testes unitários em `frontend/tests/unit/components.test.tsx` e `frontend/tests/unit/review.test.tsx`

**Checkpoint**: US2 completa — todas as telas usam a linguagem única dos 6 estados de superfície.

---

## Phase 5: User Story 3 - Padronizar identidade visual por empresa, contraste e componentes em todas as páginas (Priority: P2)

**Goal**: Aplicar a regra emendada de cores por empresa (8 marcas conferidas + grafite `#6B6B73`), contraste AA estrito em todos os textos, correspondência visual de requisitos declarados com o perfil (`você tem` / `não está no perfil`) no detalhe da vaga e padronização completa de botões, checkboxes, cabeçalhos e layout responsivo (`390px` e `1280px`) em todas as rotas, inclusive `/bots` em estado visual padronizado.

**Independent Test**: Percorrer todas as 15 rotas do painel em `390px` e `1280px` via teclado e mouse, validando: zero uso de hash cromático em empresas desconhecidas, contraste AA nas marcações de requisitos declarados vs perfil, uso uniforme de `.btn.bt.bt-pri`/`.btn.bt.bt-sec.dsh` e `.check-field`, e zero rolagem horizontal.

- [X] T025 [P] [US3] Remover fallback por hash (`COMPANY_PALETTE`) em `companyColor` mantendo exclusivamente as 8 marcas conferidas (`Itaú`, `SAP`, `PicPay`, `Sicredi`, `BV`, `CWI`, `DB`, `CI&T`) e retornando `GRAPHITE = '#6B6B73'` para qualquer empresa fora da lista ou sem nome (emenda explícita à `FR-004` da spec `003`) em `frontend/src/lib/opportunity.ts`
- [X] T026 [P] [US3] Atualizar testes unitários e fixture E2E de cores por empresa para validar `#6B6B73` fora da lista das 8 marcas conferidas em `frontend/tests/unit/company-colors.test.ts` e `frontend/tests/e2e/support/company-colors.ts`
- [X] T027 [US3] Padronizar cabeçalho (`PageHeader`), cartões, campos, checkboxes (`.check-field`) e botões (`.btn.bt.bt-pri` / `.btn.bt.bt-sec.dsh`, altura mínima `44px`) em `frontend/src/pages/Opportunities.tsx`, `frontend/src/pages/SalaryReferences.tsx` e `frontend/src/pages/ApplicationDetail.tsx`
- [X] T028 [P] [US3] Padronizar botões (`.bt` ausente), campos, `.check-field` e estados em `frontend/src/pages/Profile.tsx` (`Profile`, `Criteria`, `Companies`, `DataPrivacy`), `frontend/src/pages/ReviewQueue.tsx`, `frontend/src/pages/DedupeReview.tsx` e `frontend/src/pages/Login.tsx`
- [X] T029 [P] [US3] Padronizar a rota `/bots` e `NotFound` usando `PageHeader` + `SurfaceStateBlock` (`VAZIO`, informando estado em construção sem implementar criador conversacional) em `frontend/src/pages/Placeholder.tsx`
- [X] T030 [US3] Implementar correspondência visual dos requisitos declarados no detalhe da vaga (`você tem` em `#1F6B0E` e `não está no perfil` em `#9A2A12`) cruzando com `compatibilityCriteria`/`compatibilityEvidence`, mantendo distinção das inferências do bot em `frontend/src/pages/OpportunityDetail.tsx` e `frontend/src/pages/opportunity-detail.css`
- [X] T031 [P] [US3] Verificar acessibilidade de foco, redução de movimento (`prefers-reduced-motion`) no componente de paisagem e responsividade em `390px` na navegação e rodapé em `frontend/src/components/Landscape.tsx`, `frontend/src/app/Layout.tsx` e `frontend/src/app/layout.css`

**Checkpoint**: US3 completa — consistência visual, regra de marcas emendada, correspondência de perfil e acessibilidade AA/390px padronizadas em todo o painel.

---

## Phase 6: User Story 4 - Alinhar blocos contextuais da Revisão do Dia e do Detalhe da Vaga (Priority: P3)

**Goal**: Completar a experiência diária e do detalhe da vaga conforme os quadros de referência: pendências individuais acionáveis em `"Precisa de você"` na Home, exibição de `NÍVEL` no resumo, pontos de atenção na recomendação, `"Linha do tempo"` com registro opt-in de abertura do link (`"(isso não registra candidatura)"`) e bloco `"Preparar"` padronizado.

**Independent Test**: Abrir a Home com pendências distintas e conferir cartões individuais `PENDENTE DE REVISÃO` em `"Precisa de você"`; abrir o detalhe de uma vaga verificando `NÍVEL` no resumo, `"Linha do tempo"`, abertura de link registrando apenas evento informativo sem criar candidatura, e seção `"Preparar"` padronizada.

- [X] T032 [P] [US4] Estender `HomeController.HomeResponse` para incluir `pendingReviewItems` (`id`, `kind`: `dedupe_candidate | review_item | email_attention | missed_routine`, `kindLabel`, `title`, `summary`, `targetRoute`) a partir de `MatchCandidateRepository.open()` e rotinas perdidas sem quebrar `reviewQueueCount` em `backend/src/main/java/dev/curriculotron/shared/web/HomeController.java`
- [X] T033 [P] [US4] Implementar serviço/projeção de `OpportunityTimelineService` (combinando `opportunity_source`, `daily_selection`, `opportunity_correction` e `activity_log`) e derivação de `SeniorityAssessment` (`fact | corrected_title | inferred | absent`) para o detalhe da vaga em `backend/src/main/java/dev/curriculotron/opportunities/application/`
- [X] T034 [US4] Adicionar `POST /api/opportunities/{id}/external-visit` (`ResponseEntity<Void>`, gravando `opportunity.external_opened` em `activity_log` sem alterar `user_decision` nem criar `application`) e incluir `seniority` e `timeline` em `OpportunityDetailResponse` em `backend/src/main/java/dev/curriculotron/opportunities/adapter/in/web/OpportunityController.java` e `OpportunityDetailResponse.java`
- [X] T035 [P] [US4] Adicionar testes de API para `HomeResponse.pendingReviewItems`, `OpportunityDetailResponse.seniority`/`timeline` e `POST /api/opportunities/{id}/external-visit` (comprovando CSRF e ausência de candidatura automática) em `backend/src/test/java/dev/curriculotron/opportunities/HomeApiTest.java` e `OpportunityApiTest.java`
- [X] T036 [US4] Atualizar `NeedsYou` na página inicial para renderizar cada item de `pendingReviewItems` com `SurfaceStateBlock` (`PENDENTE DE REVISÃO`), mantendo título curto mobile (`ShortHeadline`) e limite de 3 cartões no celular com link para as demais em `frontend/src/pages/Home.tsx` e `frontend/src/pages/home.css`
- [X] T037 [US4] Atualizar `OpportunityDetail` para exibir `NÍVEL` no `Summary` (`✓` / `~` + motivo / `AbsentValue`), bloco `"Linha do tempo"` cronológico, disparo não bloqueante de `POST /api/opportunities/{id}/external-visit` ao clicar `"Abrir vaga no site ↗"`, e bloco `"Preparar"` em estado visual padronizado sem simulador completo em `frontend/src/pages/OpportunityDetail.tsx` e `frontend/src/pages/opportunity-detail.css`

**Checkpoint**: US4 completa — todas as 4 histórias de usuário implementadas e testáveis de forma independente.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Validação integrada, acessibilidade automatizada (`@axe-core/playwright`), responsividade `390px`/`1280px` e verificação final de integridade sem `design/`.

- [X] T038 [P] Criar/atualizar suíte E2E cobrindo os 6 estados de superfície, 4 padrões epistêmicos, correção direta a partir da evidência, abertura de link na linha do tempo sem candidatura, marca neutra grafite e auditoria `@axe-core/playwright` + ausência de `scrollWidth > innerWidth` em todas as rotas (`390x844` e `1280x800`) em `frontend/tests/e2e/frontend.spec.ts` e `frontend/tests/e2e/j1-review.spec.ts`
- [X] T039 Executar suíte completa do backend (`./backend/gradlew.bat -p backend spotlessCheck test integrationTest`) e validar build + checks do frontend (`npm run lint`, `npm run format:check`, `npm run test`, `npm run build`, `npx playwright test --config playwright.frontend.config.ts`) conforme `specs/005-padroes-visuais-componentes/quickstart.md` — 306 testes backend e 190 unitários frontend aprovados; 46 E2E simulados e 12 jornadas J1/J3 com aplicação real aprovados. A source set integrationTest não contém casos.
- [X] T040 Verificar que `design/` não foi incluído em `git status`, `frontend/dist` ou `backend/build/resources/main/static` e revisar todos os critérios `SC-001` a `SC-004` em `specs/005-padroes-visuais-componentes/spec.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: Sem dependências — `T001` deve rodar primeiro para alinhar `origin/main` antes das edições em arquivos modificados por `003`/`004`.
- **Phase 2 (Foundational)**: Depende da Phase 1 — **BLOQUEIA** `US1`, `US2`, `US3` e `US4`.
- **Phase 3 (`US1` - P1)**: Depende da Phase 2.
- **Phase 4 (`US2` - P1)**: Depende da Phase 2 (e coordena com `T016` ao editar `OpportunityDetail.tsx` ou `SalaryReferences.tsx` via `T022`/`T027`).
- **Phase 5 (`US3` - P2)**: Depende da Phase 2; `T030` integra sobre `OpportunityDetail.tsx` (`T016`).
- **Phase 6 (`US4` - P3)**: Depende da Phase 2 (`T004`, `T007`, `T008`); `T037` integra sobre `OpportunityDetail.tsx` (`T016`, `T030`).
- **Phase 7 (Polish)**: Depende da conclusão de `US1`–`US4`.

### User Story Dependencies

- **US1 (P1)**: Independente após Phase 2 (entrega MVP de linguagem epistêmica + correção direta de evidência).
- **US2 (P1)**: Independente após Phase 2 (entrega os 6 estados padronizados em todas as telas).
- **US3 (P2)**: Independente após Phase 2 para marca/botões/formulários/Bots; coordena apenas edição sequencial em `OpportunityDetail.tsx` (`T030`) e `SalaryReferences.tsx` (`T027`).
- **US4 (P3)**: Independente após Phase 2 no backend (`T032`–`T035`) e `Home.tsx` (`T036`); coordena edição sequencial final em `OpportunityDetail.tsx` (`T037`).

### Parallel Opportunities

- **Phase 2**: `T005` (`Epistemic.tsx`), `T006` (`SurfaceState.tsx`) e `T007` (domínio Java) podem rodar em paralelo após `T004`/`T008`.
- **Phase 3 (`US1`)**: `T009` + `T013` + `T014` em paralelo; depois `T010`–`T012` (backend) em paralelo com `T015`–`T017` (frontend).
- **Phase 4 (`US2`)**: `T018` (`Panel.tsx`), `T019` (`DataAsOf.tsx`), `T022` (`SalaryReferences.tsx`) e `T023` (`ReviewQueue`/`DedupeReview`) em paralelo antes de `T020`–`T021` (`Activities`/`Integrations`).
- **Phase 5 (`US3`)**: `T025`+`T026` (cores), `T028` (`Profile`/`Login`), `T029` (`Placeholder`), e `T031` (`Landscape`/`Layout`) em paralelo.
- **Phase 6 (`US4`)**: `T032` (`HomeController`) e `T033` (`Timeline`/`Seniority`) em paralelo com `T036` após DTOs fechados.

---

## Parallel Example: User Story 1

```text
# Backend e Frontend da US1 em paralelo após Phase 2:
Backend:  T009 (OpportunityCorrectionRepository/Entity) -> T010 (Service) -> T011 (Controller/Request) + T012 (SQLite Tests)
Frontend: T013 (SalaryEstimate) + T014 (OpportunityCard/Applications) + T015 (OpportunityCorrectionForm) -> T016 (OpportunityDetail) + T017 (Vitest)
```

---

## Implementation Strategy

### MVP First (`Phase 1 + Phase 2 + US1` ou `P1` Completo `US1 + US2`)

1. Concluir Phase 1 (`T001`–`T003`) e Phase 2 (`T004`–`T008`) — base Flyway `V14`, contratos TS e componentes `Epistemic` + `SurfaceStateBlock`.
2. Entregar **US1** (`T009`–`T017`): todos os 4 marcadores epistêmicos + correção direta de evidência testada com SQLite real.
3. Entregar **US2** (`T018`–`T024`): os 6 estados padronizados em todas as telas assíncronas e vazias.
4. Validar incremento P1 antes de seguir para **US3** (`T025`–`T031`), **US4** (`T032`–`T037`) e **Polish** (`T038`–`T040`).

### Notes

- Todas as alterações em `OpportunityDetail.tsx` (`T016` -> `T030` -> `T037`) devem ser executadas em sequência para evitar conflitos de edição no mesmo arquivo.
- Nunca remover proteção de CSRF nem permitir mutações via `GET` (a abertura externa da vaga usa explicitamente `POST /api/opportunities/{id}/external-visit`).
- `design/` nunca deve aparecer em `git add` nem em artefatos compilados.
