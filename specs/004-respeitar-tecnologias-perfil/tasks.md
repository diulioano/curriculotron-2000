# Tasks: Respeitar tecnologias e cargos do perfil nas vagas

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contrato](contracts/compatibility-api.md) e [quickstart.md](quickstart.md).

**Prerequisites**: Planejamento aprovado após correções de mínimo salarial e exceção júnior. Esta lista não executa implementação nem troca de branch.

**Tests**: Incluir os cenários de aceitação e regressão previstos pela spec/plano. Cada tarefa de código inclui seus testes de comportamento, executados antes da revisão e do commit; criar o teste que reproduz o problema antes da correção dentro da mesma tarefa, sem deixar um commit deliberadamente quebrado.

**Organization**: Fases por jornada, com fundação compartilhada. Todos os caminhos são relativos à raiz do repositório; novos nomes de arquivos abaixo são alvos propostos.

## Format: `[ID] [P?] [Story] Description`

[P] identifica apenas trabalho em arquivos distintos com pré-requisitos satisfeitos, não autoriza executar antes da fundação. IDs locais a esta feature; commits devem citar escopo e ID, por exemplo `feat(opportunities): filter incompatible jobs [T014]`.

## Path Conventions

Backend em backend/src/main/java/dev/curriculotron; testes em backend/src/test/java/dev/curriculotron e backend/src/test/resources. Frontend em frontend/src e frontend/tests. Sem atualização de stack ou dependências novas.

## Regras de execução de cada tarefa

Ler constituição, boas práticas e contratos afetados. Implementar e executar verificações proporcionais; rodar os checks aplicáveis do quickstart antes de concluir cada tarefa. Revisão por agente focado é obrigatória, com localização/evidência/impacto/severidade; corrigir impeditivos e submeter correções à nova revisão. Só marcar concluída e criar um commit delimitado após checks e aprovação. Registrar decisões recusadas aceitas pelo revisor no corpo do commit. Não alterar dados pessoais em testes.

A única mudança nesta fase é este documento. Não marcar tarefas como feitas ao gerar a lista.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Preparar entrega isolada e dados de aceitação, sem bootstrap de projeto.

- [X] T001 Preparar a branch de implementação feature/US-004-respeitar-tecnologias-perfil a partir da main atualizada, preservando documentos não rastreados e alterações alheias; registrar base/branch em specs/004-respeitar-tecnologias-perfil/plan.md e seguir o ciclo de revisão/commit por tarefa, sem reset destrutivo ou push forçado.
- [X] T002 Definir fixtures com pelo menos vinte anúncios completos e resultados esperados por eixo em backend/src/test/resources/compatibility/postings.json, acrescentando amostras parciais/legadas, Java/Python, cargos das capturas, C/C++/C#, versões, negação, incidental, diferencial, contradição e exceção júnior; criar loader em backend/src/test/java/dev/curriculotron/opportunities/CompatibilityFixtures.java e validar integridade dos dados sem coleta externa.

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Disponibilizar critérios reais, evidência recuperável e avaliação versionada para todas as jornadas.

- [X] T003 Criar migração aditiva backend/src/main/resources/db/migration/V13__opportunity_compatibility.sql (conferir próximo número livre antes de criar), tabelas opportunity_evidence/opportunity_compatibility/compatibility_refresh e opportunity.evidence_revision; preservar tabelas e dados anteriores e raw_excerpt. Aplicar literalmente as restrições “opportunity_id PK/FK”, “source_id FK nullable”, “UNIQUE source_id quando presente”, “id=1”, “run_id nullable”, “started_at/finished_at, error_code nullable” e índices do modelo; backfill legado como unknown/legacy_excerpt, manual literal como manual/unknown sem inventar URL. Verificar banco novo e upgrade legado com SQLite temporário em backend/src/test/java/dev/curriculotron/opportunities/CompatibilityMigrationTest.java.
- [X] T004 Implementar records/enums de domínio e portas em backend/src/main/java/dev/curriculotron/opportunities/domain/CompatibilityAssessment.java, VacancyEvidence.java, CompatibilityRefresh.java e application/port/out/CompatibilityRepository.java, EvidenceRepository.java, CompatibilityRefreshRepository.java; aplicar “role_status: matched|mismatched|unknown”, “technology_status: matched|mismatched|unknown|unrestricted”, “description_completeness: complete|partial|unknown”, “Tipos de menção: required|optional|usage|negated|incidental|uncertain”, “state: pending|running|ready|failed”, “content_revision: inteiro positivo”, “até 100.000 caracteres” e “JSON validado e tipado na aplicação”; coleções defensivas, critérios/revisões/tempos explícitos, sem dependências Spring no domínio, com testes de invariantes.
- [X] T005 Implementar adapters/entidades/conversores em backend/src/main/java/dev/curriculotron/opportunities/adapter/out/persistence/CompatibilityPersistenceAdapter.java, EvidencePersistenceAdapter.java e CompatibilityRefreshPersistenceAdapter.java e arquivos de entidades/repositories correspondentes; usar JPA derivado/JPQL, sem nativeQuery, persistência condicional por critérios/evaluator/evidenceRevision e lotes de leitura por id; testar unicidade, roundtrip JSON tipado, backfill e commit obsoleto em backend/src/test/java/dev/curriculotron/opportunities/CompatibilityRepositoryTest.java; depende de T003–T004.
- [X] T006 Mapear todos os campos existentes e a restrição “version (inteiro positivo)” em backend/src/main/java/dev/curriculotron/sources/adapter/out/persistence/SearchCriteriaEntity.java, SearchCriteriaPersistenceAdapter.java e sources/SearchCriteriaRepository.java; criar caso de uso CriteriaService.java para leitura/salvamento via WriteExecutor, comparar versão, incrementar uma vez só em mudança efetiva e marcar refresh pending atomicamente; no-op mantém versão/estado e PUT novo invalida execução anterior sem alterar rotinas. Verificar rollback/conflito/no-op em backend/src/test/java/dev/curriculotron/sources/CriteriaServiceTest.java; depende de T005.
- [X] T007 Expor GET/PUT /api/criteria em backend/src/main/java/dev/curriculotron/sources/CriteriaController.java com DTOs concretos/Validation e envelope de erro existente: listas “até 100 itens de até 200 caracteres”, trim/deduplicação/remoção de vazios, “Lista vazia permitida”, “decimais BRL estritamente positivos, no máximo duas casas”, “zero e negativos são inválidos”, “Hora HH:mm válida, deadlineHighlightDays 1–365, urgentHours 1–8760”; preservar todos os campos do formulário, 400 invalid_criteria e 409 criteria_version_conflict, CSRF/sessão e sem Idempotency-Key. Testar HTTP, limites e persistência em backend/src/test/java/dev/curriculotron/sources/CriteriaControllerTest.java; depende de T006.

**Checkpoint**: Fundação compilável/testada, critérios persistentes reais e nenhuma recomendação legada falsamente declarada atual. Não depende de serviços ainda inexistentes das próximas jornadas.

## Phase 3: User Story 1 — Receber vagas relacionadas ao perfil configurado (Priority: P1) — MVP

**Goal**: Excluir incompatibilidades das duas listas usando perfil salvo e a mesma avaliação na busca manual/rotina.

**Independent Test**: Banco descartável com critérios backend/fullstack Java/Spring e fontes simuladas contendo anúncios aderentes e funções incompatíveis das capturas; só candidatas válidas concorrem à principal, excedentes compatíveis ficam na secundária e o teto é dez.

- [X] T008 [US1] Implementar correspondência configurável de cargo em backend/src/main/java/dev/curriculotron/opportunities/domain/RoleMatcher.java e ajustar JobFactExtractor.java: famílias/especializações/aliases de research R3, responsabilidades como evidência, ausência como unknown, funções incompatíveis como mismatched; remover Java obrigatório e não exigir igualdade artificial de título. Senioridade permanece fato separado, preservando acceptJuniorIfJustified/RB-004; testar cargos das imagens e SAP em backend/src/test/java/dev/curriculotron/opportunities/RoleMatcherTest.java; depende da fase 2.
- [X] T009 [US1] Implementar TechnologyMatcher.java e CompatibilityEvaluator.java em backend/src/main/java/dev/curriculotron/opportunities/domain/ usando fixtures T002: nomes inteiros com símbolos, versões/aliases explícitos, contexto required/optional/usage, exclusão negated/incidental, conflitos uncertain/unknown, ausência somente mismatched com descrição complete, tecnologias vazias unrestricted e OR entre alternativas; integrar cargo/tecnologia sem somar score para superar mismatch e testar em TechnologyMatcherTest.java e CompatibilityEvaluatorTest.java; depende de T008.
- [X] T010 [US1] Integrar o avaliador puro ao ranker e criar caso de uso compartilhado em backend/src/main/java/dev/curriculotron/opportunities/domain/RuleBasedRanker.java e application/AssessOpportunityService.java; manter ObjectiveFilter/salário/modalidade/júnior e score existentes, versões/snapshot/evidências, calcular fora da escrita e publicar apenas se criteriaVersion/evaluatorVersion/evidenceRevision continuam atuais; testar paridade, tecnologias vazias e não fabricação de cargo em backend/src/test/java/dev/curriculotron/opportunities/AssessOpportunityServiceTest.java; depende de T009.
- [X] T011 [P] [US1] Acrescentar completude/contexto a backend/src/main/java/dev/curriculotron/sources/JobSource.java e atualizar AtsBoardSource.java, GupySource.java, CwiCareersSource.java, OracleRecruitingSource.java e ManualSource.java: Lever junta descriptionPlain/listas do mesmo payload, Greenhouse preserva content, resumos são partial, formato/entrada manual sem comprovação são unknown, limite “até 100.000 caracteres” com corte marcado partial. Sem requisições novas; testar payloads simulados em backend/src/test/java/dev/curriculotron/sources/AtsBoardSourceTest.java e CareersSourcesTest.java; pode ocorrer junto de T008, após fundação.
- [X] T012 [US1] Persistir/renovar evidência e avaliação ao inserir ou reencontrar vagas em backend/src/main/java/dev/curriculotron/sources/SearchService.java, opportunities/application/DedupeService.java, OpportunityService.java e adapters de ingestão existentes; remover substituição pelo primeiro cargo configurado, atualizar evidência também em deduplicados, preservar identidade manual sem URL e publicar com checagem das versões atuais. Testar busca manual/rotina equivalentes, refetch, dedupe e manual em backend/src/test/java/dev/curriculotron/sources/SearchServiceTest.java e opportunities/DedupeServiceTest.java; depende de T010–T011.
- [X] T013 [US1] Filtrar elegibilidade e exibição por avaliação atual em backend/src/main/java/dev/curriculotron/opportunities/adapter/out/persistence/SelectionJpaRepository.java, application/OpportunityService.java e DailySelectionService.java: principal matched + matched/unrestricted e demais filtros, secundária sem mismatched e com unknown rotulado; excluir encerradas/descartadas/aplicadas/fundidas, manter selected(date) bruto e ranking histórico sem apagar registros. Testar quinze compatíveis, zero compatíveis e prioridade sem superar mismatch em backend/src/test/java/dev/curriculotron/opportunities/DailySelectionServiceTest.java e OpportunityApiTest.java; depende de T012.
- [X] T014 [US1] Acrescentar DTO concreto de envelope de oportunidades com items/dataAsOf/assessment e compatibilidade resumida em backend/src/main/java/dev/curriculotron/opportunities/adapter/in/web/OpportunityController.java, OpportunitySummary.java e OpportunitySummaries.java; atualizar shared/web/HomeController.java para count visível/consumedCount histórico e assessment, sem mudar ApiListResponse de outras features ou timestamps em GET. Testar contratos e ausência de efeitos de GET em backend/src/test/java/dev/curriculotron/opportunities/OpportunityApiTest.java e HomeApiTest.java; depende de T013.
- [X] T015 [US1] Consumir/resumir compatibilidade e consumo correto em frontend/src/api/compatibility.ts, api/panel.ts, components/OpportunityCard.tsx, pages/Opportunities.tsx e Home.tsx: parsing a partir de unknown, enums validados, pendência explícita e aviso para stale, sem classificador React; testar interface/estado vazio/contagem em frontend/tests/unit/compatibility-lists.test.tsx e jornada nova em frontend/tests/e2e/profile-compatibility.spec.ts com seed isolado, fontes simuladas e 360/1280 px; depende de T014.

**Checkpoint**: MVP para novas ingestões com critérios já persistidos. Vagas legadas sem avaliação ficam ocultas com aviso, sem perda de registros. Recuperação integral do acervo é US3; não declarar a feature inteira concluída no MVP.

## Phase 4: User Story 2 — Entender coincidências e informações insuficientes (Priority: P2)

**Goal**: Permitir explicar cada correspondência, lacuna, diferencial e fonte sem inventar experiência.

**Independent Test**: Com fixtures conhecidas e avaliação armazenada, abrir card/detalhe e verificar termo, trecho, contexto, versão e motivo de pendência em cada cenário da US2.

- [X] T016 [P] [US2] Completar projeção do detalhe em backend/src/main/java/dev/curriculotron/opportunities/adapter/in/web/OpportunityDetailResponse.java e OpportunityController.java com compatibilityEvidence, compatibilityCriteria e missing coerentes, “field: title|description”, “section: string|null”, “sourceUrl: string|null”, “fetchedAt: string|null”, “observedVersion: string|null”, “configuredVersion: string|null” e menções tipadas; preservar facts/inferred/salários/fontes e snapshot antigo rotulado current=false. Testar resposta, ausência de avaliação e inexistência de dados de currículo em backend/src/test/java/dev/curriculotron/opportunities/CompatibilityDetailApiTest.java; depende de US1.
- [X] T017 [P] [US2] Estender parser/DTOs de detalhe em frontend/src/api/compatibility-detail.ts e frontend/tests/unit/compatibility-detail-parser.test.ts para evidências e critérios conforme contrato, rejeitando enum/tipo inválido sem mascarar erro como lista vazia; sem assertions que substituam validação e sem HTML confiável. Pode ocorrer junto de T016 com fixtures do contrato; depende de US1.
- [X] T018 [US2] Exibir motivo por eixo, critério usado, trecho, fonte/data, diferencial versus obrigação, versões e descrição insuficiente em frontend/src/pages/OpportunityDetail.tsx e components/CompatibilityEvidence.tsx; renderizar conteúdo externo como texto, com acessibilidade por roles/labels e snapshot desatualizado explícito. Testar interações e conteúdo negado/incidental em frontend/tests/unit/compatibility-detail.test.tsx; depende de T016–T017.
- [X] T019 [US2] Verificar integralmente SC-001–003 em backend/src/test/java/dev/curriculotron/opportunities/CompatibilityAcceptanceTest.java: ao menos vinte anúncios completos mais casos parciais, 100% de aderentes reconhecidos nos eixos, zero falsos matches nos casos negativos e todas as razões rastreáveis; acrescentar regressões encontradas sem reescrever o algoritmo no teste; depende de T018 e usa fixtures T002.
- [X] T020 [US2] Ampliar frontend/tests/e2e/profile-compatibility.spec.ts com US2.1–5, detalhe de anúncio parcial, obrigatoriedade/diferencial, versões e texto externo exibido sem execução, garantindo rótulos acessíveis em 360/1280 px e nenhum fato de estudo promovido a experiência; depende de T019.

**Checkpoint**: Explicações da US2 verificadas sem depender do comando de reavaliação da US3.

## Phase 5: User Story 3 — Aplicar alterações do perfil às vagas existentes (Priority: P2)

**Goal**: Atualizar acervo sem rede, preservar decisões/seleções e sobreviver a concorrência, falha e reinício.

**Independent Test**: Mudar perfil Java para Python em SQLite descartável contendo vagas, dez seleções e decisões; reavaliar, conferir listas atuais, histórico invariável, falha visível e retry seguro.

- [X] T021 [US3] Implementar reserva/status/finalização em backend/src/main/java/dev/curriculotron/opportunities/application/CompatibilityRefreshService.java e adapters correspondentes: singleton por instalação, runId/version, “state: pending|running|ready|failed”, contagens inteiras >=0, stale derivado também de avaliações ausentes, ready sem pendências como no-op; 409 ao concorrente, sem estado no controller ou fila nova. Testar transições e invalidar dono antigo por PUT em backend/src/test/java/dev/curriculotron/opportunities/CompatibilityRefreshStateTest.java; depende de US1, sem dependência de implementação da US2.
- [X] T022 [US3] Implementar reavaliação síncrona em backend/src/main/java/dev/curriculotron/opportunities/application/ReassessOpportunitiesService.java: ler páginas estáveis de vagas não fundidas, calcular pelo serviço T010 fora da escrita, “commit até 100 resultados por transação”, verificar runId/criteriaVersion/evaluatorVersion/evidenceRevision, reler mudanças e “limitar a três passagens de reconciliação”; ready só com zero pendentes, failed/evidence_changed quando não estabilizar e sem coleta/seleção. Testar parcial/legado, rollback de lote e retry em backend/src/test/java/dev/curriculotron/opportunities/ReassessOpportunitiesServiceTest.java; depende de T021.
- [X] T023 [P] [US3] Implementar recuperação de execução interrompida em backend/src/main/java/dev/curriculotron/opportunities/application/CompatibilityRefreshRecovery.java: inicialização “running → failed/interrupted”, sem retomar trabalho/rede; erro em transação separada somente se runId ainda é dono, falha de persistência com log seguro e interrupção preservada. Verificar reinício SQLite e falha ao gravar erro em backend/src/test/java/dev/curriculotron/opportunities/CompatibilityRefreshRecoveryTest.java; pode ocorrer junto de T022, após T021.
- [X] T024 [US3] Expor POST /api/opportunities/reassess e GET /api/opportunities/compatibility-status em backend/src/main/java/dev/curriculotron/opportunities/adapter/in/web/CompatibilityRefreshController.java com corpo criteriaVersion positivo, DTOs concretos, sessão/CSRF e erros 400 invalid_criteria_version, 409 criteria_version_conflict/reassessment_running/evidence_changed e 500 reassessment_failed; sem Idempotency-Key/202 ou coleta em GET. Testar status HTTP, literal antes de id dinâmico e timeout sem supor cancelamento em backend/src/test/java/dev/curriculotron/opportunities/CompatibilityRefreshApiTest.java; depende de T022–T023.
- [X] T025 [US3] Fechar invalidação/reavaliação ao restaurar, fundir ou mudar dados relevantes em backend/src/main/java/dev/curriculotron/opportunities/application/OpportunityService.java, DedupeService.java, adapter/out/persistence/OpportunityEntity.java e adapters de merge/evidências; incrementar revisão global na mesma escrita e jamais apagar decisão/candidatura/rank. Testar refetch/restore/merge e fontes contraditórias em backend/src/test/java/dev/curriculotron/opportunities/CompatibilityEvidenceLifecycleTest.java; depende de T024.
- [X] T026 [US3] Provar corridas e histórico com barreiras em backend/src/test/java/dev/curriculotron/opportunities/CompatibilityConcurrencyTest.java e CompatibilityHistoryTest.java: PUT durante busca/reavaliação, evidência alterada no cálculo, POST simultâneos, descarte/merge durante commit e finalização antiga; nenhuma avaliação obsoleta atual, dez consumidas continuam dez mesmo com cinco visíveis e novo comando não seleciona/reposiciona vagas. Corrigir serviços/repositories afetados dentro da tarefa e rever correções; depende de T025.
- [X] T027 [US3] Integrar GET/PUT reais e comando/status ao frontend/src/pages/Profile.tsx e frontend/src/api/compatibility.ts, removendo dependência de endpoints mockados apenas para critérios: save com versão, formulário preservado no conflito, indicação pending e botão “Atualizar avaliação”; polling só consulta, timeout consulta status antes de retry, falha e interrupção recuperáveis. Testar fluxo em frontend/tests/unit/compatibility-profile.test.tsx; depende de T024 e contrato consolidado da US2.
- [X] T028 [US3] Coordenar cache em frontend/src/api/useCompatibilityRefresh.ts e páginas Profile.tsx/Opportunities.tsx/Home.tsx/OpportunityDetail.tsx: cancelar consultas antigas, invalidar criteria/compatibility-status/opportunities/opportunity/home após save/refresh, não reapresentar resposta antiga como atual, avisar stale e permitir resultados atuais parciais; testar corrida de respostas e falhas em frontend/tests/unit/compatibility-cache.test.tsx; depende de T027.
- [X] T029 [US3] Completar frontend/tests/e2e/profile-compatibility.spec.ts e support/seed.ts com Java→Python, legado/parcial, falha/retry, dez consumidas/cinco visíveis, persistência dos critérios e candidaturas/descartes intactos após restart isolado; provar paridade manual/rotina no backend e nenhum GET/refresh coletando. Executar nos dois viewports e registrar resultados reais em specs/004-respeitar-tecnologias-perfil/quickstart.md; depende de T026–T028.

**Checkpoint**: Acervo reavaliável, critérios aplicados às duas listas, histórico intacto e retomada explícita verificável.

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T030 Atualizar specs/001-assistente-busca-emprego/spec.md e contracts/panel-api.md para FR-005/RB-001, critérios realmente implementados, envelopes dataAsOf/assessment, compatibilidade/evidências e consumedCount; corrigir só a premissa constitucional afetada, sem reescrever contratos de salários/candidaturas; alinhar specs/004-respeitar-tecnologias-perfil/quickstart.md e README.md com comandos/limitações efetivamente entregues; depende das três jornadas.
- [X] T031 Criar/usar benchmark controlado em backend/src/test/java/dev/curriculotron/opportunities/CompatibilityPerformanceTest.java com 1.000 anúncios e fontes simuladas, medir leitura <=2 s/reavaliação <=10 s como metas do plano e registrar hardware, volume, texto e resultado em specs/004-respeitar-tecnologias-perfil/quickstart.md; se falhar, corrigir consultas/lotes e revalidar sem criar índices ou abstrações sem evidência; depende de T030, pois ambos atualizam quickstart.md.
- [ ] T032 Executar checks locais completos proporcionais do guia em backend/build.gradle.kts, frontend/package.json e frontend/playwright.config.ts: Gradle test/spotlessCheck, npm test/lint/build/format:check e E2E da feature; integrationTest só comprova casos existentes. Inspecionar configuração e gatilhos de .github/workflows/backend-ci.yml e frontend-ci.yml e configuração de revisão por agente no Git; registrar resultados locais e lacunas em specs/004-respeitar-tecnologias-perfil/quickstart.md, deixando a comprovação de execução remota para T034 após push/PR, sem declarar esteira ativa; depende de T031.
- [ ] T033 Realizar revisão final por agente de todos os contratos/arquitetura/Java/TypeScript/JPA/SQLite/segurança e rastreabilidade FR-001–010/SC-001–005; corrigir impeditivos e obter nova revisão, consolidar resultados/limitações em specs/004-respeitar-tecnologias-perfil/quickstart.md sem confundir planejamento com testes executados; depende de T032.
- [ ] T034 Finalizar entrega Git da US na branch correspondente com commits delimitados por tarefa revisada, push e PR para main; descrever resumo, link specs/004-respeitar-tecnologias-perfil/spec.md, tarefas, como testar e QA-declined quando existir; vincular PR na ferramenta da sessão e só então comprovar execução remota dos workflows e revisão por agente no Git para o head entregue. Corrigir falhas na mesma branch, publicar correções e verificar novamente; registrar URL/head/checks em specs/004-respeitar-tecnologias-perfil/quickstart.md, distinguindo relatório local e estado remoto. Não marcar a entrega/US concluída nem fazer merge com checks/revisão obrigatórios pendentes, falhos ou ausentes; depende de T033.

## Dependencies & Execution Order

### Phase Dependencies

Setup T001 → T002 → fundação T003 → T004 → T005 → T006 → T007. Não criar controllers que dependam de serviços das fases futuras: fundação expõe metadados pela porta/estado básico, não pelo executor de reavaliação ainda inexistente.

Depois da fundação, ordem recomendada US1 → US2 → US3 → T030 → T031 → T032 (checks locais/configuração) → T033 (revisão local final) → T034 (push/PR e validação remota). US3 backend pode iniciar após US1, sem esperar UI explicativa da US2, mas a integração frontend usa o contrato consolidado. Nenhuma jornada exige executar outra para validar seus cenários após os pré-requisitos de código.

### User Story Dependencies

| Jornada | Pré-requisito | Resultado independente |
| --- | --- | --- |
| US1 (8 tarefas: T008–T015) | Setup e fundação | Novas ingestões filtradas pelo perfil |
| US2 (5 tarefas: T016–T020) | US1 para avaliações/DTOs básicos | Detalhes explicáveis com fixtures armazenadas |
| US3 (9 tarefas: T021–T029) | US1; T027 integra contrato da US2 | Critérios alterados e acervo reavaliado sem perda de histórico |

US2/US3 ampliam a mesma avaliação, portanto não são implementações isoladas de raiz; os testes independentes usam fixtures controladas e não exigem executar outra jornada na interface.

### Within Each User Story

Escrever o cenário de regressão antes da correção na mesma tarefa; domínio/modelos → serviços → endpoints → UI/integração. Cumprir testes/revisão/commit por tarefa antes de marcar a checkbox. Não usar checkpoints para encerrar a feature completa com US3 ou revisão pendente.

### Parallel Opportunities

T011 pode ocorrer junto de T008 após fundação; T016 e T017 podem ocorrer juntas após US1; T022 e T023 após T021. Esses pares têm arquivos distintos; T030/T031 e demais tarefas com compartilhamento de arquivos executam em sequência. O documento identifica oportunidades, sem iniciar agentes de implementação.

## Parallel Example: User Story 1

Após T007, executar T008 (RoleMatcher/JobFactExtractor/testes) e T011 (adapters de fonte e testes) em arquivos separados; T012 espera T010 e T011. Não paralelizar T008 e T009 nem alterar JobSource no ramo do matcher.

## Parallel Example: User Story 2

Após T015, executar T016 (DTOs/API backend) e T017 (parser/fixtures frontend) conforme contrato fechado; T018 integra as duas saídas. Não iniciar T018 antes de ambas passarem seus checks.

## Parallel Example: User Story 3

Após T021, executar T022 (processamento síncrono) e T023 (recuperação) separadamente; ambos só usam contratos da fundação. T024 espera ambos. A execução sequencial continua válida com uma pessoa.

## Implementation Strategy

### MVP First (User Story 1 Only)

Concluir T001–T015 e validar novas ingestões num banco descartável. Esse incremento resolve ruído de novas recomendações e protege a contagem, mas o acervo legado continua pendente; demonstrar essa limitação, sem declarar a correção inteira entregue.

### Incremental Delivery

US1 produz classificação segura; US2 torna o resultado auditável; US3 aplica as mudanças ao acervo e cobre falhas/concorrência. Finalizar com benchmark, checks, revisão e entrega Git. Não executar migração/reclassificação no banco pessoal durante a fase de testes.

## Rastreabilidade

| Requisitos | Tarefas principais | Verificação |
| --- | --- | --- |
| FR-001, FR-002 | T006–T012, T021–T029 | Critérios persistentes, cargos/tecnologias e paridade manual/rotina |
| FR-003, FR-004 | T009–T015, T022 | Estados por eixo, exclusão e pendências |
| FR-005 | T008–T011, T019–T020 | Nomes, versões, aliases, contexto e fontes |
| FR-006 | T014–T020 | Snapshot/trechos/diferencial/dados ausentes |
| FR-007 | T010, T013, T019 | Prioridade e score não superam mismatch |
| FR-008 | T006–T007, T021–T024, T027–T029 | Save/atualização/falha/stale sem efeitos de GET |
| FR-009, FR-010 | T013–T014, T025–T026, T029 | Decisões, merge/dedupe e teto histórico |
| SC-001, SC-002, SC-003 | T002, T015–T020 | Matriz >=20 completos e evidências na UI |
| SC-004, SC-005 | T026–T029 | Java→Python, histórico e concorrência |

## Notes

34 tarefas: 2 setup, 5 fundação, 8 US1, 5 US2, 9 US3, 5 finais. Todas começam pendentes. Não há extensions.yml no baseline, portanto nenhum hook before_tasks/after_tasks registrado. Validação estrutural: 34/34 IDs sequenciais, checkboxes pendentes, labels e caminhos presentes; links locais e espaços finais conferidos sem falhas. Revisão documental por agente: APPROVED após aceitar e corrigir a ordem da comprovação remota (T034 após push/PR) e remover paralelismo entre T030/T031. Nenhum código foi implementado, teste funcional executado ou check remoto comprovado nesta fase.
