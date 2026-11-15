# Validação — cores por empresa

## T001 — preparação

Branch `feature/US-003-company-card-colors` criada da main sincronizada com origin/main, base `775f7f33dffb29d9072a0d38213427a90c6a18d6`. Fetch executado; divergência 0/0. Documentos da feature preservados. Checklist requirements: 16/16. Sem extensions.yml para before_implement. Ignore files e ESLint verificados; saídas frontend já ignoradas. Sem publicação npm.

Revisão T001: APPROVED pelo agente qa; base e estado conferidos, git diff --check sem problemas. Nenhum código alterado nesta tarefa.

## T002 — baseline

npm ci executado: 238 pacotes, 7 vulnerabilidades moderadas reportadas pelo npm (sem atualização fora do escopo). Chromium disponível após playwright install. npm test: 29/29; npm run lint: PASS; npm run build: PASS; npm run test:frontend: 32/32 nos dois viewports. npm run format:check: FAIL preexistente em playwright.config.ts, src/api/client.ts, src/pages/{ApplicationDetail,Login,Opportunities,Profile,ReviewQueue}.tsx e tests/e2e/support/seed.ts. Nenhum desses arquivos foi alterado. Cobertura não configurada neste baseline; não afirmar percentual. Sem dependência/versão nova. A falha de formatação preexistente está registrada e não será confundida com regressão da feature.

Revisão T002: APPROVED por qa; revisor repetiu unidade29/lint/build e confirmou a falha de formatação nos mesmos oito arquivos.

## T003 — fixtures

Fixtures de 13 vagas e API simulada stateful criadas. Lint/build e 29 testes PASS; format global conserva oito falhas anteriores. Revisão qa APPROVED; revisor executou eslint focado e tsc -b. Importação de DTO da aplicação evitada nas fixtures por incompatibilidade NodeNext transitiva preexistente; fixtures inferem seus tipos sem any.

## T004 — resolução de cor

TDD: 13 casos novos, cinco falhas antes da mudança e 13 PASS depois; suíte global 42/42, lint/build e Prettier dos arquivos tocados PASS. Cores prioritárias, Map exato, alias itau, grafite e hash da paleta implementados. Revisão qa APPROVED; revisor executou eslint dos arquivos e 13 testes focados.

## T005 — consumo no card

Consumidor e CSS existentes já usam --c em contorno/carimbo/hachura; nenhuma mudança de produção adicional necessária. Testes de componente cobrem três cards e ações. Global43/43, lint/build e Prettier focado PASS. Revisão qa APPROVED com 9/9 testes de componente e inspeção CSS.

## T006 — cores computadas

Browser 2/2 PASS (360/1280), quatro superfícies e 13 cards + detalhe BV, bordas/pseudo-elementos/carimbo/hachura. Unit43/lint/build e Prettier focado PASS. Revisão qa APPROVED; MINOR sobre prova de rede ACEITO e corrigido por whitelist API e mesma origem; 2/2 repetidos e correção revisada APPROVED.

## T007 — empresa ausente

Duas falhas TDD de rótulo antes, 46/46 testes depois. Rótulo ausente, grafite, iniciais decorativas e restauração explícitos. Lint/build PASS. Revisão qa APPROVED, 12/12 componentes executados pelo revisor.

## T008 — teclado e acessibilidade

Browser2/2 PASS (360/1280), com Tab em controles visíveis, foco dashed3px, axe sem violações, overflow ausente, tinta/fundo/opacidade preservados, leitura sem cor e Enter nas ações. Houve falha de fixture antes: todos descartados ao voltar à lista ativa; corrigida e reexecutada. Unit46/lint/build/Prettier tocados PASS. Revisão qa APPROVED com eslint focado; sem certificação universal apenas pelo axe.

## T009 — inspeção dirigida e contraste

Nenhum defeito CSS demonstrado: não houve alteração de panel.css/index.css. Inspeção do CSS existente confirmou .sk::before/after com --c e pointer-events:none, hachura decorativa e marca aria-hidden, carimbo com texto sobre --card, foco de tinta independente da marca e opacidade1. T006 verificou cores computadas nos dois pseudo-elementos, carimbo/hachura para13 cards; T008 verificou foco/overflow/axe e opacidade em ambas as larguras. Contraste calculado pela luminância relativa sRGB sobre #fffdf6: tinta #1b1b1f=16,87:1; suave #2a2a30=14,01:1; muted #44444c=9,48:1; hover #55555d=7,26:1. Todos excedem 4,5:1 normal e 3:1 grande. Evidência é inspeção de regras e estilos computados; não foi feita certificação visual humana universal. Revisão T009 APPROVED por qa, com recálculo independente de contraste e inspeção CSS.


## T010 — invariantes de identidade

Testes estendidos de caixa/bordas, nomes similares, ordem/subconjunto, Unicode e nomes longos: global58/58 PASS; revisão qa APPROVED com25/25 casos focados. Sem nova lógica de produção.

## T011 — estabilidade nas jornadas

Browser2/2 PASS em360/1280: Hoje/listas, ordem/subconjunto, reload, descarte/restauração, correção SAP->BV e novo contexto de navegador preservam identidades. Global58/lint/build PASS, format global mantém oito falhas anteriores. Revisão qa APPROVED com eslint focado e inspeção do cenário.

## T012 — integração da identidade

T010/T011 executados com sucesso. Home e Opportunities inspecionados: consomem o mesmo OpportunityCard; detalhe usa o mesmo companyColor. Nenhuma divergência demonstrada que exija código adicional. Sem Effects, cache paralelo ou persistência de cor. Revisão T012 APPROVED por qa; integração e comparação de produção contra base conferidas.

## T013 — consolidação local

Resultados finais: npm run test:frontend 38/38 (360/1280); npm test58/58; lint/build PASS; Prettier de todos os seis arquivos frontend tocados PASS. format:check global continua falhando somente nos oito arquivos preexistentes listados em T002. Nenhuma validação backend foi executada (sem alteração pertinente).

Cobertura medida com @vitest/coverage-v8 5.1.0, versão conferida no registro npm e uso conforme https://vitest.dev/guide/coverage.html . Instalação temporária no node_modules (`npm install --no-save --package-lock=false --ignore-scripts`), sem mudar manifest/lock, seguida de npm ci --ignore-scripts para restaurar exatamente o lockfile. Relatório V8 dos dois arquivos de produção tocados: 66,66% de linhas globais (32/48), não representa todo o frontend. Recorte por diff contra a base: 11/11 linhas executáveis adicionadas/alteradas com início de statement/branch cobertas, conforme coverage-changed-lines.json; ramo do rótulo na linha98 teve contagens9 e2. Não declarar gate de cobertura global ou CI ativo. Tentativa anterior com NODE_V8_COVERAGE bruto não capturou módulos transformados do Vitest e não foi usada como evidência.

O resultado da implementação local atende aos cenários descritos; entrega completa de US permanece dependente dos gates Git de T014. Revisão consolidada T013 APPROVED por qa; revisor executou58 testes/lint/build/diff-check e conferiu recorte e escopo. Nenhum QA-declined: o único MINOR foi aceito e corrigido. Sem hooks after_implement (extensions.yml ausente).

## T014 — entrega Git parcial e gates pendentes

Commits delimitados T001–T013 enviados para origin/feature/US-003-company-card-colors. PR rascunho aberto: https://github.com/diulioano/curriculotron-2000/pull/4 . Execução frontend-ci iniciada no Git e observada em andamento: https://github.com/diulioano/curriculotron-2000/actions/runs/43747439169 ; isto comprova início, não sucesso.

Inspeção dos workflows e da API GitHub no SHA inicial do PR: frontend-ci executava npm ci --ignore-scripts, lint, testes e build; não executava esta suíte Playwright, cobertura ou revisão por agente. Backend-ci possuía Sonar condicional. Repositório sem secrets cadastrados, inclusive SONAR_TOKEN e credencial de revisão. main protegida com uma aprovação obrigatória; resposta da proteção não listou checks obrigatórios. A proteção não foi alterada nesta demanda.

T014 permanece desmarcada: não foi possível certificar a esteira obrigatória de revisão por agente no Git, Sonar e gates de cobertura. Não adicionar provedor pago nem prometer revisão automatizada sem credencial/configuração. A US não está declarada integralmente concluída e o PR não foi mergeado. São necessários configurar/revalidar esses gates antes da liberação. Revisão local por agentes não substitui execução no Git.

Revisão qa APPROVED somente para o registro parcial de T014; tarefa permanece aberta e gates não certificados. Nenhum hook after_implement registrado (extensions.yml ausente).

## T014 — correção do gate frontend após revisão do PR

O workflow frontend-ci passou a instalar Chromium, executar `npm run test:frontend` e medir cobertura V8 com `@vitest/coverage-v8` fixado em 5.1.0. O script `frontend/scripts/check-changed-coverage.mjs` compara o relatório com o diff do SHA base do PR e exige 80% das linhas executáveis alteradas. `coverage/` foi ignorado pelo Git. A medição local foi 11/11 linhas (100%); cobertura global de `frontend/src` foi 47,51%, incluindo legado não coberto. O gate novo não afirma 80% global.

Validação local da correção: 58/58 testes unitários com V8, 38/38 testes Playwright nos dois viewports, lint e build PASS; Prettier de package.json, vite.config.ts e do script PASS. Revisão independente do diff da correção: APPROVED, sem defeito impeditivo; o revisor não executou testes. A execução do workflow no Git será verificada após o push. A revisão por agente na CI e a configuração de proteção da branch continuam pendentes; T014 segue aberta.

