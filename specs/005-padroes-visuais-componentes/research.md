# Pesquisa Técnica e Decisões Arquiteturais: Alinhamento aos Padrões Visuais e Biblioteca de Componentes

**Feature**: `005-padroes-visuais-componentes`  
**Date**: 2026-11-16  
**Spec**: [spec.md](file:///C:/Users/Diuliano/Documents/curriculotron-2000/specs/005-padroes-visuais-componentes/spec.md)

## 1. Alinhamento de Branch com `origin/main` e Proteção de `design/`

- **Constatação**: O branch local `main` (`775f7f3`) está 59 commits atrás de `origin/main` (`e9afc88`). O remoto já integrou:
  - PR #3 (`chore/keep-design-local`): inclusão de `/design/` no `.gitignore` e remoção de `design/Curriculotron 2000.html` do índice git;
  - PR #4 (`003-company-card-colors`): cor por empresa e inicialização visual em [opportunity.ts](file:///C:/Users/Diuliano/Documents/curriculotron-2000/frontend/src/lib/opportunity.ts);
  - PR #5 (`004-respeitar-tecnologias-perfil`): tabela `opportunity_compatibility` ([V13__opportunity_compatibility.sql](file:///C:/Users/Diuliano/Documents/curriculotron-2000/backend/src/main/resources/db/migration/V13__opportunity_compatibility.sql)), parsers tipados em runtime (`compatibility.ts`, `compatibility-detail.ts`) e componente `CompatibilityEvidence`.
- **Decisão**: O plano técnico, modelos de dados, contratos e migração Flyway (`V14`) desta feature assumem `origin/main` como linha de base. Antes de iniciar a implementação (`$speckit-implement`), o repositório de trabalho DEVE ser sincronizado/rebaseado sobre `origin/main`.
- **Alternativa rejeitada**: Planejar sobre `775f7f3` ignorando `003` e `004` causaria conflitos imediatos em `OpportunityDetail.tsx`, `panel.ts`, `OpportunityController.java` e numeração Flyway (`V13`).
- **Restrição**: `design/` e `design/Curriculotron 2000.html` permanecem exclusivamente como referência visual local ("o quê") e não devem ser versionados nem empacotados em `frontend/dist` ou recursos estáticos do backend.

## 2. Biblioteca Compartilhada de Estados de Superfície e Marcadores Epistêmicos

- **Decisão**: Implementar os 6 estados de superfície (`VAZIO`, `CARREGANDO`, `PARCIAL`, `FALHA`, `DESATUALIZADO`, `PENDENTE DE REVISÃO`) e os 4 padrões epistêmicos (`Fato declarado`, `Inferência do bot`, `Ausência não informada`, `Evidência`) com CSS nativo em [index.css](file:///C:/Users/Diuliano/Documents/curriculotron-2000/frontend/src/index.css) e [panel.css](file:///C:/Users/Diuliano/Documents/curriculotron-2000/frontend/src/app/panel.css) + componentes React puros e tipados em `frontend/src/components/`. Nenhuma biblioteca externa de UI ou ícones será adicionada (Princípio I da Constituição).
- **Mapeamento fiel ao quadro de padrões visuais**:
  - `VAZIO`: moldura `.sk` com fundo `var(--card)`, rótulo `VAZIO` em `Space Mono`, título direto em negrito e texto explicativo do que foi verificado e próximo passo opcional. Substitui `Empty` atual (`.dsh` sem rótulo) e estados vazios crus (como em `SalaryReferences.tsx` e `Placeholder.tsx`).
  - `CARREGANDO`: borda `.dsh` (`--d: var(--ink)`), rótulo `CARREGANDO` em `Space Mono`, texto de progresso (incluindo etapas/fontes quando disponível, ex.: `Buscando vagas… 3 de 6 fontes`) e barra `.hatch` (`height: 12px`, `border: 2px solid var(--ink)`). Substitui `<p className="empty mono">Carregando…</p>` em [Panel.tsx](file:///C:/Users/Diuliano/Documents/curriculotron-2000/frontend/src/components/Panel.tsx) e [App.tsx](file:///C:/Users/Diuliano/Documents/curriculotron-2000/frontend/src/app/App.tsx).
  - `PARCIAL`: borda sólida `2px solid var(--ink)`, fundo `var(--card)`, rótulo `PARCIAL`, título com contagem explícita (`X de Y fontes consultadas`) e explicação do motivo/próxima tentativa.
  - `FALHA`: borda `.dsh` (`--d: var(--danger)`), fundo `var(--card)`, rótulo `FALHA` em `var(--danger)` negrito, título, explicação e ação de recuperação (`Tentar de novo`, `Revisar em Integrações`).
  - `DESATUALIZADO`: fundo `.hatch` com `--c: #E2DCCB` e borda `2px solid #6B6B73`; todas as linhas de texto (rótulo `DESATUALIZADO`, carimbo real de data/hora, explicação e link/botão `Atualizar →`) recebem faixa de fundo sólida `var(--paper)` ou `var(--card)` (`padding: 0 4px`) para preservar contraste AA sobre a hachura.
  - `PENDENTE DE REVISÃO`: destaque `.hl` com borda `2px solid var(--ink)`, rótulo `PENDENTE DE REVISÃO`, título em negrito, contexto e ação direta para decisão humana.
- **Padrões epistêmicos**:
  - **Fato declarado**: tinta sólida `var(--ink)` com prefixo `✓` e sem contorno tracejado; no detalhe da vaga, requisitos declarados recebem indicador de perfil (`você tem` / `não está no perfil`).
  - **Inferência do bot**: prefixo `~`, contorno `.dsh` (`--d: #6B6B73`) ou `.dsh-b` (em linha no resumo), motivo entre parênteses. Quando uma inferência vier sem razão detalhada do backend, o componente injeta explicação padrão explícita (`(inferido pelo sistema)`). Estimativas salariais de referência exibidas na ausência de salário declarado passam a usar esse contêiner de inferência mantendo a faixa declarada ausente visível.
  - **Ausência não informada**: `.hatch` (`--c: #C9C2AE`) envolvendo `<span>` com `background: var(--paper)` ou `var(--card)` para garantir legibilidade e contraste AA (ex.: `salário a confirmar`, `sem atualização bem-sucedida ainda`, `nenhuma próxima ação definida`).
  - **Evidência**: tipografia `Space Mono`, fonte + timestamp real (`consultado:` e `publicada:` quando conhecida ou ausência explícita) + acionador clicável para correção manual.

## 3. Regra de Cores por Empresa e Auditoria de Contraste AA

- **Decisão (Q1-C — Emenda explícita à `FR-004` da especificação `003`)**:
  - Em `frontend/src/lib/opportunity.ts`, manter o mapa `BRANDS` com as 8 marcas explicitamente conferidas: `Itaú` (`#EC7000`), `SAP` (`#0070F2`), `PicPay` (`#21C25E`), `Sicredi` (`#2F7D12`), `BV` (`#005CA9`), `CWI` (`#FF8A00`), `DB` (`#F70773`) e `CI&T` (`#FA5A50`).
  - Remover o fallback cromático por hash (`COMPANY_PALETTE`) e retornar `GRAPHITE` (`#6B6B73`) para qualquer empresa fora dessa lista ou sem nome informado.
  - Documentar essa alteração explicitamente no plano e no código como emenda à `FR-004` da spec `003`.
- **Auditoria de contraste AA**:
  - Cores de marca permanecem restritas a bordas (`.sk` `--c`), hachuras (`.hatch` `--c`), barra lateral (`.next-bar`), sublinhado zigue-zague e contorno de carimbo.
  - Textos sobre `--paper` (`#FBF8EF`) e `--card` (`#FFFDF6`) usam apenas tintas escuras verificadas: `--ink` (`#1B1B1F`, ~16,5:1), `--ink-soft` (`#2A2A30`, ~13,8:1), `--ink-muted` (`#44444C`, ~9,1:1), `--danger` (`#9A2A12`, ~7,4:1) e `--ok` ajustado de `#2F7D12` (~5,1:1) para `#1F6B0E` (~6,5:1, idêntico ao protótipo do detalhe da vaga) para maior margem em textos pequenos (`14px` negrito).
  - Em `prefers-reduced-motion: reduce`, desativar animações de nuvens no rodapé (`.cloud`) e transições de hover em `.bt` e links interativos.

## 4. Correção Manual Direta de Evidência em Vagas

- **Decisão (Q3-A)**: Ao clicar em um bloco de evidência ou em `"Corrigir algum dado →"` no detalhe da vaga, abrir um formulário inline no próprio painel de detalhe (ou painel expansível acessível junto à evidência) para edição direta dos campos da vaga: `title`, `companyName`, `location`, `workMode`, `contractType`, `salary` (`min`, `max`, `unit`), `availability`, `deadlineAt` e `publishedAt`, além de um campo opcional de observação/motivo.
- **Preservação de proveniência e histórico**:
  - A tabela `opportunity_source` (fontes originais coletadas) e `opportunity_evidence` nunca são alteradas pela correção humana.
  - Uma nova tabela append-only `opportunity_correction` (criada em `V14`, protegida por triggers SQLite `BEFORE UPDATE` e `BEFORE DELETE` idênticos aos de `activity_log`) registra cada campo alterado (`field_name`, `previous_value`, `corrected_value`, `reason`, `author = 'user'`, `corrected_at`), enquanto atualiza os campos efetivos em `opportunity` dentro da mesma transação curta no `WriteExecutor`.
  - Cada alteração gera entrada correspondente em `activity_log` (`action = 'opportunity.corrected'`, `target_type = 'opportunity'`, `target_id = id`).
  - Se a correção alterar campos de negócio/conteúdo (`title`, `company_name`, `location`, `work_mode`, `contract_type`, salário ou `availability`), a revisão de evidência da vaga é incrementada e o caso de uso existente (`AssessOpportunityService`) reavalia a compatibilidade; datas isoladas não invalidam a avaliação. Com título corrigido, a avaliação usa em memória uma evidência `user_correction` e ignora o título bruto da fonte, sem alterar `opportunity_evidence` (ver `data-model.md` §2.1 e a emenda à spec `004` em `contracts/visual-library-and-correction-api.md`).
  - Correções humanas prevalecem por campo sobre re-ingestões futuras; fontes e evidência continuam registrando o dado bruto coletado.
- **Alternativa rejeitada**: Salvar apenas em `activity_log` sem tabela estruturada de correções dificultaria reconstruir quais campos específicos têm correção humana ativa e exibir `"corrigido por você · <data>"` por campo/evidência.
- **Concorrência e idempotência**:
  - Escrita serializada pelo `WriteExecutor` existente (SQLite single-writer, Princípio VI).
  - Cliente envia `Idempotency-Key` (via helper `post()` já existente) tratado pelo `IdempotencyFilter` já instalado, evitando duplicação acidental de registros em `opportunity_correction` e `activity_log` em caso de retry de rede (Princípio V).

## 5. Exposição de Progresso Parcial, Pendências Individuais e Linha do Tempo da Vaga

- **Estado `PARCIAL` em execuções (`TaskRun`)**:
  - `dev.curriculotron.orchestration.domain.TaskRun` já armazena `sourcesConsulted` e `sourcesFailed` em `V5__task_run.sql` e já é serializado pelo `SearchController` em `/api/task-runs` e `/api/task-runs/{id}`.
  - No frontend, atualizar o tipo `TaskRun` (e parser/consumidores) em `frontend/src/api/panel.ts` para expor `kind`, `sourcesConsulted`, `sourcesFailed`, `startedAt`, `finishedAt`, `createdAt`. Quando `sourcesFailed.length > 0` em uma execução concluída (ou quando o backend indicar cobertura parcial), `Activities.tsx` e `Integrations.tsx` renderizam o estado `PARCIAL` com `X de Y fontes consultadas` e os nomes das fontes com falha.
- **Seção `"Precisa de você"` na página inicial (`HomeController` + `Home.tsx`)**:
  - Atualmente `HomeController` devolve apenas `reviewQueueCount` (número agregado de `candidates.open().size()`) e `mailNeedingAttention` vazio.
  - Adicionar ao `HomeResponse` o campo `pendingReviewItems` (lista resumida dos itens individuais de revisão/duplicatas abertas, limitada para o card da Home, com `id`, `kind`, `title`, `summary`, `targetRoute`) mantendo `reviewQueueCount` para compatibilidade e contagem total. Assim, `NeedsYou` renderiza cada pendência com `PENDENTE DE REVISÃO` (rótulo monoespaçado, descrição e link direto) em vez de apenas um bloco único genérico.
- **Linha do tempo e abertura de link externo no detalhe da vaga**:
  - `OpportunityDetailResponse` ganha `timeline` (eventos ordenados cronologicamente vindos de `opportunity_source`, `daily_selection`, `opportunity_correction` e `activity_log` para `target_type='opportunity'`) e `corrections` (lista de correções humanas registradas).
  - Ao clicar em `"Abrir vaga no site ↗"` no detalhe da vaga, o frontend mantém a navegação externa sem bloquear a aba e envia de forma explícita e não bloqueante `POST /api/opportunities/{id}/external-visit`, que registra `opportunity.external_opened` em `activity_log` (sem criar candidatura nem alterar `userDecision`). Isso preserva estritamente o Princípio VII (`GET` nunca dispara mutação e abertura de link nunca registra candidatura).
- **Indicador de nível (`NÍVEL`) e correspondência de requisitos declarados no detalhe da vaga**:
  - No resumo do detalhe (`Summary`), adicionar o campo `NÍVEL` derivado de fato/inferência do domínio ou marcador de ausência quando não identificável, usando o estilo epistêmico correspondente (`✓` quando declarado nos fatos ou `~ <nível> (inferido: ...)` quando extraído de pistas como anos de experiência no título/evidência).
  - Na coluna `Declarados na vaga` (anteriormente `Fatos da vaga`), cruzar os requisitos/fatos exibidos com `compatibilityCriteria.stackKeywords`, `compatibilityEvidence` e dados do `Profile` já disponíveis, rotulando cada item correspondente como `você tem` (`#1F6B0E`) e itens explícitos fora do perfil como `não está no perfil` (`#9A2A12`), sem classificar inferências como requisitos oficiais.

## 6. Padronização das Demais Telas e Escopo Controlado (`Bots` e `Preparar`)

- **Telas padronizadas neste ciclo**:
  - `Home.tsx`, `OpportunityDetail.tsx`, `Opportunities.tsx`, `SalaryReferences.tsx`, `Applications.tsx`, `ApplicationDetail.tsx`, `ReviewQueue.tsx`, `DedupeReview.tsx`, `Activities.tsx`, `Integrations.tsx`, `Profile.tsx`, `Criteria.tsx`, `Companies.tsx`, `Privacy.tsx`, `Placeholder.tsx` (`/bots`) e `Layout.tsx`.
  - Padronizar todos os botões (`.btn.bt.bt-pri` e `.btn.bt.bt-sec.dsh`), caixas de seleção (`.check-field`), cabeçalhos (`PageHeader`), alvos mínimos de toque (`min-height: 44px`), foco visível via teclado e ausência de `overflow-x` em `390px` e `1280px`.
- **Escopo estritamente controlado (Q2-A)**:
  - `/bots` (`Placeholder.tsx`): aplica `PageHeader` e o componente de estado `VAZIO` padronizado informando que o criador e controle de bots ainda não foram habilitados nesta versão (registrando nas notas de futuro ciclo a referência ao layout de busca de vagas).
  - Bloco `Preparar` no `OpportunityDetail`: exibe a seção estruturada conforme o protótipo com estado padronizado indicando que a simulação de entrevista ainda não está disponível neste ciclo, sem criar backend nem fluxo conversacional de simulação.
