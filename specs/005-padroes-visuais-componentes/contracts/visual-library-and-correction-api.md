# Contratos HTTP e de Componentes: Padrões Visuais, Correção de Evidências e Estados

**Feature**: `005-padroes-visuais-componentes`  
**Date**: 2026-11-16  
**Base API**: [panel-api.md](file:///C:/Users/Diuliano/Documents/curriculotron-2000/specs/001-assistente-busca-emprego/contracts/panel-api.md)

Todos os endpoints seguem a sessão/CSRF do Spring Security (incluindo o header `X-CSRF-Token` em mutações `POST`) e o envelope de erro padrão `ApiErrorBody` (`code`, `message`, `details`). Não há `ResponseEntity<?>`, `Object` ou mapas abertos nos contratos HTTP.

---

## 1. Endpoints Novos e Atualizados (Backend Spring MVC)

### 1.1 `POST /api/opportunities/{id}/corrections` *(novo)*

Aplica uma ou mais correções manuais diretamente a partir do bloco de evidência no detalhe da vaga, preservando `opportunity_source` e `opportunity_evidence` intactos e gravando autoria humana (`"você"`) + timestamp em `opportunity_correction` e `activity_log`.

- **Headers**:
  - `Content-Type: application/json`
  - `X-CSRF-Token: <token>`
  - `Idempotency-Key: <uuid>` *(recomendado e enviado pelo cliente `post()`; replay com mesmo payload retorna `200 OK`, conflito com payload distinto retorna `422 idempotency_key_reused` via `IdempotencyFilter`)*
- **Request DTO (`OpportunityCorrectionRequest`)**:

```json
{
  "title": "Desenvolvedor(a) Full Stack · Java/Angular",
  "companyName": "SAP Labs",
  "location": "São Leopoldo, RS",
  "workMode": "hybrid",
  "contractType": "clt",
  "salary": {
    "min": 9500,
    "max": 12000,
    "unit": "monthly"
  },
  "clearSalary": false,
  "availability": "open",
  "deadlineAt": "2026-11-30",
  "publishedAt": "2026-11-11",
  "reason": "Valor confirmado no edital oficial da empresa."
}
```

- **Regras de validação na fronteira (Jakarta Bean Validation + domínio)**:
  - Pelo menos um campo efetivamente corrigível ou `clearSalary = true` deve estar presente e produzir alteração válida (`@AssertTrue` ou validação explícita de domínio retornando `400 invalid_correction`).
  - `title`: opcional; se informado, `@NotBlank`, `@Size(max = 240)`.
  - `companyName`: opcional; se informado, `@NotBlank`, `@Size(max = 160)`.
  - `location`: opcional; se informado, `@Size(max = 160)`.
  - `workMode`: enum fechado `remote | hybrid | onsite | unknown`.
  - `contractType`: enum fechado `clt | pj | other | unknown`.
  - `availability`: enum fechado `open | closed | unconfirmed`.
  - `salary`: objeto opcional (`@Valid`); quando presente e `clearSalary != true`:
    - `min` e `max`: `BigDecimal >= 0` (`@DecimalMin("0")`) com no máximo 9 dígitos inteiros e 2 casas decimais (`@Digits(integer = 9, fraction = 2)`, espelhado no domínio), com `min <= max` quando ambos informados; ao menos um entre `min` ou `max` presente.
    - `unit`: enum fechado `monthly | hourly | yearly | unknown`.
  - `deadlineAt` / `publishedAt`: data ISO `YYYY-MM-DD` (ou vazio/nulo quando explicitamente limpo por enum/flag se suportado).
  - `reason`: opcional, `@Size(max = 500)`.
- **Respostas**:
  - `200 OK` -> retorna `OpportunityDetailResponse` atualizado (já contendo `corrections` e `timeline`).
  - `400 Bad Request` -> `validation_failed` (violação de Bean Validation, ex.: tamanho, `@Digits`, salário negativo), `malformed_request` (JSON inválido, enum desconhecido, data fora de `YYYY-MM-DD` ou token JSON inesperado em data) ou `invalid_correction` (regra de domínio: nenhum dado alterado, salário incoerente, `clearSalary` sem salário etc.).
  - `404 Not Found` (`not_found`) -> vaga inexistente.
  - `409 Conflict` (`opportunity_merged`) -> a vaga foi fundida em outra; a mensagem indica a vaga sobrevivente, onde a correção deve ser feita.
  - `422 Unprocessable Entity` (`idempotency_key_reused`) -> reutilização de `Idempotency-Key` com payload diferente.
- **Datas (`deadlineAt` / `publishedAt`)**: chave ausente mantém o valor; `null` explícito ou `""` limpa a data; texto precisa ser exatamente `YYYY-MM-DD`. Texto só com espaços, timestamps ISO, números, arrays, objetos e booleanos resultam em `400 malformed_request`.
- **Reavaliação e `source: "user_correction"`**: após corrigir `title`, a compatibilidade é reavaliada e `compatibilityEvidence[]` passa a conter uma entrada com `source: "user_correction"` (`sourceUrl: null`, `fetchedAt` = instante da correção, `field: "title"`), que a interface exibe como `fonte: título corrigido por você`. A evidência original (`opportunity_evidence`) permanece intacta; o título bruto da fonte é ignorado só na avaliação em memória, enquanto os sinais da descrição continuam valendo (podendo gerar `role_conflict` quando contradizem o título corrigido).

> **Emenda à spec `004`**: `source` em `compatibilityEvidence[]` deixa de referir-se apenas a fontes coletadas (`opportunity_source`) e passa a admitir o valor `user_correction`, com `sourceUrl` nulo por definição. Consumidores devem tratá-lo como proveniência humana, não como erro de link indisponível.

---

### 1.2 `POST /api/opportunities/{id}/external-visit` *(novo)*

Registra explicitamente quando a pessoa aciona `"Abrir vaga no site ↗"` para consultar o anúncio original, gerando entrada informativa na linha do tempo (`activity_log` com `action = "opportunity.external_opened"`).

- **Garantia crítica (Princípio VII)**: Este endpoint **nunca** altera `user_decision`, **nunca** cria `application` e só pode ser chamado via `POST` por ação direta do usuário (jamais durante `GET /api/opportunities/{id}`).
- **Request**: corpo vazio (sem payload obrigatório).
- **Headers**: `X-CSRF-Token`, `Idempotency-Key` opcional.
- **Respostas**:
  - `204 No Content` (`ResponseEntity<Void>`)
  - `404 Not Found` (`not_found`) -> vaga inexistente.

---

### 1.3 `GET /api/opportunities/{id}` *(estendido com retrocompatibilidade aditiva)*

Adiciona campos explícitos ao `OpportunityDetailResponse` existente de `origin/main`:

```json
{
  "seniority": {
    "label": "Pleno",
    "status": "inferred",
    "reason": "inferido: “3+ anos”"
  },
  "corrections": [
    {
      "id": "019a...",
      "fieldName": "salary_min",
      "previousValue": null,
      "correctedValue": "9500",
      "reason": "Confirmado com recrutamento",
      "authorLabel": "você",
      "correctedAt": "2026-11-16T14:20:00Z"
    }
  ],
  "timeline": [
    {
      "id": "src-...",
      "kind": "discovered",
      "actorLabel": "bot Busca de vagas",
      "description": "Encontrada em careers.sap.com.",
      "note": null,
      "at": "2026-11-14T10:33:00Z"
    },
    {
      "id": "act-...",
      "kind": "external_opened",
      "actorLabel": "você",
      "description": "Abriu o link da vaga.",
      "note": "isso não registra candidatura",
      "at": "2026-11-14T12:02:00Z"
    }
  ]
}
```

- `correctedFields` identifica os campos corrigidos pela pessoa usando os mesmos nomes de `corrections[].fieldName`. O detalhe, cada item de `GET /api/opportunities` e os cartões de `GET /api/home` retornam uma lista vazia quando não há correções.
- `seniority.status`: `'fact' | 'corrected_title' | 'inferred' | 'absent'`.
- `timeline[].kind`: `'discovered' | 'selected_today' | 'external_opened' | 'corrected' | 'discarded' | 'restored' | 'applied'`.
- Campos já existentes de `OpportunityDetailResponse` (`compatibility`, `compatibilityEvidence`, `compatibilityCriteria`, `facts`, `inferred`, `missing`, `sources`, `publishedAt`, `salaryReference`, `glassdoorUrl`) permanecem intactos e aditivamente estendidos.

---

### 1.4 `GET /api/home` *(estendido com retrocompatibilidade aditiva)*

Adiciona `pendingReviewItems` ao `HomeResponse` mantendo `reviewQueueCount`, `mailNeedingAttention` e `missedRoutines` intactos:

```json
{
  "pendingReviewItems": [
    {
      "id": "cand-...",
      "kind": "dedupe_candidate",
      "kindLabel": "DUPLICATA · revisão",
      "title": "Possível vaga duplicada: Itaú Unibanco",
      "summary": "Mesmo cargo e empresa detectados em Gupy e LinkedIn.",
      "targetRoute": "/revisao"
    }
  ]
}
```

- `pendingReviewItems[].kind`: `'dedupe_candidate' | 'review_item' | 'email_attention' | 'missed_routine'`.

---

### 1.5 `GET /api/task-runs` e `GET /api/task-runs/{id}` *(alinhamento de contrato TS)*

O backend `SearchController` já serializa `TaskRun` com `id`, `kind`, `state`, `pauseReason`, `sourcesConsulted`, `sourcesFailed`, `errorSummary`, `startedAt`, `finishedAt`, `createdAt`.
O cliente TypeScript (`frontend/src/api/panel.ts`) passa a tipar e validar em runtime:
- `sourcesConsulted: string[]`
- `sourcesFailed: string[]`
- `kind: string`
- `startedAt: string | null`
- `finishedAt: string | null`
- `createdAt: string`

Quando `state === 'completed'` e `sourcesFailed.length > 0`, a interface renderiza o estado `PARCIAL` exibindo `${sourcesConsulted.length} de ${sourcesConsulted.length + sourcesFailed.length} fontes consultadas` e os nomes das fontes com falha.

---

## 2. Contratos de Componentes Frontend (`frontend/src/components/` e `lib/`)

### 2.1 `SurfaceStateBlock` (6 estados oficiais)

| `state.kind` | Rótulo exibido | Estilo visual obrigatório | Ação suportada |
|---|---|---|---|
| `empty` | `VAZIO` | `.sk` + `background: var(--card)` | Link ou botão secundário opcional |
| `loading` | `CARREGANDO` | `.dsh` (`--d: var(--ink)`) + barra `.hatch` com borda `2px solid var(--ink)` | Progresso textual + largura proporcional quando houver `progress` |
| `partial` | `PARCIAL` | Borda `2px solid var(--ink)` + `background: var(--card)` | Contagem `X de Y` + motivo/próxima tentativa + ação opcional |
| `error` | `FALHA` | `.dsh` (`--d: var(--danger)`) + `background: var(--card)` + rótulo em `var(--danger)` | Botão `.btn.bt.bt-sec.dsh` ou link de recuperação |
| `stale` | `DESATUALIZADO` | `.hatch` (`--c: #E2DCCB`) + borda `2px solid #6B6B73` + cada linha com faixa `--paper`/`--card` | Timestamp real + atalho `Atualizar →` |
| `pending_review` | `PENDENTE DE REVISÃO` | `.hl` + borda `2px solid var(--ink)` | Link ou ação direta de decisão |

### 2.2 Marcadores Epistêmicos

- **Fato declarado (`FactItem`)**: renderiza `✓ {value}` em tinta sólida `--ink`; quando receber `profileMatch`:
  - `'matched'` -> sufixo `você tem` (`color: var(--ok)`, `#1F6B0E`, contraste >= 4,5:1);
  - `'missing_from_profile'` -> sufixo `não está no perfil` (`color: var(--danger)`, `#9A2A12`).
- **Inferência do bot (`InferenceItem` / `InferencePanel`)**: renderiza `~ {value}` com classe `.dsh` ou `.dsh-b` (`--d: #6B6B73`) e `({reason || 'inferido pelo sistema'})` em `--ink-muted`.
- **Ausência não informada (`AbsentValue`)**: renderiza `.hatch` (`--c: #C9C2AE`) envolvendo `<span>` com fundo `var(--paper)` ou `var(--card)` para legibilidade AA.
- **Evidência (`EvidenceBlock`)**: renderiza `Space Mono` com `fonte:`, `consultado:`, `publicada:` (ou `AbsentValue` quando ausente), última correção `"corrigido por você · <data>"` quando presente, e acionador clicável (`"Corrigir algum dado →"`).

### 2.3 Identidade Visual de Empresas (`frontend/src/lib/opportunity.ts`)

- `companyColor(name: string): string`:
  - Normaliza `name.trim().toLowerCase()`.
  - Retorna hex conferido apenas para `itaú`/`itau`, `sap`, `picpay`, `sicredi`, `bv`, `cwi`, `db`, `ci&t`.
  - Para qualquer outro valor (inclusive vazio ou não informado), retorna `GRAPHITE = '#6B6B73'` (emenda explícita à `FR-004` da especificação `003`).
- `companyInitials(name: string): string`:
  - Mantém o contrato existente (1 letra para siglas em maiúsculas; 2 letras da primeira palavra caso contrário; `'?'` quando vazio).
