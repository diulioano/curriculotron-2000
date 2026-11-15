# Contrato — Saídas estruturadas dos bots

Toda resposta de agente OpenBot é tratada como proposta. O backend valida estes schemas (JSON Schema 2020-12) e decide o efeito. Os arquivos `.schema.json` correspondentes serão gerados na implementação em `backend/src/main/resources/schemas/` a partir desta especificação.

## `job.rank@1` — ranking de vagas (Busca)

Entrada: critérios, fatos `documented|declared` do perfil, lista de vagas já normalizadas pelo backend.

```json
{
  "items": [{
    "opportunityId": "string",
    "fit": "strong | medium | weak",
    "reasons": ["string"],
    "inferredRequirements": ["string"],
    "concerns": ["string"]
  }]
}
```
Efeito: o backend aplica filtros objetivos (RB-002, RB-003) **antes** e **depois**; o modelo não pode incluir vaga reprovada nem ultrapassar o teto diário. `weak` nunca entra na seleção (US1-2).

## `mail.classify@1` — classificação de mensagem (Acompanhamento)

Entrada: metadados + trecho necessário da mensagem e candidaturas candidatas (id, empresa, título, data).

```json
{
  "relevant": true,
  "kind": "confirmation | recruiter_contact | interview_invite | deadline | result | job_alert | other",
  "applicationIds": ["string"],
  "evidenceQuote": "string",
  "deadline": { "value": "ISO-8601 | null", "precision": "exact | date_only | ambiguous" },
  "resultOutcome": "rejected | advanced | offer | null"
}
```
Efeito: `applicationIds` com exatamente um item **e** `evidenceQuote` presente literalmente na mensagem ⇒ associação `explicit`; caso contrário `needs_review`. Mudança de status só com `kind ∈ {result, interview_invite, recruiter_contact}` e evidência literal.

## `draft.prepare@1` — rascunho (Acompanhamento/Preparação)

```json
{ "subject": "string", "body": "string", "claimsUsed": [{ "text": "string", "profileFactId": "string" }] }
```
Efeito: cria `Draft` sem destinatários definidos pelo modelo (destinatários vêm do usuário ou da mensagem original e são confirmados na aprovação). Afirmação sem `profileFactId` de origem `documented|declared` é sinalizada para revisão (RB-006).

## `bot.propose@1` — proposta do criador (Coordenador)

```json
{
  "inScope": true,
  "clarifyingQuestions": ["string"],
  "proposal": {
    "name": "string", "purpose": "string", "instructions": "string",
    "capabilities": ["job.search | job.rank | mail.classify | draft.prepare | interview.simulate | profile.read"],
    "limits": { "maxTasksPerDay": 0 },
    "routines": [{ "kind": "daily | manual", "timeLocal": "HH:mm" }]
  }
}
```
Efeito: `inScope=false` ⇒ resposta informa o limite da V1 e nada é criado (US2-4). Com perguntas pendentes, `proposal` pode ser nulo. Proposta válida ⇒ `BotConfig` em `in_review`; nunca ativa sem `POST …/approve`.

## `task.interpret@1` — pedido em linguagem natural (Coordenador)

```json
{ "intent": "search_now | adjust_criteria | explain | status | other", "parameters": {}, "replyText": "string" }
```
Efeito: o backend mapeia `intent` para operações permitidas; `adjust_criteria` gera proposta de mudança que o usuário confirma.

## `interview.turn@1` / `interview.feedback@1` (Preparação)

```json
{ "question": "string", "topic": "string" }
```
```json
{
  "strengths": ["string"], "improvements": ["string"],
  "studyTopics": [{ "topic": "string", "linkedRequirement": "string" }],
  "exercises": ["string"], "questionsToAsk": ["string"]
}
```
Efeito: `studyTopics` são gravados como `ProfileFact(origin=study_recommended)` apenas em recomendações, nunca no perfil documentado (US6-3).
