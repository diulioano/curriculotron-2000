# Validação — cores por empresa

## T001 — preparação

Branch `feature/US-003-company-card-colors` criada da main sincronizada com origin/main, base `775f7f33dffb29d9072a0d38213427a90c6a18d6`. Fetch executado; divergência 0/0. Documentos da feature preservados. Checklist requirements: 16/16. Sem extensions.yml para before_implement. Ignore files e ESLint verificados; saídas frontend já ignoradas. Sem publicação npm.

Revisão T001: APPROVED pelo agente qa; base e estado conferidos, git diff --check sem problemas. Nenhum código alterado nesta tarefa.

## T002 — baseline

npm ci executado: 238 pacotes, 7 vulnerabilidades moderadas reportadas pelo npm (sem atualização fora do escopo). Chromium disponível após playwright install. npm test: 29/29; npm run lint: PASS; npm run build: PASS; npm run test:frontend: 32/32 nos dois viewports. npm run format:check: FAIL preexistente em playwright.config.ts, src/api/client.ts, src/pages/{ApplicationDetail,Login,Opportunities,Profile,ReviewQueue}.tsx e tests/e2e/support/seed.ts. Nenhum desses arquivos foi alterado. Cobertura não configurada neste baseline; não afirmar percentual. Sem dependência/versão nova. A falha de formatação preexistente está registrada e não será confundida com regressão da feature.

Revisão T002: APPROVED por qa; revisor repetiu unidade29/lint/build e confirmou a falha de formatação nos mesmos oito arquivos.

## T003 — fixtures

Fixtures de 13 vagas e API simulada stateful criadas. Lint/build e 29 testes PASS; format global conserva oito falhas anteriores. Revisão qa APPROVED; revisor executou eslint focado e tsc -b. Importação de DTO da aplicação evitada nas fixtures por incompatibilidade NodeNext transitiva preexistente; fixtures inferem seus tipos sem any.
