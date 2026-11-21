# Quickstart e Guia de Verificação: Padrões Visuais e Biblioteca de Componentes

**Feature**: `005-padroes-visuais-componentes`  
**Date**: 2026-11-16

## 1. Pré-requisito de Sincronização e Regra de Design Local

1. Confirmar que `design/` **não** está rastreado nem será commitado (`git status` não deve exibir `design/` como `staged` após `origin/main` ser integrado).
2. Antes de codificar na etapa de implementação, atualizar o branch de trabalho a partir de `origin/main` (`e9afc88` ou mais recente) para herdar `V13__opportunity_compatibility.sql`, `.gitignore` de `/design/`, `opportunity.ts` (`003`) e os parsers tipados (`004`).

## 2. Verificação do Backend (Java 25 + Spring Boot 4.1.2 + SQLite real)

Na raiz do repositório:

```powershell
# Formatação + testes unitários + testes de integração (SQLite temporário + Flyway V14)
./backend/gradlew.bat -p backend spotlessCheck test integrationTest
```

Cenários obrigatórios a cobrir nos testes de backend:
- Flyway `V14` aplica e bloqueia `UPDATE`/`DELETE` em `opportunity_correction` (append-only em SQLite real).
- `POST /api/opportunities/{id}/corrections`:
  - Valida entrada inválida com `400` (salário `min > max`, enum inválido, requisição sem alteração).
  - Preserva `opportunity_source` e `opportunity_evidence` originais intactos enquanto grava `opportunity_correction` e evento `opportunity.corrected` em `activity_log`.
  - Respeita `Idempotency-Key` (replay `200`, payload divergente `422`).
  - Exige CSRF/autenticação sob Spring Security.
- `POST /api/opportunities/{id}/external-visit`:
  - Grava `opportunity.external_opened` em `activity_log` e retorna `204` sem alterar `user_decision` nem criar `application`.
- `GET /api/home` e `GET /api/opportunities/{id}`:
  - Retornam `pendingReviewItems`, `seniority`, `corrections` e `timeline` com DTOs nomeados.

## 3. Verificação do Frontend (TypeScript strict + Vitest + Build)

Em `frontend/`:

```powershell
npm ci --ignore-scripts
npm run lint
npm run format:check
npm run test
npm run build
```

Cenários unitários obrigatórios (`Vitest`):
- `companyColor` retorna as 8 marcas conferidas e `#6B6B73` (`GRAPHITE`) para empresas desconhecidas ou ausentes (emenda à spec `003`).
- `SurfaceStateBlock` renderiza os 6 estados (`VAZIO`, `CARREGANDO`, `PARCIAL`, `FALHA`, `DESATUALIZADO`, `PENDENTE DE REVISÃO`) com rótulos, marcações e ações esperadas.
- Marcadores epistêmicos (`✓`, `~` + `.dsh` + motivo com fallback, `.hatch` de ausência, `Space Mono` de evidência com acionador de correção) e correspondência `você tem` / `não está no perfil`.
- Parsers runtime de API validam `pendingReviewItems`, `TaskRun` (`sourcesConsulted`/`sourcesFailed`), `seniority`, `corrections` e `timeline`.

## 4. Verificação End-to-End, Responsividade e Acessibilidade (`Playwright` + `@axe-core/playwright`)

Em `frontend/`:

```powershell
npx playwright test --config playwright.frontend.config.ts
```

Cobertura E2E exigida para aprovação da entrega:
- Percorrer todas as rotas do painel (`/`, `/vagas`, `/vagas/:id`, `/referencias-salariais`, `/candidaturas`, `/candidaturas/:id`, `/revisao`, `/atividades`, `/integracoes`, `/bots`, `/perfil`, `/criterios`, `/empresas`, `/dados`) em desktop (`1280x800`) e celular (`390x844`):
  - Zero violações automatizadas no `@axe-core/playwright` (inclusive contraste AA em cartões de empresa, `DESATUALIZADO`, `FALHA`, `PENDENTE DE REVISÃO`, `você tem` e `não está no perfil`);
  - Zero rolagem horizontal (`document.documentElement.scrollWidth <= window.innerWidth`);
  - Navegação completa por teclado (`Tab` + foco visível e alvos `>= 44px`).
- Fluxo de correção de evidência no detalhe da vaga: clicar em `"Corrigir algum dado →"` / evidência -> alterar salário/local/modalidade -> salvar -> confirmar `"corrigido por você"`, histórico original preservado e atualização na linha do tempo.
- Fluxo de abertura externa no detalhe da vaga: clicar em `"Abrir vaga no site ↗"` -> registrar evento na linha do tempo com `"(isso não registra candidatura)"` e manter `userDecision = 'none'`.
- Conferir que `frontend/dist` não contém nenhum arquivo de `design/`.
