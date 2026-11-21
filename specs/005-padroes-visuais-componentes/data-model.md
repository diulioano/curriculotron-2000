# Modelo de Dados: Alinhamento aos Padrões Visuais e Biblioteca de Componentes

**Feature**: `005-padroes-visuais-componentes`  
**Date**: 2026-11-16  
**Spec**: [spec.md](file:///C:/Users/Diuliano/Documents/curriculotron-2000/specs/005-padroes-visuais-componentes/spec.md)

## 1. Visão Geral

Este modelo cobre:
1. **Entidades persistidas no backend (SQLite + Flyway `V14`)** para correções manuais de evidência/campos de vaga preservando a origem original e alimentando a linha do tempo do detalhe da vaga;
2. **Projeções de leitura no backend** para a linha do tempo da vaga, progresso parcial de `TaskRun` e itens acionáveis de revisão na página inicial;
3. **Tipos de apresentação no frontend (TypeScript `strict`)** para os 6 estados de superfície, 4 marcadores epistêmicos e identidade visual de empresas.

---

## 2. Persistência no Backend (SQLite / Flyway `V14`)

### 2.0 Coluna `opportunity.deadline_at`

A `V14` também adiciona o prazo de inscrição como dado persistido (antes sempre nulo), para que possa ser corrigido:

```sql
ALTER TABLE opportunity ADD COLUMN deadline_at TEXT
  CHECK(deadline_at IS NULL OR deadline_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]');
```

O `CHECK` garante que somente datas ISO `YYYY-MM-DD` (ou `NULL`) sejam gravadas, espelhando a validação da API.

### 2.1 `OpportunityCorrection` (`opportunity_correction`)

Tabela **append-only** (com triggers SQLite bloqueando `UPDATE` e `DELETE`, seguindo a convenção de `activity_log` em `V1__base.sql`) que registra cada alteração feita manualmente pela pessoa a partir do bloco de evidência no detalhe da vaga.

| Campo | Tipo SQLite | Restrições | Descrição |
|---|---|---|---|
| `id` | `TEXT` | `PRIMARY KEY` | UUIDv7 gerado via `Ids.newId()` |
| `opportunity_id` | `TEXT` | `NOT NULL REFERENCES opportunity(id)` | Vaga corrigida |
| `field_name` | `TEXT` | `NOT NULL CHECK(field_name IN ('title','company_name','location','work_mode','contract_type','salary_min','salary_max','salary_unit','availability','deadline_at','published_at'))` | Campo corrigido na vaga |
| `previous_value` | `TEXT` | `NULL` permitido | Valor anterior serializado em texto para auditoria |
| `corrected_value` | `TEXT` | `NULL` permitido | Novo valor aplicado em `opportunity` (ou `NULL` quando limpo explicitamente) |
| `reason` | `TEXT` | `CHECK(reason IS NULL OR length(reason) <= 500)` | Justificativa opcional informada pela pessoa |
| `author` | `TEXT` | `NOT NULL DEFAULT 'user' CHECK(author = 'user')` | Autoria humana explícita |
| `corrected_at` | `TEXT` | `NOT NULL` | Instante UTC ISO-8601 da correção (`AppClock`) |

- **Índices**:
  - `CREATE INDEX opportunity_correction_opp_at ON opportunity_correction (opportunity_id, corrected_at DESC);`
- **Triggers de imutabilidade**:
  - `opportunity_correction_no_update` (`BEFORE UPDATE` -> `RAISE(ABORT, 'opportunity_correction e append-only')`)
  - `opportunity_correction_no_delete` (`BEFORE DELETE` -> `RAISE(ABORT, 'opportunity_correction e append-only')`)
- **Invariantes**:
  1. `opportunity_source` e `opportunity_evidence` originais **nunca** são removidos ou alterados por uma correção manual.
  2. Quando `salary_min` ou `salary_max` forem atualizados, a invariante `salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max` de `V6__opportunities.sql` continua garantida pelo domínio antes da escrita no banco.
  3. Toda correção grava também um evento `opportunity.corrected` em `activity_log` (`target_type = 'opportunity'`, `target_id = opportunity_id`, `author = 'user'`).
  4. **Reavaliação**: quando a correção altera campos de negócio/conteúdo — `title`, `company_name`, `location`, `work_mode`, `contract_type`, `salary_min`/`salary_max`/`salary_unit` ou `availability` — a revisão de evidência (`evidence_revision`) da vaga é incrementada e o `AssessOpportunityService` reavalia a compatibilidade fora da transação de escrita, mantendo consistência com `004`. Correções apenas de datas (`deadline_at`, `published_at`) **não** invalidam a avaliação. Se a reavaliação falhar, a correção permanece válida e a compatibilidade fica como desatualizada.
  5. **Precedência da correção humana**: para cada campo com ao menos uma linha em `opportunity_correction`, o valor corrigido prevalece sobre re-ingestões futuras. A coleta continua atualizando normalmente os campos *não corrigidos*; `title`, `company_name`, `location` e `work_mode` corrigidos não são sobrescritos por `refreshDetails`, e `published_at` corrigido não é sobrescrito por `PublicationStore.record`. `opportunity_source` e `opportunity_evidence` seguem registrando o dado bruto de cada coleta, sem contaminação por correções.
  6. **Título corrigido na avaliação**: quando existe correção de `title`, `AssessmentInputService` monta, **somente em memória**, a lista de evidências da avaliação — as evidências originais com `title = null` (para que o título antigo da fonte não gere falso `role_mismatch`/`role_conflict`; descrição, local e demais sinais permanecem) mais uma evidência sintética `user_correction` (`id = "user-correction-<opportunityId>"`, `sourceId = null`, `title`/`location` atuais da vaga, `text = null`, `completeness = unknown`, `fetchedAt` = instante da correção mais recente do título, `sourceUrl = null`). Nada disso é gravado em `opportunity_evidence`.
  7. **Vaga fundida**: uma vaga com `merged_into IS NOT NULL` não pode ser corrigida; a tentativa é recusada com `409 opportunity_merged` (sem gravar correção nem `activity_log`), pois a correção deve ser feita na vaga sobrevivente.

---

## 3. Modelos e Projeções de Aplicação (Java)

### 3.1 `OpportunityCorrectionEntry` (Domínio / Projeção)

Representa uma correção humana exibida no bloco de evidência e no histórico de campo da vaga.

- `id: String`
- `fieldName: CorrectableOpportunityField` (`enum`, valor persistido e exposto em snake_case: `title`, `company_name`, `location`, `work_mode`, `contract_type`, `salary_min`, `salary_max`, `salary_unit`, `availability`, `deadline_at`, `published_at`)
- `previousValue: String | null`
- `correctedValue: String | null`
- `reason: String | null`
- `authorLabel: String` (ex.: `"você"`)
- `correctedAt: Instant`

### 3.2 `OpportunityTimelineEvent` (Projeção de leitura)

Combina eventos da vaga em ordem cronológica crescente (ou decrescente padronizada no detalhe) sem criar tabela redundante:
- **Descoberta / coleta**: derivada de `opportunity_source` (`actor = "bot Busca de vagas"` ou `"entrada manual"`, descrição da fonte e data/hora).
- **Seleção diária**: derivada de `daily_selection` quando presente.
- **Correções (`corrected`)**: derivadas **somente** de `opportunity_correction` (uma entrada por operação de correção, com resumo dos campos). O evento `opportunity.corrected` do `activity_log` é ignorado na projeção para não duplicar a correção.
- **Candidatura (`applied`)**: derivada da tabela `application` (o `activity_log` registra a candidatura sob o alvo `application`, não sob a vaga), sem duplicar com `activity_log`.
- **Demais ações da pessoa**: derivadas de `activity_log` para `target_type = 'opportunity'` e `target_id = id`:
  - `opportunity.external_opened` -> `"Abriu o link da vaga."` + nota `"(isso não registra candidatura)"`;
  - `opportunity.discarded` / `opportunity.restored`.
- **Campos**:
  - `id: String`
  - `kind: TimelineEventKind` (`discovered`, `selected_today`, `external_opened`, `corrected`, `discarded`, `restored`, `applied`)
  - `actorLabel: String` (ex.: `"bot Busca de vagas"`, `"você"`, `"sistema"`)
  - `description: String`
  - `note: String | null` (ex.: `"isso não registra candidatura"`)
  - `at: Instant`

### 3.3 `PendingReviewItem` em `HomeController.HomeResponse`

Permite à seção `"Precisa de você"` exibir cada pendência individual com o estado `PENDENTE DE REVISÃO`, além de `reviewQueueCount`:
- `id: String`
- `kind: PendingReviewKind` (`dedupe_candidate`, `review_item`, `email_attention`, `missed_routine`)
- `kindLabel: String` (ex.: `"DUPLICATA · revisão"`, `"FILA DE REVISÃO"`)
- `title: String`
- `summary: String`
- `targetRoute: String` (ex.: `"/revisao"`, `"/atividades"`)

### 3.4 `SeniorityAssessment` (Nível no Resumo do Detalhe)

Projeção explícita para preencher o bloco `NÍVEL` no detalhe da vaga conforme os 4 padrões epistêmicos:
- `label: String | null` (ex.: `"Júnior"`, `"Pleno"`, `"Sênior"`)
- `status: EpistemicKind` (`fact` quando explícito nos fatos/título oficial; `corrected_title` quando o nível vem do título corrigido pela pessoa; `inferred` quando deduzido de pista como `"3+ anos"`; `absent` quando não há pista segura)
- `reason: String | null` (ex.: `"inferido: “3+ anos”"`)

---

## 4. Modelos de Apresentação no Frontend (TypeScript `strict`)

### 4.1 `SurfaceState` (6 estados padronizados)

União discriminada usada pelo componente compartilhado `SurfaceStateBlock` em todas as rotas:

- `empty`: `{ kind: 'empty'; title: string; detail?: string; action?: SurfaceAction }` -> `.sk` + `--card` + rótulo `VAZIO`
- `loading`: `{ kind: 'loading'; title: string; progress?: { current: number; total: number } | null; detail?: string }` -> `.dsh` (`--d: var(--ink)`) + rótulo `CARREGANDO` + barra `.hatch`
- `partial`: `{ kind: 'partial'; title: string; respondedCount: number; totalCount: number; detail: string; action?: SurfaceAction }` -> borda sólida `2px solid var(--ink)` + `--card` + rótulo `PARCIAL`
- `error`: `{ kind: 'error'; title: string; detail: string; action?: SurfaceAction }` -> `.dsh` (`--d: var(--danger)`) + `--card` + rótulo `FALHA` em `var(--danger)`
- `stale`: `{ kind: 'stale'; title: string; lastSuccessAt: string | null; detail: string; action?: SurfaceAction }` -> `.hatch` (`--c: #E2DCCB`) + borda `#6B6B73` + rótulo `DESATUALIZADO` + faixas legíveis `--paper`/`--card`
- `pending_review`: `{ kind: 'pending_review'; kindLabel?: string; title: string; detail: string; action?: SurfaceAction }` -> `.hl` + borda `2px solid var(--ink)` + rótulo `PENDENTE DE REVISÃO`

### 4.2 `EpistemicDatum` (4 padrões epistêmicos)

- `fact`: `{ kind: 'fact'; value: string; profileMatch?: 'matched' | 'missing_from_profile' | null }`
- `inferred`: `{ kind: 'inferred'; value: string; reason: string }` (fallback obrigatório caso `reason` esteja vazio: `'inferido pelo sistema'`)
- `absent`: `{ kind: 'absent'; label: string }`
- `evidence`: `{ kind: 'evidence'; source: string; url?: string | null; fetchedAt: string; publishedAt?: string | null; correctedByUser?: { at: string; summary: string } | null; onCorrect?: () => void }`

### 4.3 `BrandIdentity` (Emenda à `FR-004` da especificação `003`)

- Mapa fechado `BRANDS` (8 marcas conferidas): `itaú`/`itau` (`#EC7000`), `sap` (`#0070F2`), `picpay` (`#21C25E`), `sicredi` (`#2F7D12`), `bv` (`#005CA9`), `cwi` (`#FF8A00`), `db` (`#F70773`), `ci&t` (`#FA5A50`).
- Regra de fallback: qualquer outra empresa ou empresa não informada recebe `GRAPHITE = '#6B6B73'`.
- Aplicação restrita a elementos decorativos (`--c` em `.sk`, `.hatch`, `.next-bar`, sublinhado zigue-zague e contorno de carimbo); textos de leitura permanecem em `--ink` / `--ink-soft` / `--ink-muted` / `--danger` / `--ok` (`#1F6B0E`).
