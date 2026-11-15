# Implementation Plan: Respeitar tecnologias e cargos do perfil

**Branch**: `feature/US-004-respeitar-tecnologias-perfil` | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md)

**Input**: `specs/004-respeitar-tecnologias-perfil/spec.md`. Na fase de planejamento o setup resolveu BRANCH pelo diretório ativo sem trocar o checkout. T001 criou a branch exclusiva da implementação a partir de origin/main atualizado, base `c617fa7326f775aa1c50593c9375d6345a69a129`, preservando os documentos desta feature. Ignore files verificados e complementados para artefatos Java/coverage/build.

## Summary

Corrigir a seleção principal, lista secundária e início para respeitar cargos e tecnologias salvos. Separar avaliação por eixo de score, exigir evidência contextual e tornar descrição insuficiente uma pendência explícita. Implementar GET/PUT de critérios, ainda ausentes no backend, persistir evidências reutilizáveis e reavaliar vagas existentes por comando explícito sem coleta externa. Manter decisões, deduplicação e consumo histórico do teto diário.

## Technical Context

**Language/Version**: Java 25; TypeScript ~6.0 strict.
**Primary Dependencies**: Spring Boot 4.1.2, MVC, Security, Validation, Spring Data JPA/Hibernate e Flyway; React ^19.3.1, Vite ^8.3.6 e TanStack Query ^5.107.1. Versões observadas nos manifests, sem upgrade ou dependência nova.
**Storage**: SQLite 3.54.1.0 via JDBC; migração Flyway aditiva após V12, número a conferir na implementação; adapters JPA com métodos derivados/JPQL.
**Testing**: JUnit e testes Spring com SQLite temporário; Vitest/Testing Library; Playwright em 360 e 1280 px com fontes simuladas. Fonte de integração separada atualmente sem casos, não assumir cobertura.
**Target Platform**: Aplicação privada local, Windows/Linux, uma instalação/um usuário; HTTP loopback local e segurança existente nos demais perfis.
**Project Type**: Monorepo web backend/frontend.
**Performance Goals**: Meta de validação, ainda não medida: listas com até 1.000 vagas respondem em até 2 s no ambiente local de referência; reavaliação de 1.000 anúncios até 10 s, em páginas de 100, sem rede e sem computação textual na transação de escrita.
**Constraints**: Sem serviços pagos, LLM ou coleta nova; GET sem comandos; CSRF preservado; sem banco pessoal em testes; sem SQL nativo fora das migrações.
**Scale/Scope**: Uma linha de critérios e uma avaliação corrente por vaga; até 1.000 anúncios no benchmark controlado. Não promete interpretação linguística universal nem multi-instância.

## Constitution Check

| Princípio | Antes da pesquisa | Após desenho |
| --- | --- | --- |
| I — Ecossistema | PASS: reutilizar stack existente | PASS: Validation, JPA/JPQL, TransactionTemplate do WriteExecutor e Query; sem biblioteca nova |
| II — Responsabilidades | PASS: regras no domínio | PASS: avaliador puro; casos de uso compartilhados; controllers só adaptam |
| III — Contratos | PASS: contratos explícitos necessários | PASS: DTOs/enums e validação de JSON nas fronteiras; contrato em contracts/compatibility-api.md |
| IV — Erros | PASS: manter envelope existente | PASS: códigos de conflito/falha; estado desatualizado; sem mensagens externas sensíveis |
| V — Concorrência | PASS: definir versão e chave | PASS: reserva por perfil/versão, commit condicional de evidência e versão; um processo |
| VI — Persistência | PASS: SQLite e escritor existentes | PASS: Flyway, JPA, lotes curtos, recuperação de execução interrompida |
| VII — Controle | PASS: fontes como dados | PASS: sem ações externas; evidência e incerteza explícitas; GET só leitura |
| VIII — Evidência | PASS: testes proporcionais previstos | PASS de desenho; testes ainda não executados; revisão documental obrigatória |

Não há exceção técnica proposta. PASS registra conformidade do desenho, não do legado. Os requisitos globais de PostgreSQL/pnpm/stack nova não substituem SQLite/npm e contratos já adotados pelo projeto.

## Project Structure

### Documentation (this feature)

```text
specs/004-respeitar-tecnologias-perfil/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/compatibility-api.md
└── checklists/requirements.md
```

`tasks.md` será criado por `$speckit-tasks`.

### Source Code (repository root)

```text
backend/src/main/java/dev/curriculotron/
├── opportunities/domain/                 # JobFactExtractor, avaliador por eixo, RuleBasedRanker
├── opportunities/application/            # avaliação, reavaliação, DailySelectionService, OpportunityService
├── opportunities/application/port/out/   # critérios/evidências/avaliações acessados por portas
├── opportunities/adapter/in/web/         # comando/status e DTOs de compatibilidade
├── opportunities/adapter/out/persistence/# entidades, JPA/JPQL e projeções
├── sources/                              # JobSource, adapters, SearchService; criteria web/application
├── sources/adapter/out/persistence/       # SearchCriteriaEntity e adapters existentes
└── shared/                               # WriteExecutor, AppClock e HomeController existentes
backend/src/main/resources/db/migration/   # migração aditiva, sem alterar anteriores
backend/src/test/java/                    # domínio, MVC, concorrência e SQLite real temporário
frontend/src/api/                          # contratos tipados e parsing de respostas
frontend/src/pages/                        # Profile, Opportunities, OpportunityDetail, Home
frontend/src/components/                   # OpportunityCard e feedback acessível
frontend/tests/unit/
frontend/tests/e2e/
```

**Structure Decision**: Manter organização real por portas/adaptadores. Introduzir somente portas com fronteira concreta, sem interface para todo serviço. A aplicação de oportunidades não importa entidades JPA de sources. A nova escrita de critérios fica na feature sources, que já possui a leitura, sem construir o restante do perfil fora deste escopo.

## Phase 0 — Research

Pesquisa em [research.md](research.md) resolve: ausência de PUT criteria, Java fixo e substituição artificial de cargo, score fraco indistinguível de incompatibilidade, perda de descrição após 4.000 caracteres, completude por fonte, reavaliação e corridas. Recursos de persistência e invalidação conferidos em documentação oficial; não se adiciona dependência.

## Phase 1 — Design

1. Avaliação pura com eixos `matched|mismatched|unknown|unrestricted`, evidência e códigos de motivo; `unrestricted` só para tecnologias vazias. Match não equivale a experiência comprovada.
2. Cargo configurável e tecnologia avaliados separadamente. Política contextual conservadora detalhada na pesquisa. Score e filtros de remuneração/modalidade continuam depois da avaliação; não usar apenas fit=weak para decidir presença secundária.
3. Guardar texto/contexto por fonte, completude explícita e revisão de evidência. Legado não recebe completude completa inferida. Refetch atualiza evidência e avaliação mesmo quando dedupe retorna vaga existente.
4. GET/PUT criteria tipados com versão otimista; PUT grava critérios e marca atualização pendente, sem executar coleta ou reavaliar como efeito de GET.
5. POST de reavaliação síncrono e limitado ao conjunto armazenado: reserva curta, leitura/avaliação fora da escrita, persistência em lotes de 100 e finalização condicionada. Sem nova fila, worker ou agendamento; recuperação ao reiniciar marca execução interrompida como falha revisável. Detalhes em data-model/contrato.
6. Listas e início exibem só avaliações atuais, sem mismatched. Eixos unknown ficam apenas na secundária identificada. Enquanto pendente/falha, ocultar recomendações desatualizadas e mostrar aviso global, sem apagá-las.
7. Manter consulta histórica selected(date) sem filtro para contar consumo. Consulta de exibição e consumo tornam-se operações distintas. Home expõe count visível e consumedCount histórico.
8. Frontend salva critérios, oferece “Atualizar avaliação” e invalida criteria, compatibility-status, opportunities, opportunity e home. Não reimplementar classificador no navegador.

### Compatibilidade dos contratos

A [spec 001](../001-assistente-busca-emprego/spec.md) FR-005 passa a definir “restantes” como vagas sem incompatibilidade demonstrada. RB-001 permanece perfil inicial editável. Atualizar esses trechos e [panel-api.md](../001-assistente-busca-emprego/contracts/panel-api.md) na implementação, conforme contrato desta feature; atualizar o aviso antigo sobre ausência de constituição apenas no trecho afetado. Preservar envelope de lista, campos de salário/fontes e erros. Acrescentar metadados de avaliação; não converter a lista em array puro. Contratos de salário e candidatura não mudam.

### Sequência e verificação previstas

Fundação de evidências/critérios e migração → avaliador e matriz de anúncios → integração de ingestão/listas → comando e recuperação → UI e cenários ponta a ponta. Matriz com pelo menos vinte anúncios cobre FR-001–007/SC-001–003. SQLite/concorrência e E2E cobrem FR-008–010/SC-004–005. Revisor por tarefa e correções antes do commit, conforme AGENTS.md.

Checks e comandos em [quickstart.md](quickstart.md). Não declarar CI ou revisão no Git ativos sem verificar configuração e execução; existem workflows backend-ci.yml/frontend-ci.yml, mas não foram executados nesta fase. Não ampliar esta entrega para reforma geral do CI.

### Validação desta fase

Revisão documental independente concluída em 2026-11-15: APPROVED após correção de dois achados MAJOR aceitos — mínimos salariais estritamente positivos conforme ObjectiveFilter e separação de senioridade da compatibilidade funcional para preservar RB-004/acceptJuniorIfJustified. Cenários de regressão registrados no quickstart. Conferidos sete artefatos Markdown quanto a links locais, placeholders e espaços finais, sem falhas. Nenhum teste funcional, migração ou benchmark foi executado. Não há extensions.yml nem hooks before_plan/after_plan a executar. Plano apto para geração de tarefas.

## Complexity Tracking

Nenhuma violação constitucional ou camada extra justificada. Estado persistido de reavaliação e revisão de evidência são necessários para distinguir falha/atualidade e impedir publicação com critérios ou texto antigos. Não há fila nova nem garantia distribuída.
