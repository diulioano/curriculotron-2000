# Tasks: Cores dos cards por empresa

**Input**: documentos em `/specs/003-company-card-colors/`.
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contrato](contracts/company-card-colors.md) e [quickstart.md](quickstart.md).
**Tests**: incluídos conforme cenários da especificação, plano e constituição. Cada tarefa de implementação inclui seus testes de comportamento; escrevê-los antes da alteração e confirmar a falha pertinente, depois obter sucesso antes do commit. Não criar commits deliberadamente quebrados.
**Organization**: US1 (P1), US3 (P1), US2 (P2); os identificadores preservam as histórias da spec.

## Format: `[ID] [P?] [Story] Description`

Todos os caminhos são relativos à raiz. `[P]` indica tarefas que podem ocorrer simultaneamente nas condições da seção de dependências, em arquivos distintos. Checkboxes somente devem ser marcadas após execução, checks e revisão.

## Regras de execução para todas as tarefas

Ler `.specify/memory/constitution.md`, `docs/boas-praticas.md` e os contratos relevantes. Para cada tarefa: implementar e validar o que se aplica, submeter diff e arquivos novos à revisão por agente com contexto focado; exigir localização, evidência, impacto e severidade, corrigir impeditivos e obter nova revisão das correções. Registrar resultado por tarefa em `specs/003-company-card-colors/validation.md`. CRITICAL sempre é corrigido; divergência após duas rodadas deve ser apresentada ao usuário. Um commit delimitado por tarefa, com ID, sem misturar outras demandas; achados recusados aceitos pelo revisor entram como `QA-declined`.

Checks frontend após alterações de código: `npm test`, `npm run lint`, `npm run build` e `npm run format:check`, no diretório `frontend`; executar `npm run test:frontend` quando a jornada ou CSS for afetado. Não exigir backend sem mudança pertinente. Edição somente documental exige checagem documental, não teste novo. Paralelismo de trabalho não dispensa revisão/commit individual; serializar o registro em validation.md e commits.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: preparar a entrega sem reinstanciar projeto ou migrar ferramentas.

- [X] T001 Preparar a branch `feature/US-003-company-card-colors` a partir da main atualizada, preservando os documentos não rastreados de `specs/003-company-card-colors/`; conferir remotes e alterações existentes antes de trocar branch e registrar a base real em `specs/003-company-card-colors/validation.md`, sem descartar trabalho nem fazer force push.
- [X] T002 Conferir `frontend/package.json`, `frontend/package-lock.json` e `frontend/playwright.frontend.config.ts`, instalar dependências pelo lockfile e Chromium se necessário, executar baseline dos scripts de quickstart e registrar resultados/falhas preexistentes em `specs/003-company-card-colors/validation.md`; não trocar npm, versões, CSS ou stack.

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: garantir evidência de teste utilizável por todas as histórias.

- [X] T003 Preparar fixtures reutilizáveis em `frontend/tests/e2e/support/company-colors.ts` para seis prioritárias, Itaú/Sicredi, duas vagas da mesma empresa, desconhecida, vazia, espaços e prefixo semelhante; manter o contrato “`Opportunity.companyName: string`” e “null não faz parte do tipo atual e não será legitimado por cast”; expor respostas para Hoje/listas e estado de descarte/restauração/correção sem backend real, usando o padrão de simulação de `frontend/tests/e2e/frontend.spec.ts`.

**Checkpoint**: ambiente e fixtures prontos. Sem endpoint, coluna, migração ou biblioteca nova.

## Phase 3: User Story 1 - Reconhecer a empresa pela cor (Priority: P1) — MVP funcional

**Goal**: cobrir empresas prioritárias e demais empresas com identidade de cor no card.
**Independent Test**: consultar Hoje e listas com PicPay, SAP, BV, CWI, DB, CI&T e duas vagas da mesma empresa; comparar cores computadas nos destaques, incluindo desconhecida.

- [X] T004 [US1] Ampliar `frontend/src/lib/opportunity.ts` e criar testes comportamentais em `frontend/tests/unit/company-colors.test.ts`: manter `companyColor(name: string): string`, normalizar por “`companyName.trim().toLowerCase()`”, respeitar “Não remover acentos, pontuação, espaços internos nem sufixos automaticamente”; trocar prefixos por chaves exatas e alias explícito itau/itaú; preservar Itaú #EC7000, SAP #0070F2, PicPay #21C25E e Sicredi #2F7D12; adicionar BV #005CA9 (aproximado), CWI #FF8A00, DB #F70773 e CI&T #FA5A50; aplicar “string hexadecimal no formato `#RRGGBB`”, grafite #6B6B73 à chave vazia e hash unsigned de 32 bits sobre unidades UTF-16, inicial zero, multiplicador 31, para paleta ordenada #475569/#7C3AED/#0F766E/#B45309/#BE185D/#0369A1; testar saída válida, correspondência exata, ausência e fallback sem duplicar o algoritmo no teste; colisões são permitidas.
- [X] T005 [US1] Verificar e completar o consumo central em `frontend/src/components/OpportunityCard.tsx` e `frontend/src/app/panel.css`, mantendo `--c` nos traços, carimbo e hachuras com fundo claro/texto escuro; ampliar `frontend/tests/unit/components.test.tsx` para identidade por empresa e duas vagas iguais; não criar cache, Effect, busca externa ou resolvedor duplicado em Home/listas.
- [ ] T006 [US1] Adicionar cenários de marcas e fallback em `frontend/tests/e2e/frontend.spec.ts` com fixtures de T003: comparar `--c` e estilos computados dos contornos/pseudo-elementos/carimbo entre Hoje e selection/secondary/discarded; verificar regressões dos consumidores existentes de companyColor, inclusive detalhe da vaga, sem reformular suas telas; confirmar ausência de requisições extras de marca e preservar ações/conteúdo (FR-001/003/004/007/009; SC-001).

**Checkpoint**: US1 demonstrável e revisada; acessibilidade mínima continua obrigatória, com verificação completa em US3 antes de liberar.

## Phase 4: User Story 3 - Ler e operar com clareza (Priority: P1)

**Goal**: identidade textual, contraste e ações acessíveis com qualquer cor.
**Independent Test**: operar cards conhecidos/desconhecidos/ausentes por teclado em 360 e 1280 px, com estados legíveis sem depender da cor.

- [ ] T007 [P] [US3] Tratar nome vazio/apenas espaços como “Empresa não informada” em `frontend/src/components/OpportunityCard.tsx`, preservando iniciais decorativas `?`, e ampliar `frontend/tests/unit/components.test.tsx` para ausência, empresa visível e estados textuais; não alterar o DTO nem aceitar null por cast (FR-006/008).
- [ ] T008 [P] [US3] Acrescentar casos de acessibilidade em `frontend/tests/e2e/frontend.spec.ts` usando fixtures T003: axe e contraste 4,5:1 em texto normal/3:1 em texto grande; teclado em todas as ações existentes, foco visível, nomes e estados textuais e `scrollWidth <= clientWidth` em 360/1280 px; incluir lista de descartadas e acesso às vagas além do limite móvel de Hoje (FR-008/010; SC-003/004).
- [ ] T009 [US3] Executar T008 e corrigir problemas demonstrados de contraste, foco ou overflow em `frontend/src/app/panel.css` e, somente se necessário, em `frontend/src/index.css`; preservar texto/tinta, fundo claro, indicadores textuais e evitar opacidade no card inteiro; inspecionar pseudo-elementos e hachuras que axe não comprova, registrar medidas/evidências em `specs/003-company-card-colors/validation.md` e repetir cenários após correções.

**Checkpoint**: US1 + US3 formam o primeiro incremento liberável, sujeito aos gates finais.

## Phase 5: User Story 2 - Manter identidade ao navegar (Priority: P2)

**Goal**: provar consistência sem estado ou persistência de cor.
**Independent Test**: variar ordem/subconjuntos das mesmas vagas, navegar, descartar/restaurar, recarregar e reabrir o aplicativo; corrigir empresa e confirmar atualização.

- [ ] T010 [P] [US2] Ampliar `frontend/tests/unit/company-colors.test.ts` para equivalência por caixa/espaços externos, diferenças por chave exata sem aliases aproximados, repetição independente de ordem/subconjunto e nomes longos/Unicode; usar saídas esperadas de casos representativos, sem reimplementar o hash nos testes e sem exigir exclusividade das cores (FR-002/005).
- [ ] T011 [P] [US2] Ampliar `frontend/tests/e2e/frontend.spec.ts` para preservar cor entre Hoje/três listas, variação de ordem/subconjunto nas respostas simuladas, reload e novo contexto de navegador; modelar descarte/restauração com leituras atualizadas e correção do nome para outra empresa conhecida; não adicionar controles de ordenação/filtro ausentes nem mudar payloads, CSRF ou ações (FR-002/005/009; SC-002).
- [ ] T012 [US2] Executar os cenários de T010/T011, conferir os consumidores em `frontend/src/pages/Home.tsx` e `frontend/src/pages/Opportunities.tsx` e corrigir somente divergências comprovadas na resolução/consumo compartilhado em `frontend/src/lib/opportunity.ts` ou `frontend/src/components/OpportunityCard.tsx`; manter “Não criar Effects ou cache paralelo” e registrar resultados em `specs/003-company-card-colors/validation.md`.

**Checkpoint**: todas as histórias aceitas localmente e revisadas; ainda depende da entrega Git e dos checks aplicáveis.

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T013 Executar a matriz de `specs/003-company-card-colors/quickstart.md`, registrar comandos/resultados e cobertura realmente medida em `specs/003-company-card-colors/validation.md`, identificar configuração ausente sem declarar 80% atingido sem relatório, reconciliar documentação com o resultado final e obter revisão por agente de constituição/guia/contratos; revisar TypeScript, arquitetura e ausência de impacto Spring/JPA/SQLite; corrigir impeditivos e submeter as correções à nova revisão antes de concluir.
- [ ] T014 Conferir commits delimitados por tarefa e push da branch correspondente, abrir PR para main com spec, tarefas, validações e QA-declined, e conferir `.github/workflows/` quanto aos checks automatizados e revisão por agente exigidos; corrigir ou registrar explicitamente impedimentos reais, verificar execução no Git e registrar URL/estado em `specs/003-company-card-colors/validation.md`; não declarar gate ativo sem execução verificada nem encerrar com revisão/falha impeditiva pendente, e nunca fazer merge sem CI verde.

## Dependencies & Execution Order

```text
T001 → T002 → T003 → T004 → T005 → T006
                                      ├→ T007 ─┐
                                      └→ T008 ─┴→ T009
                                                   ├→ T010 ─┐
                                                   └→ T011 ─┴→ T012 → T013 → T014
```

As fases seguem prioridades P1 (US1), P1 (US3), P2 (US2). US3/US2 acrescentam aceitação sobre o mesmo resolver; não recriar implementação para tornar as histórias artificialmente independentes. Cada história possui teste próprio e pode ser validada sem executar as histórias posteriores. T009 depende de T007 e T008; T012 depende de T010 e T011. Revisões e commits de cada tarefa precedem o avanço de suas dependentes.

## Parallel Examples

- **US1**: tarefas T004–T006 sequenciais por compartilharem contrato/consumo; paralelizar apenas leituras de pesquisa e inspeção de consumidores, sem prometer implementação concorrente em arquivos comuns.
- **US3**: após T006, T007 (componente + testes unitários) e T008 (testes de navegador) podem ser preparados em paralelo; integração/execução final em T009. Não editar os mesmos arquivos ou o registro de validação simultaneamente.
- **US2**: após T009, T010 (teste unitário do resolver) e T011 (teste de navegador) podem ser preparados em paralelo; execução/correções em T012.

## Implementation Strategy

Entregar primeiro o MVP funcional US1, com testes e revisão. Completar US3 antes de liberar, pois seus critérios de acessibilidade são P1 e não podem ser adiados como acabamento. Acrescentar US2 para verificar estabilidade nas jornadas. Finalizar com validação consolidada, revisão, commits/push/PR e evidência real de checks no Git. Não alterar backend, esquema, contrato HTTP ou gerenciador de pacotes para viabilizar cores.

## Notes

Cobertura: FR-001/003/004/007 em T004–T006; FR-006 em T004/T007; FR-008/010 em T007–T009; FR-002/005 em T004/T010–T012; FR-009 em T005/T006/T011; SC-001 em T006, SC-002 em T011/T012, SC-003/004 em T008/T009. T013 consolida a evidência.

`before_tasks`/`after_tasks`: `.specify/extensions.yml` ausente na geração, portanto sem hooks registrados a executar. Nenhuma tarefa de implementação foi executada por esta geração documental.





