# Implementation Plan: Assistente pessoal de busca de emprego

**Branch**: `001-assistente-busca-emprego` (diretório da feature; branch Git ainda não criada, trabalho em `main`) | **Date**: 2026-11-14 | **Spec**: [spec.md](spec.md)

**Status**: Fase 0 e fase 1 concluídas. **Gate restante**: prova ao vivo PT-001/PT-002 ([quickstart §1](quickstart.md)) bloqueia as tarefas que integram o OpenBot; as demais tarefas podem começar.

## Summary

Serviço local único (Spring Boot, Java 25) que guarda todo o estado em SQLite, serve um painel Vite/React por HTTPS na Wi-Fi de casa e usa o OpenBot apenas como executor de raciocínio, pela API oficial **Local scripts**. O app, e não o modelo, controla bots, aprovações, rotinas, teto diário e envio de e-mail. Gmail é integrado direto pelo backend com escopo mínimo e envio só com aprovação vinculada ao conteúdo. Coleta de vagas é feita pelo backend com fontes permitidas (LinkedIn restrito a alertas por e-mail e entrada manual). Detalhes em [research.md](research.md).

## Technical Context

**Language/Version**: Java 25 LTS (backend); TypeScript 5 (frontend).
**Primary Dependencies**: Spring Boot 4.x (Web, Security, Validation, Scheduling, Data JPA), sqlite-jdbc + Hibernate SQLiteDialect, Flyway, Google API Client + Gmail API, Jackson + validador JSON Schema (networknt), Argon2 (Spring Security crypto/Bouncy Castle); Vite, React, React Router, TanStack Query. OpenBot 0.70.0 via HTTP Local scripts (sem SDK).
**Storage**: SQLite em `%LOCALAPPDATA%\Curriculotron\data`, WAL, Flyway; backups diários rotativos. Token Gmail cifrado com DPAPI.
**Testing**: JUnit 5, Mockito, AssertJ, Spring Boot Test, WireMock (OpenBot/Gmail/fontes); Vitest + Testing Library; Playwright (360/1280 px) + axe-core.
**Target Platform**: Windows 11 do usuário (serviço local); navegadores do computador e do celular na mesma LAN.
**Project Type**: aplicação web privada — backend + frontend, um usuário por instalação.
**Performance Goals**: dados locais em ≤3 s em 95% de 20 aberturas; revisão diária mediana ≤10 min.
**Constraints**: nenhuma candidatura automática; envio só com aprovação por versão; nenhuma troca automática para consumo pago; conteúdo externo nunca concede capacidades; acesso só pela Wi-Fi de casa; depende do computador ligado e do OpenBot aberto.
**Scale/Scope**: 1 usuário; 4 bots iniciais + criados por conversa; ≤10 novas seleções/dia (America/Sao_Paulo); centenas de vagas e mensagens por mês; 6 jornadas na V1.

## Constitution Check

*Pré-pesquisa*: `.specify/memory/constitution.md` é template sem princípios ratificados ⇒ nenhum gate constitucional. Gates aplicados vêm da especificação:

| Gate (spec) | Pré-desenho | Pós-desenho |
| --- | --- | --- |
| PT-001–PT-008 com classificação e evidência | Bloqueado | ✅ Classificados ([research](research.md#status-das-investigações)); PT-001/002 "restrito — não demonstrado" |
| Limitação central volta ao usuário | — | ✅ Stack, acesso móvel e prova decididos pelo usuário; LinkedIn restrito comunicado |
| Sem troca silenciosa de serviço/custos | — | ✅ D-04/D-10: sem fallback de provedor; limite ⇒ pausa |
| Enforcement de aprovação fora do modelo | — | ✅ D-05, contratos de saída estruturada |
| Minimização Gmail definida | — | ✅ D-07 (consulta temporal, metadata primeiro) |
| Prova ao vivo no ambiente alvo | Bloqueado | ⏳ Pendente — bloqueia só tarefas de integração OpenBot |

Nenhuma violação a justificar.

## Project Structure

### Documentation (this feature)

```text
specs/001-assistente-busca-emprego/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── panel-api.md
│   ├── openbot-adapter.md
│   └── bot-task-outputs.md
└── checklists/requirements.md
```

### Source Code (repository root)

```text
backend/                               # Spring Boot, Gradle Kotlin DSL
├── build.gradle.kts
└── src/
    ├── main/java/dev/curriculotron/
    │   ├── shared/                    # bootstrap, config, HTTPS, executor de escrita, conversores JPA, erros da API
    │   ├── auth/                      # senha do app, sessão, CSRF
    │   ├── idempotency/               # Idempotency-Key
    │   ├── profile/                   # perfil, fatos com origem, critérios, empresas
    │   ├── opportunities/             # vagas, dedupe, seleção diária
    │   ├── sources/                   # adaptadores: gupy, company-boards, linkedin-alerts, manual
    │   ├── applications/              # candidaturas, histórico, evidências, revisão
    │   ├── mail/                      # Gmail OAuth, descoberta, associação, drafts, outbox
    │   ├── bots/                      # Bot, BotConfig, aprovação, catálogo de capacidades
    │   ├── orchestration/             # TaskRun, rotinas, checkpoints, coordenador
    │   ├── openbot/                   # cliente Local scripts, validação de saída
    │   ├── interview/                 # simulação e feedback
    │   └── audit/                     # ActivityLog, IntegrationStatus
    ├── main/resources/
    │   ├── db/migration/              # Flyway
    │   └── schemas/                   # JSON Schemas de bot-task-outputs
    └── test/
        ├── java/…                     # unit + integração (WireMock, SQLite temporário)
        └── resources/fixtures/        # vagas, e-mails, respostas de agentes (inclui maliciosos)

frontend/                              # Vite + React + TypeScript
├── src/
│   ├── pages/                         # Início, Vagas, Candidaturas, Revisão, Bots, Conversa, Perfil, Integrações, Entrevista
│   ├── components/
│   └── api/                           # cliente tipado da panel-api
└── tests/
    ├── unit/
    └── e2e/                           # Playwright J1–J6, perf/
```

**Structure Decision**: duas unidades (`backend/`, `frontend/`); o build do frontend é copiado para os estáticos do Spring Boot, resultando em um processo e uma origem em execução. Pacotes do backend por domínio, alinhados às entidades do [data-model](data-model.md); dentro de cada um, arquitetura hexagonal (`domain/`, `application/` + `port/out/`, `adapter/in/`, `adapter/out/persistence/` com JPA) — ver D-03.

## Phase 0: Research

Concluída — [research.md](research.md). Decisões D-01 a D-12; matriz de fontes; status PT-001–PT-008.

## Phase 1: Design & Contracts

Concluída — [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md).

## Ordem sugerida para tasks

1. Esqueleto backend/frontend, segurança (login, HTTPS, CSRF), SQLite/Flyway — independe do OpenBot.
2. Perfil, vagas, dedupe, seleção diária, fontes, candidaturas, revisão (J1, J3, J5) com ranking por regras objetivas — independe do OpenBot.
3. **Gate quickstart §1** ⇒ adaptador OpenBot, bots, coordenador, criador (J2), ranking com modelo.
4. Gmail, drafts, outbox, resumo (J4).
5. Entrevista (J6); acesso pelo celular; desempenho.

## Riscos e limitações declaradas

- LinkedIn: cobertura parcial (alertas por e-mail + manual) — não há meio permitido de busca direta.
- Gmail `gmail.modify` é restrito; em modo Testing exige reconexão a cada 7 dias (validar alternativa).
- Agente OpenBot possui ferramentas amplas; mitigado por tarefas sem navegação, aprovações do OpenBot ligadas e testes de injeção, mas não eliminado.
- Painel inacessível fora de casa e com computador desligado ou OpenBot fechado (este último pausa só tarefas de bots).

## Complexity Tracking

Sem violações constitucionais.
