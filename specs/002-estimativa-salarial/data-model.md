# Modelo de dados

## SalaryProfile

Chave SHA-256 de JSON canônico versionado: cargo normalizado, senioridade, localidade/modalidade, regime, empresa opcional. Normalizar Unicode/caixa/espaços, sem fundir cargos ou senioridades por inferência livre. Campos insuficientes => insufficient_profile. Consulta primária por empresa + cargo, com identidade da empresa confirmada. Empresa específica tem preferência sobre mercado do mesmo perfil. Chaves específicas de empresas diferentes nunca compartilham estimativa; empresa nula é reservada ao escopo market. Ambas devem ser resolvidas em uma consulta lógica; regime desconhecido não combina com CLT/PJ.

## SalaryEstimate (imutável)

- id, profileKey, critérios completos; scope: company|market.
- currency: BRL; contractType: clt|pj.
- originalMin, originalMax, originalCentral opcional; originalUnit: monthly|yearly|hourly|daily|unknown.
- monthlyMin, monthlyMax, monthlyCentral opcionais. Restrições: `min >= 0 e max >= min`; `valores monetários decimais finitos, nunca ponto flutuante binário`.
- convertedFromAnnual: somente yearly; dividir por 12, duas casas HALF_UP, sem benefícios/13º. hourly/daily/unknown => mensais nulos e originais preservados.
- sampleSize: `nulo ou inteiro >= 0`; valor <5 gera poucos dados.
- source, sourceUrl, consultedAt, expiresAt, evidence: `obrigatórios para sucesso`; URL: `HTTPS pública sem credenciais`; evidência numérica verificável, sem instruções.
- expiresAt = consultedAt +90 dias; no instante da expiração já é antiga. Datas UTC; exibição pt-BR mês/ano em America/Sao_Paulo.
- vigente/antiga é calculado; substituída quando novo vínculo passa a valer. Não apagar versões ou atualizar data em falha.

## OpportunitySalaryLink

id, opportunityId FK, estimateId FK, appliedAt, supersededAt opcional. Restrição: `um vínculo corrente por oportunidade`, índice parcial. Várias vagas usam a mesma estimativa. Salário declarado sempre prevalece; ocultar referência sem apagar histórico se surgir salário depois.

## SalaryLookupAttempt

id, profileKey, taskRunId, requestedAt, completedAt, state, source, reason, estimateId opcional. Estados queued -> running -> succeeded|not_found|blocked|unavailable|invalid|provider_limit. Restrição: `único (taskRunId, profileKey)`; `máximo um running por perfil`. Persistir deduplicação também em falha/reinício. Renovação manual cria TaskRun próprio e usa idempotência existente.

## Transações

Rede fora da transação. Inserção da estimativa, conclusão da tentativa e substituição do vínculo em transação curta usando executor de escrita existente. Requisição concorrente reutiliza tarefa ativa. Falha mantém vínculo e lastSuccessAt. Cache negativo dura apenas o TaskRun, busca seguinte pode tentar. Sem credenciais ou conteúdo externo integral em logs.

