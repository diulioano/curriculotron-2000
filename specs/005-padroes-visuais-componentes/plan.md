# Implementation Plan: Alinhamento aos Padrões Visuais e Biblioteca de Componentes

**Branch**: `[005-padroes-visuais-componentes]` *(alinhar com `origin/main` antes da execução)* | **Date**: 2026-11-16 | **Spec**: [spec.md](file:///C:/Users/Diuliano/Documents/curriculotron-2000/specs/005-padroes-visuais-componentes/spec.md)  
**Input**: Feature specification from `/specs/005-padroes-visuais-componentes/spec.md`

## Summary

Consolidar os 6 estados de superfície (`VAZIO`, `CARREGANDO`, `PARCIAL`, `FALHA`, `DESATUALIZADO`, `PENDENTE DE REVISÃO`), os 4 padrões epistêmicos (`Fato declarado`, `Inferência do bot`, `Ausência não informada`, `Evidência`) e a hierarquia completa de componentes da biblioteca visual em todas as telas do painel React/TypeScript, tendo o arquivo local `design/Curriculotron 2000.html` exclusivamente como especificação visual ("o quê", nunca versionado nem empacotado). A entrega inclui também: emenda explícita à `FR-004` da especificação `003` para usar grafite neutro (`#6B6B73`) fora da lista de 8 marcas conferidas; correção manual direta de dados da vaga a partir da evidência (`POST /api/opportunities/{id}/corrections`) preservando histórico/proveniência em tabela append-only (`V14`); linha do tempo da vaga com registro explícito de abertura de link (`POST /api/opportunities/{id}/external-visit`, sem criar candidatura); exposição de progresso parcial (`sourcesConsulted`/`sourcesFailed`), itens individuais em `"Precisa de você"`, nível no resumo, correspondência de requisitos com o perfil (`você tem` / `não está no perfil`) e padronização visual em estado atual das áreas `/bots` e `Preparar`.

## Technical Context

**Language/Version**: Java 25 (backend) · TypeScript `~6.0` (`strict: true`, frontend)  
**Primary Dependencies**:
- Backend: Spring Boot `4.1.2` (`spring-boot-starter-webmvc`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`), Hibernate Community Dialects (SQLite), Flyway (`spring-boot-starter-flyway`), `sqlite-jdbc:3.54.1.0`, `com.networknt:json-schema-validator:3.0.9`, BouncyCastle `1.87`.
- Frontend: React `19.3.1`, React Router `8.5.1`, TanStack Query `5.107.1`, Vite `8.3.6` (sem adição de bibliotecas externas de UI/ícones).  
**Storage**: SQLite local gerenciado por Flyway (última migração em `origin/main`: `V13__opportunity_compatibility.sql`; nova migração planejada: `V14__opportunity_corrections.sql`) e serialização de escrita pelo `WriteExecutor`.  
**Testing**:
- Backend: JUnit 5 (`./backend/gradlew.bat -p backend test integrationTest spotlessCheck`), com SQLite temporário real e WireMock `3.13.2`.
- Frontend: Vitest `5.1.0` + Testing Library (`@testing-library/react`, `user-event`), Playwright `1.65.1` + `@axe-core/playwright` `4.14.0`, ESLint `10.15.0`, Prettier `3.10.1`.  
**Target Platform**: Aplicação web local (LAN privada, uso pessoal), servida pelo Spring Boot (`frontend/dist` copiado via Gradle `copyFrontend`), responsiva para desktop (`1280px`) e celular (`390px`), com fontes locais (`Kalam`, `Permanent Marker`, `Space Mono`).  
**Project Type**: Aplicação web monolítica modular (`backend/` Java Spring Boot + `frontend/` React SPA).  
**Performance Goals**: Renderização imediata dos estados padronizados sem dependências externas de rede/fontes; escrita síncrona serializada curta no SQLite (< 50 ms para correção manual ou registro de abertura externa).  
**Constraints**:
- `design/` e `design/Curriculotron 2000.html` NÃO podem ser versionados nem incluídos em `frontend/dist` ou `build/resources/main/static`.
- Contraste mínimo WCAG AA (4,5:1 texto normal; 3:1 texto grande) em todos os cartões, faixas sobre hachuras e marcadores (`#1F6B0E`, `#9A2A12`, `#1B1B1F`, `#44444C`).
- Alvos interativos `>= 44px`, foco visível via teclado, `prefers-reduced-motion` respeitado, zero rolagem horizontal em `390px` e `1280px`.
- Pré-requisito de execução: sincronizar/rebasear o branch de trabalho com `origin/main` (`e9afc88`+) antes da implementação.  
**Scale/Scope**: 1 pessoa usuária por instalação; ~15 rotas no painel frontend, 2 novos endpoints `POST` (`corrections`, `external-visit`), extensões aditivas em `HomeResponse`, `OpportunityDetailResponse` e `TaskRun` TS, 1 nova migração Flyway (`V14`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Avaliação Pré-Design (Antes da Phase 0) e Pós-Design (Após Phase 1)

| Princípio | Verificação Pré-Design | Verificação Pós-Design (após Phase 1) | Status |
|---|---|---|---|
| **I. Ecossistema antes de infra própria** | Reutilizar CSS/SVG local, TanStack Query, Jakarta Validation, Spring Security e `WriteExecutor` sem novas dependências. | Confirmado: nenhuma biblioteca nova no `package.json` ou `build.gradle.kts`; reuso de `IdempotencyFilter`, `ActivityLogService`, `AppClock`, `WriteExecutor` e `AssessOpportunityService`. | PASS |
| **II. Responsabilidades separadas e casos de uso** | Regras de correção/linha do tempo ficam no domínio/aplicação de `opportunities`; controllers apenas adaptam DTOs HTTP. | Confirmado em `data-model.md` e `contracts/`: `OpportunityController` apenas valida e delega ao caso de uso de correção/visita; `AssessOpportunityService` existente é reutilizado. | PASS |
| **III. Contratos explícitos e validação nas fronteiras** | Sem `Object`, `ResponseEntity<?>` ou mapas nos novos contratos; TS `strict` com parsers runtime. | Confirmado em `contracts/visual-library-and-correction-api.md`: DTOs nomeados (`OpportunityCorrectionRequest`, `OpportunityDetailResponse`, `HomeResponse`), enums fechados, Jakarta Validation e `ResponseEntity<Void>` / `@ResponseStatus(NO_CONTENT)`. | PASS |
| **IV. Erros previsíveis e diagnóstico seguro** | Exceções de domínio traduzidas em `ApiErrorBody` sem vazar dados sensíveis ou usar `NoSuchElementException` como contrato novo. | Confirmado: validações estruturais (`400 invalid_correction` / `invalid_input`), ausência tratada explicitamente (`404 not_found`), conflito idempotente (`422 idempotency_key_reused`), logs via SLF4J. | PASS |
| **V. Concorrência e idempotência orientadas a requisitos** | Identificar recurso/alcance de escrita no SQLite e uso de `Idempotency-Key` em mutações repetíveis. | Confirmado: escrita única serializada pelo `WriteExecutor` (alcance processo/instância local única) + `IdempotencyFilter` já ativo cobrindo `POST /api/opportunities/{id}/corrections` contra replay acidental. | PASS |
| **VI. Persistência e ciclo de vida definido** | Evolução por Flyway; transações curtas; reavaliação pesada fora da transação de escrita; verificação em SQLite real. | Confirmado: `V14` cria tabela append-only `opportunity_correction` com triggers `BEFORE UPDATE/DELETE`; atualização + log em transação curta no `WriteExecutor`; `AssessOpportunityService` executado após commit. | PASS |
| **VII. Segurança, proveniência e controle da pessoa** | Sessão/CSRF intactos; `GET` sem efeito colateral; abertura externa nunca registra candidatura; fatos × inferências × ausências preservados. | Confirmado: visita externa usa `POST .../external-visit` explícito (nunca `GET` e nunca altera `user_decision`/`application`); `opportunity_source` e `opportunity_evidence` permanecem intactos após correção humana; `003` `FR-004` explicitamente conciliado/emendado (Q1-C). | PASS |
| **VIII. Simplicidade e evidência na entrega** | Menor mecanismo suficiente (sem criador de bots nem simulador de entrevista neste ciclo); testes reais cobrindo contratos, SQLite e `@axe-core/playwright`. | Confirmado: `/bots` e `Preparar` restritos à padronização visual (Q2-A); `quickstart.md` define suítes concretas (`spotlessCheck`, `test`, `integrationTest`, `vitest`, `playwright` + `axe-core` em `390px`/`1280px`). | PASS |

## Project Structure

### Documentation (this feature)

```text
specs/005-padroes-visuais-componentes/
├── plan.md                                             # Este plano ($speckit-plan)
├── research.md                                         # Phase 0: decisões arquiteturais e análise de lacunas
├── data-model.md                                       # Phase 1: esquema SQLite V14, projeções e tipos UI
├── quickstart.md                                       # Phase 1: guia executável de validação
├── contracts/
│   └── visual-library-and-correction-api.md            # Phase 1: contratos HTTP, DTOs e contratos de componentes
└── checklists/
    └── requirements.md                                 # Checklist de qualidade da especificação
```

### Source Code (repository root)

*Estrutura concreta alinhada à base `origin/main` (a ser sincronizada antes da implementação):*

```text
backend/
├── src/main/resources/db/migration/
│   └── V14__opportunity_corrections.sql                # Nova tabela append-only opportunity_correction + triggers + índice
├── src/main/java/dev/curriculotron/
│   ├── opportunities/
│   │   ├── domain/
│   │   │   ├── CorrectableOpportunityField.java        # Enum fechado dos campos corrigíveis
│   │   │   ├── OpportunityCorrectionEntry.java         # Modelo de correção humana preservando anterior/novo/motivo
│   │   │   ├── OpportunityTimelineEvent.java           # Modelo de evento da linha do tempo
│   │   │   └── SeniorityAssessment.java                # Nível (fact | corrected_title | inferred | absent) + justificativa
│   │   ├── application/
│   │   │   ├── OpportunityCorrectionService.java       # Caso de uso de correção manual + invalidação/reavaliação
│   │   │   ├── OpportunityTimelineService.java         # Agregação cronológica de fontes, seleção, correções e activity_log
│   │   │   ├── OpportunityService.java                 # Registro explícito de abertura externa sem candidatura
│   │   │   └── port/out/
│   │   │       └── OpportunityCorrectionRepository.java  # Porta de leitura/escrita append-only de correções
│   │   └── adapter/
│   │       ├── in/web/
│   │       │   ├── OpportunityController.java          # POST /{id}/corrections, POST /{id}/external-visit, GET /{id} estendido
│   │       │   ├── OpportunityCorrectionRequest.java   # DTO validado (@Valid, enums fechados, BigDecimal >= 0)
│   │       │   └── OpportunityDetailResponse.java      # Extensão aditiva: seniority, corrections, timeline
│   │       └── out/persistence/
│   │           ├── OpportunityCorrectionEntity.java      # Entidade JPA mapeando opportunity_correction
│   │           ├── OpportunityCorrectionJpaRepository.java
│   │           └── OpportunityCorrectionPersistenceAdapter.java
│   └── shared/web/
│       └── HomeController.java                         # Extensão aditiva: pendingReviewItems no HomeResponse
└── src/test/ & src/integrationTest/                    # Testes unitários, WebMVC/CSRF e SQLite real (Flyway V14)

frontend/
├── src/
│   ├── index.css                                       # Tokens (--ok #1F6B0E, texturas dos 6 estados, prefers-reduced-motion)
│   ├── app/
│   │   ├── panel.css                                   # Estilos padronizados de estados, botões, campos e responsividade 390px
│   │   ├── App.tsx                                     # Loading inicial padronizado com SurfaceStateBlock
│   │   └── Layout.tsx                                  # Navegação e rodapé com reduced-motion e contraste AA
│   ├── lib/
│   │   ├── opportunity.ts                              # Emenda FR-004 (003): BRANDS + GRAPHITE (#6B6B73), sem hash cromático
│   │   └── opportunity.test.ts                         # Testes da regra de cores e iniciais
│   ├── api/
│   │   ├── panel.ts                                    # TaskRun completo (sourcesConsulted/Failed) e ações de correção/visita
│   │   ├── compatibility.ts                            # Parser atualizado de HomeData (pendingReviewItems)
│   │   └── compatibility-detail.ts                     # Parser atualizado de OpportunityDetail (seniority, corrections, timeline)
│   ├── components/
│   │   ├── SurfaceState.tsx                            # Os 6 estados oficiais (VAZIO, CARREGANDO, PARCIAL, FALHA, DESATUALIZADO, PENDENTE DE REVISÃO)
│   │   ├── Epistemic.tsx                               # FactItem, InferenceBox, AbsentValue, EvidenceBlock
│   │   ├── OpportunityCorrectionForm.tsx               # Formulário direto inline a partir da evidência
│   │   ├── Panel.tsx                                   # QueryState e Empty delegando para SurfaceState
│   │   ├── DataAsOf.tsx                                # Ausência e estado DESATUALIZADO padronizados
│   │   ├── SalaryEstimate.tsx                          # Estimativa como inferência (~ + .dsh + motivo) e faixa ausente hachurada
│   │   └── OpportunityCard.tsx                         # Cartão com marcadores epistêmicos e contraste AA
│   └── pages/
│       ├── Home.tsx                                    # Precisa de você com itens individuais + cards desktop/mobile
│       ├── OpportunityDetail.tsx                       # Nível, match de perfil nos requisitos, timeline, evidência editável e Preparar padronizado
│       ├── SalaryReferences.tsx                        # Adoção de PageHeader, DESATUALIZADO/VAZIO e marcadores epistêmicos
│       ├── Activities.tsx                              # Estado PARCIAL (sourcesConsulted/Failed), FALHA e CARREGANDO
│       ├── Integrations.tsx                            # Estados PARCIAL, FALHA e ausências hachuradas
│       ├── Placeholder.tsx                             # /bots com PageHeader + estado VAZIO padronizado
│       └── Opportunities.tsx, Applications.tsx, ApplicationDetail.tsx, ReviewQueue.tsx, DedupeReview.tsx, Profile.tsx, Criteria.tsx, Companies.tsx, Privacy.tsx
└── tests/ & e2e/                                       # Vitest + Playwright + @axe-core/playwright (390px e 1280px)
```

**Structure Decision**: Manter exatamente a arquitetura modular existente (`dev.curriculotron.<contexto>` com `domain`, `application`, `adapter` no backend + SPA Vite/React/TanStack Query em `frontend/`), evitando projetos extras, camadas genéricas ou abstrações especulativas.

## Complexity Tracking

Nenhuma violação à Constituição foi introduzida; nenhuma justificativa de exceção necessária.
