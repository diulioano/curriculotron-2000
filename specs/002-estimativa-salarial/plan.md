# Implementation Plan: Estimativa salarial

**Feature**: 002-estimativa-salarial | **Git**: main (sem nova branch) | **Data**: 2026-11-14 | **Spec**: [spec.md](spec.md)

## Summary

Acrescentar referência salarial com proveniência, cache de 90 dias e histórico às vagas sem salário. Enriquecimento na busca ou por atualização explícita, nunca em GET. Não altera fatos, salário declarado, ranking, filtros ou seleção. O usuário também autorizou modo local sem login; manter modo autenticado para futura VPS, sem implantação nesta demanda.

## Technical Context

- Linguagens: Java 25 e TypeScript ~6.0 (package.json atual; plano 001 dizia TS5).
- Dependências existentes: Spring Boot 4.1.2, Gradle Kotlin DSL, JPA, SQLite, Flyway, Jackson, networknt; React 19, Vite 8 e TanStack Query. Nenhuma dependência nova presumida.
- Storage: SQLite atual, migração aditiva V10 (rever versão livre antes de implementar; V9 de candidaturas já existe no workspace).
- Testes: JUnit/Mockito/WireMock, integração SQLite temporário, Vitest/Testing Library e Playwright. SC-102/104/105 pedem verificações de ranking, links e edge cases.
- Plataforma: aplicação web privada Windows, usuário único, centenas de vagas.
- Desempenho: preservar meta de leitura local <=3 s da 001; nenhuma rede em GET; timeout de consulta 30 s, sem retry automático.
- Restrições: uma consulta lógica por perfil por busca, inclusive falhas; orçamento explícito; BRL, CLT/PJ separados; sem inferir regime desconhecido; sem contornar login/captcha ou trocar provedor pago.

## Constitution Check

Constituição atual é template sem princípios ratificados. Gates pré e pós-desenho: separação fatos/inferências PASS (DTO separado); ranking imutável PASS (regressão); proveniência PASS (validação fora do modelo); falha isolada PASS (atividade/estado da fonte); login local PASS (perfil explícito com loopback e CSRF).

**Gate externo restrito**: Glassdoor não está liberado para coleta automática. Fonte real e destino de pesquisa exigem evidência e, para substituição por outra fonte, decisão do usuário conforme spec. Infraestrutura/UI podem ser implementadas com fixtures; FR-101 e SC-101 permanecem pendentes até validação real. Não afirmar que a feature inteira está concluída com adaptador indisponível. Sem depender de cliente OpenBot ainda inexistente na 001.

## Project Structure

Documentação nesta pasta: plan.md, research.md, data-model.md, quickstart.md, tasks.md e contracts/salary-api.md.

Código novo em backend/src/main/java/dev/curriculotron/salary/{domain,application,application/port/out,adapter/in/web,adapter/out/persistence,adapter/out/source}/, seguindo portas e adapters do projeto. Testes em backend/src/test/java/dev/curriculotron/salary/ e backend/src/integrationTest/java/dev/curriculotron/salary/.

Integrações: sources/SearchService.java (após seleção), opportunities/adapter/in/web/{OpportunityController,OpportunitySummaries}.java, audit/application/{ActivityLogService,IntegrationStatusService}.java. Usar executor de escrita existente para transações curtas; rede fora da transação.

Frontend: frontend/src/api/panel.ts, novo components/SalaryEstimate.tsx, components/OpportunityCard.tsx e pages/OpportunityDetail.tsx. Início/lista compartilham cartões e resumos.

Modo local: application-local.yml, SecurityConfig.java e SessionController.java existentes no domínio auth; frontend api/client.ts, app/App.tsx e app/Layout.tsx.

## Phase 0: Research

Concluída como investigação documental em [research.md](research.md); integração externa classificada restrita, não demonstrada. Nenhuma dúvida tratada como permissão presumida.

## Phase 1: Design & Contracts

[data-model.md](data-model.md), [contracts/salary-api.md](contracts/salary-api.md), [quickstart.md](quickstart.md). US1 e US2 P1, US3 P2. Cache básico na fundação; renovação e concorrência na US3. Gate externo só bloqueia ativação real, não desenvolvimento local.

## Complexity Tracking

Sem violações constitucionais. Sem novo banco, serviço ou migração VPS. Login local é ajuste operacional autorizado adicional às três histórias.

## Atualização de canal (2026-11-15)

Usuário escolheu coleta assistida pela ferramenta web. Uma leitura real foi demonstrada e salva em glassdoor-web-observation.json; detalhes em research.md D6. A porta deverá aceitar saída estruturada desse executor quando houver integração comprovada. A ferramenta desta sessão não acompanha automaticamente o backend local. Substituir a hipótese de impossibilidade técnica por: acesso web demonstrado, execução local autônoma e compatibilidade dos dados ainda pendentes. Não aplicar faixa genérica a CLT/PJ ou senioridade sem evidência.

## Prioridade de consulta — empresa + cargo

Conforme refinamento do usuário em 2026-11-15, pesquisar primeiro empresa + cargo; confirmar identidade da empresa e compatibilidade de senioridade/localidade/regime. Usar mercado apenas como fallback identificado. Cache específico separado por empresa; jamais transferir faixa de uma empresa para outra. Validar com duas empresas para o mesmo cargo antes de considerar busca específica demonstrada.
