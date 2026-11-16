# Contrato — Critérios e compatibilidade

Contrato proposto para implementação. Complementa [panel-api.md](../../001-assistente-busca-emprego/contracts/panel-api.md). JSON camelCase, sessão/CSRF e envelope de erro existentes. GET não reavalia nem coleta.

## Critérios

GET /api/criteria → 200 CriteriaResponse. Implementação backend ainda ausente no baseline.

CriteriaResponse mantém os campos do formulário:
version, targetRoles[], stackKeywords[], remotePreferred, hybridRegions[], cltMinMonthlyBrl, pjMinMonthlyBrl, acceptJuniorIfJustified, dailySearchTimeLocal, deadlineHighlightDays, urgentHours, emailDigestEnabled.
Acrescenta assessment: RefreshStatus.

PUT /api/criteria recebe os mesmos campos editáveis e version esperada, sem assessment. Responde 200 com CriteriaResponse normalizado e assessment.state=pending quando houve mudança. Não executa reavaliação ou rede. Campo version é obrigatório; conflito → 409 criteria_version_conflict; payload inválido → 400 invalid_criteria, sem escrita parcial. No-op mantém versão/estado. Limites em [data-model.md](../data-model.md).

PUT é atualização versionada, sem exigir Idempotency-Key. Retry com versão antiga pode retornar 409; cliente relê, não sobrescreve perfil automaticamente. Horário/digest são preservados e validados no armazenamento; não ativar rotinas por efeito colateral deste endpoint, mantendo controles específicos existentes.

## Reavaliação

GET /api/opportunities/compatibility-status → 200 RefreshStatus.

POST /api/opportunities/reassess recebe {criteriaVersion: inteiro positivo}; exige CSRF. Reavalia somente dados armazenados e responde sincronicamente 200 {criteriaVersion, state: ready, assessedCount, remainingCount: 0, finishedAt}. Ready já atual permite o mesmo retorno sem reaplicar. Não altera daily_selection.

RefreshStatus:
- criteriaVersion: versão alvo atual;
- state: pending|running|ready|failed;
- stale: boolean (true se há avaliações ausentes/desatualizadas ou execução pendente/falha);
- assessedCount, remainingCount: inteiros >=0;
- startedAt, finishedAt: instante ou null;
- errorCode: null ou código seguro (assessment_failed, interrupted, evidence_changed).

Status é projeção de estado persistido e contagem atual de pendências; ingestão de nova evidência não pode deixar stale=false indevidamente. A rota literal compatibility-status deve ter mapeamento próprio, sem ser consumida como id de vaga.

Erros de POST:
- 400 invalid_criteria_version: corpo ausente/inválido;
- 409 criteria_version_conflict: versão solicitada deixou de ser atual;
- 409 reassessment_running: mesma instalação já tem execução; consultar status sem iniciar outra;
- 409 evidence_changed: três passagens não estabilizaram; preservar resultados atuais e permitir retry;
- 500 reassessment_failed: falha segura, consultar status; nenhuma resposta afirma ready;
- 401/403: contrato de autenticação/CSRF existente.

Sem Idempotency-Key: operação determinística que substitui avaliações, sem novo efeito de candidatura/seleção; concorrência é tratada por reserva. Timeout do cliente não prova cancelamento do servidor: consultar GET status antes de repetir. Nenhum polling inicia trabalho.

## Compatibilidade nas respostas

OpportunitySummary acrescenta:
compatibility: {criteriaVersion: number|null, evaluatorVersion: number|null, assessedAt: string|null, current: boolean, roleStatus: matched|mismatched|unknown, technologyStatus: matched|mismatched|unknown|unrestricted, reasonCodes: string[]}.

Sem avaliação: current=false, versões/tempo null, estados unknown e reasonCode=not_assessed. Não rotular o resultado legado como atual.

OpportunityDetailResponse acrescenta:
compatibilityEvidence: [{source: string, sourceUrl: string|null, fetchedAt: string|null, field: title|description, section: string|null, quote: string, matchedCriterion: string|null, observedTerm: string|null, mentionKind: required|optional|usage|negated|incidental|uncertain, observedVersion: string|null, configuredVersion: string|null}],
compatibilityCriteria: {targetRoles: string[], stackKeywords: string[]} | null.
Preservar facts/inferred/missing e fontes; missing inclui ausência de cargo/tecnologia/descrição. Renderizar quotes como texto, sem HTML externo. Não devolver descrição inteira na lista.

GET /api/opportunities?list=selection|secondary|discarded&date=:
manter items e dataAsOf (confirmados em ApiListResponse e ListResult do frontend). Acrescentar assessment: RefreshStatus em um DTO específico de lista de oportunidades, sem alterar envelopes de outras features.
selection e secondary só retornam avaliações current=true. mismatch nunca aparece. Unknown só na secundária, rotulado. discarded e detalhe continuam consultáveis mesmo desatualizados.

GET /api/home acrescenta assessment no nível principal e selectionToday.consumedCount. count continua quantidade de items visíveis; consumedCount é histórico bruto do dia (0–10 normalmente), limit=10. Frontend mostra “consumidas X de 10” e não infere consumo de items.length. dataAsOf não vira hora de leitura ou de salvar critérios.

## UI e cache

Perfil oferece salvar e, quando pending/failed, “Atualizar avaliação”. Mostrar conflito de edição com opção de recarregar critérios, sem perder silenciosamente o formulário. Botão de reavaliação indica execução em andamento e falha recuperável; pendências ficam identificadas por texto, sem depender só de cor.

Início e listas mostram aviso de avaliação desatualizada quando stale; resultados atuais parciais podem aparecer com esse aviso, resultados antigos ficam ocultos. Detalhe mantém avaliação anterior rotulada e snapshot dos critérios usados. Campo fitReasons antigo não pode contradizer os estados novos nem afirmar match onde há unknown.

Após PUT/POST, invalidar query keys criteria, compatibility-status, opportunities, opportunity e home; incluir critérios/versão nos dados de resposta e evitar que resposta de consulta iniciada antes do save reapareça como atual (cancelar consultas em andamento antes da invalidação). Novo parser de DTOs trata resposta como unknown e valida enums/campos; não usar api<T> como validação. Não implementar o classificador no React.

## Refinamento dos contratos anteriores

FR-005 da spec 001 passa a excluir incompatibilidades demonstradas da lista secundária. RB-001 continua seed inicial editável. Atualizar a spec 001 e panel-api.md no mesmo incremento do código, preservando contratos de salários/candidaturas e identificando rotas realmente implementadas. Este documento descreve o desenho futuro, não certifica implementação.
