# Implementation Plan: Cores dos cards por empresa

**Branch**: atual `main`; implementação deverá usar `feature/US-003-company-card-colors` | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-company-card-colors/spec.md`

## Summary

Completar a identidade de cor dos cards de vagas, preservando o rabisco, o fundo claro e os textos escuros. Ampliar a resolução central de cores por nome de empresa: correspondência exata normalizada para marcas conhecidas, paleta alternativa determinística para outras empresas e grafite para ausência. Reutilizar `OpportunityCard` e a variável CSS `--c`, sem serviço externo, persistência ou dependência nova.

## Technical Context

**Language/Version**: TypeScript ~6.0 com strict; React ^19.3.1; Vite ^8.3.6 (manifest atual, sem atualização proposta).

**Primary Dependencies**: React, React Router ^8.5.1, TanStack Query ^5.107.1; CSS existente. Nenhuma nova dependência.

**Storage**: nenhum armazenamento novo; cores derivadas do nome recebido, sem localStorage ou mudança no SQLite.

**Testing**: Vitest ^5.1.0, Testing Library, Playwright ^1.65.1 e @axe-core/playwright ^4.14.0 já instalados no projeto.

**Target Platform**: navegador desktop/mobile; larguras de aceitação de 360 e 1280 pixels.

**Project Type**: aplicação web em monorepo; mudança de apresentação no frontend.

**Performance Goals**: cálculo síncrono proporcional ao tamanho do nome, sem rede adicional, efeitos, timers ou renderização bloqueada por consulta de marca.

**Constraints**: identidade estável independente da posição/estado; contraste 4,5:1 em texto normal e 3:1 em texto grande; ações e foco preservados. Cor não comunica status sozinha.

**Scale/Scope**: um usuário por instalação; página Hoje e listas selection/secondary/discarded; seis empresas prioritárias e marcas já cobertas. Sem editor de cores, logos remotos, alteração de endpoints ou nova ordenação/filtro.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Antes da pesquisa | Após design / evidência |
| --- | --- | --- |
| I — Ecossistema existente | PASS | CSS, testes e utilitário existentes; nenhuma dependência nova |
| II — Responsabilidades | PASS | Resolução pura em lib; apresentação em componente/CSS; backend fora do escopo |
| III — Contratos e tipos | PASS | companyName continua string; contrato local explícito; nenhuma mudança HTTP |
| IV — Erros previsíveis | PASS | Ausência tem rótulo e neutro; empresa desconhecida tem alternativa; erros da API seguem o fluxo existente |
| V — Concorrência/idempotência | PASS | Sem nova operação de negócio ou estado mutável |
| VI — Persistência/lifecycle | PASS | Sem persistência, migração, SQL ou worker |
| VII — Segurança/proveniência | PASS | Sem coleta, envio ou credencial; cor alternativa não é anunciada como oficial |
| VIII — Simplicidade/evidência | PASS | Função pura, tabela e paleta; testes comportamentais e validação visual previstos |

Constituição 1.0.0 e guia de boas práticas prevalecem sobre recomendações genéricas de trocar stack. Usar npm conforme package-lock e scripts existentes; não migrar para pnpm nesta feature. Não há conflito com RB-005/NFR-005 da feature 001. Planejamento aprovado documentalmente não comprova implementação ou CI.

## Project Structure

### Documentation (this feature)

```text
specs/003-company-card-colors/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── company-card-colors.md
└── checklists/requirements.md
```

`tasks.md` será gerado por `$speckit-tasks`, fora desta etapa.

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── lib/opportunity.ts                  # resolver de cores existente
│   ├── components/OpportunityCard.tsx       # consumo de --c e rótulo ausente
│   ├── app/panel.css                       # destaques do card, se necessário
│   ├── index.css                           # rabisco/foco; preservar tokens globais
│   └── pages/{Home,Opportunities}.tsx       # consumidores; sem duplicar resolução
└── tests/
    ├── unit/company-colors.test.ts          # novo: identidade/normalização/fallback
    ├── unit/components.test.tsx             # ampliar comportamento dos cards
    └── e2e/frontend.spec.ts                 # ampliar fixtures e cenários isolados
```

**Structure Decision**: estender os pontos existentes; não reorganizar o frontend por feature nesta mudança. `Home`, detalhe da vaga e outros consumidores de `companyColor` herdarão a regra central; verificar regressão nesses usos sem ampliar seu escopo visual.

## Design e sequência de implementação

1. **US1**: substituir regex de prefixo por tabela de nomes normalizados exatos, com aliases explícitos e documentados. Preservar valores já usados por Itaú, SAP, PicPay e Sicredi; adicionar BV, CWI, DB e CI&T conforme pesquisa. Resolver desconhecidas com hash estável e paleta fixa. Testar empresas prioritárias, colisões permitidas e falsos positivos por prefixo.
2. **US3**: aplicar identidade no contorno/carimbo/hachura do card e tratar nome vazio como “Empresa não informada”. Preservar texto/fundo/foco; validar contraste e ações em ambas as larguras. Semântica textual continua independente da cor.
3. **US2**: provar estabilidade entre Hoje/listas e após descarte/restauração/recarregamento. Usar a ordenação/filtragem já disponível ou variação de ordem e subconjuntos nas fixtures; não criar controles ausentes. Cobrir correção da empresa.
4. Executar checks locais, revisão por agente focada em constituição/guia/contratos e corrigir achados impeditivos antes dos commits delimitados por task. Depois, push e PR conforme fluxo do projeto; conferir configuração/execução real da CI antes de declarar gate ativo. Este planejamento não conclui uma US.

## Complexity Tracking

Nenhuma violação constitucional ou exceção arquitetural identificada. Não é necessário criar API de temas, cadastro persistente de cores ou mecanismo de aliases aproximados.
