# Contrato — API privada do painel

Consumidor: SPA Vite/React servida pela mesma origem (`https://<host-lan>:8443`). Prefixo `/api`. JSON UTF-8.

Regras transversais:

- Todas as rotas exceto `POST /api/session` e `GET /api/health` exigem sessão válida; sem sessão ⇒ `401` com corpo sem dados privados (NFR-001). Arquivos estáticos da SPA não contêm dados.
- Mutações exigem cabeçalho `X-CSRF-Token` obtido em `GET /api/session`.
- Respostas de erro: `{ "error": { "code": "<slug>", "message": "<pt-BR>" } }`. Nunca incluir tokens, caminhos de credenciais ou stack traces.
- Toda mutação aceita `Idempotency-Key` opcional; repetição com a mesma chave devolve o mesmo resultado.
- Datas em ISO-8601 com offset.

## Sessão

| Método e rota | Descrição |
| --- | --- |
| `GET /api/health` | `{status}` — sem dados privados |
| `POST /api/setup` | Define senha no primeiro uso; `409` se já definida |
| `POST /api/session` | `{password}` ⇒ cookie de sessão; `429` após tentativas excessivas |
| `GET /api/session` | `{authenticated, csrfToken}` |
| `DELETE /api/session` | Logout |

## Início (FR-016)

`GET /api/home` ⇒
```json
{
  "dataAsOf": "…", "integrations": [{ "id": "openbot", "state": "ok", "lastSuccessAt": "…", "lastAttemptAt": "…" }],
  "selectionToday": { "localDate": "2026-11-14", "count": 3, "limit": 10, "items": [/* OpportunitySummary */] },
  "applicationsWithNextActions": [/* ApplicationSummary, ordenadas por prazo */],
  "mailNeedingAttention": [/* MailSummary */],
  "reviewQueueCount": 2,
  "missedRoutines": [{ "routineId": "…", "scheduledFor": "…" }]
}
```
`dataAsOf` reflete a última atualização real (US5-4); ausência de sucesso ⇒ `null`, nunca a hora atual.

## Vagas (FR-003–FR-006, FR-010)

| Rota | Descrição |
| --- | --- |
| `GET /api/opportunities?list=selection\|secondary\|discarded&date=` | Listas |
| `GET /api/opportunities/{id}` | Detalhe com `facts`, `inferred`, `missing` separados; `salary: null` ⇒ UI "salário a confirmar"; `sources[]` com `url`, `fetchedAt` |
| `POST /api/opportunities/{id}/discard` | Exclui de recomendações futuras |
| `POST /api/opportunities/{id}/restore` | Reversão explícita |
| `POST /api/opportunities/{id}/applied` | "Me candidatei" ⇒ cria `Application` (`confirmationOrigin: manual`); `409` se já existe |
| `POST /api/opportunities/manual` | `{url?, text?}` entrada manual |
| `POST /api/searches` | `{publishedFrom?: "YYYY-MM-DD", publishedTo?: "YYYY-MM-DD"}`; omitir datas usa os 30 dias anteriores; busca manual respeita teto diário; responde `202 {taskRunId}` |
| `GET /api/dedupe-candidates` / `POST /api/dedupe-candidates/{id}/merge\|reject` | Fusão só manual |

Quando uma fonte não informa a data de publicação, a oportunidade deve ser sinalizada com data desconhecida; não afirmar que ela está dentro do período solicitado. A rotina diária automática fica desabilitada por padrão.

Abrir o link da vaga é navegação do navegador; não há rota que registre candidatura por clique (US3-1).

## Candidaturas (FR-011)

| Rota | Descrição |
| --- | --- |
| `GET /api/applications` / `GET /api/applications/{id}` | Inclui `history[]` de `StatusChange` com `author`, `evidence` |
| `POST /api/applications/{id}/status` | `{toStatus, reason}` ⇒ `author: user` |
| `POST /api/applications/{id}/next-action` | `{text, dueAt?, duePrecision}` |
| `PUT /api/mail-associations/{gmailMessageId}` | Corrigir/definir vínculo manualmente |

## Revisão

`GET /api/review-items`, `POST /api/review-items/{id}/resolve` `{resolution}`.

## Gmail e rascunhos (FR-012–FR-015)

| Rota | Descrição |
| --- | --- |
| `POST /api/integrations/gmail/connect` | ⇒ `{authorizationUrl}` (fluxo loopback) |
| `DELETE /api/integrations/gmail` | Revoga token e para processamento; histórico mantido |
| `GET /api/drafts/{id}` / `PUT /api/drafts/{id}` | Editar incrementa `version` e invalida aprovação |
| `POST /api/drafts/{id}/approve` | `{version, contentHash, recipients[]}` — `409` se não coincide com o atual |
| `POST /api/drafts/{id}/send` | Exige `Approval` válida para a versão atual; `202 {outboxId}` |
| `PUT /api/settings/digest` | `{enabled, timeLocal}` |

Não existe rota que envie e-mail sem `Approval`; não existe parâmetro que dispense a aprovação.

## Bots e conversa (FR-007–FR-009)

| Rota | Descrição |
| --- | --- |
| `GET /api/bots` / `GET /api/bots/{id}` | Inclui config ativa e proposta pendente |
| `POST /api/bot-creator/messages` | `{conversationId?, text}` ⇒ `202 {conversationId, taskRunId}`; produz `BotProposal` |
| `POST /api/bots/{id}/configs/{configId}/approve` | `{contentHash}` ⇒ ativa (ou aplica nova versão) |
| `POST /api/bots/{id}/pause` / `…/resume` | |
| `POST /api/conversations/{id}/messages` | Conversa com coordenador/bot |
| `GET /api/task-runs?botId=` / `GET /api/task-runs/{id}` | Responsáveis, ação, fontes, resultado, falhas, pendências |
| `POST /api/task-runs/{id}/resume` | Retomada após pausa |
| `GET /api/activity?since=` | `ActivityLog` |

## Perfil (FR-001, FR-002)

`GET/PUT /api/profile`, `POST /api/profile/import` (reimporta `curriculo-en-draft.md`), `GET/PUT /api/criteria`, `GET/POST/PUT /api/companies`, `POST /api/companies/{id}/promote`.

## Preparação (FR-019, FR-020)

| Rota | Descrição |
| --- | --- |
| `POST /api/interviews` | `{opportunityId, language, kind}` ⇒ sessão + primeira pergunta |
| `POST /api/interviews/{id}/answers` | `{answer}` ⇒ próxima pergunta (uma por vez) |
| `POST /api/interviews/{id}/finish` | ⇒ `feedback`, `recommendations[]` com `origin` explícito |

## Dados (D-11)

`GET /api/data-inventory`, `DELETE /api/applications/{id}/mail-content`, `POST /api/data/erase-all` (exige senha novamente).

## DTOs do frontend (incremento de 2026-11-14)

As rotas acima que ainda não têm implementação de domínio no backend são consumidas pelo frontend com o formato abaixo. Os testes de interface interceptam essas rotas; fixtures não são incluídas no app de produção. A entrega das telas não comprova a implementação dos serviços.

- Listas de vagas, candidaturas, duplicatas, revisão, empresas, tarefas e atividades: `{items: [...], dataAsOf: string|null}`. Detalhes retornam o objeto diretamente. Datas usam camelCase, assim como os demais campos JSON.
- `OpportunitySummary`: `{id, title, companyName, location, workMode, contractType, salary, publishedAt: string|null, fitReasons: string[], userDecision, availability, deadlineAt?: string|null}`. `publishedAt: null` indica que a fonte não informou a data; isso não prova que a vaga esteja dentro do período pesquisado. `salary` é `null` ou `{min: number|null, max: number|null, unit: monthly|hourly|yearly|unknown}`; valores em BRL. O detalhe acrescenta `facts`, `inferred`, `missing` (listas de texto) e `sources: [{source, url, fetchedAt}]`. O resumo traz `source: {source, url, fetchedAt} | null` (a fonte consultada por último) e pode trazer `companyTier: priority|suggested|null`; o detalhe traz todas as fontes.
- `ApplicationSummary`: `{id, opportunityId, title, companyName, status, nextAction: string|null, nextActionDueAt: string|null, duePrecision}`. O detalhe acrescenta `history: [{id, toStatus, author, reason, at, evidence: {ref, quote}|null}]`. O formulário de próxima ação envia data sem hora escolhida como fim do dia em São Paulo (`T23:59:59-03:00`) e `duePrecision: date_only`; sem data envia `dueAt: null` e `ambiguous`.
- Duplicatas: `{id, opportunityA: OpportunitySummary, opportunityB: OpportunitySummary, signal}`. Revisão: `{id, kind, summary, targetRef, createdAt}`. Resolver uma revisão de bot não aprova configuração: essa aprovação permanece na rota específica, vinculada ao hash.
- Tarefas: `{id, state, pauseReason: string|null, errorSummary: string|null}`. Atividades: `{id, author, action, result, error: string|null, at}`. `GET /api/home` mantém o formato já descrito acima; integrações acrescentam `detail?: string`.
- Perfil: `{id, status, facts: [{id, kind, statement, origin, originRef}]}`. `PUT /api/profile` recebe essa estrutura; revisar envia `status: reviewed`. O frontend preserva fatos documentados e de estudo, edita declarações e cria novas declarações com UUID. O backend deve validar origens e não permitir que uma declaração se torne evidência documental por edição da requisição.
- Critérios: campos de `SearchCriteria` do data-model em camelCase, incluindo `version` para controle de concorrência. Empresas: `{id, name, tier, notes}`; criar recebe `{name, tier: priority, notes}`. Promoção de sugerida usa exclusivamente `POST /api/companies/{id}/promote`.
- Inventário: `{items: [{category, count, retention}]}`. A tela de dados é somente consulta neste incremento; exclusão e revogação continuam pendentes de T096.

Frontend ainda pendente: gestão/conversa de bots (T060), Gmail/rascunhos (T087), entrevista (T104), edição de empresas existentes e exclusão em Dados/Privacidade (restante de T097). Falhas de API são exibidas com opção de tentar novamente e nunca transformadas em listas vazias ou em datas de sucesso.

### Busca por período

GET /api/settings/search-routine retorna {enabled}; PUT recebe {enabled} e habilita/desabilita a rotina de busca. Rotina começa desabilitada. O período informado filtra novas oportunidades e novas inclusões da seleção sem apagar seleções anteriores. Datas desconhecidas continuam visíveis com aviso.
