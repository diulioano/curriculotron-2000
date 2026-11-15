# Contratos salariais e sessão local

## Leituras

GET /api/home, /api/opportunities e /api/opportunities/{id}: acrescentar salaryReference e glassdoorUrl sem alterar salary/facts/fitReasons/ordem. GET não consulta rede nem cria tarefa.

salaryReference é null se salário declarado existe. Caso contrário contém status (available|unavailable|non_monthly), reason opcional, estimate opcional. unavailable => estimate=null e reason not_found|insufficient_profile|blocked|unavailable|invalid|provider_limit|not_configured.

estimate contém id, monthlyMin, monthlyMax, monthlyCentral opcional, currency=BRL, originalMin, originalMax, originalCentral opcional, originalUnit, convertedFromAnnual, sampleSize opcional, source, sourceUrl, consultedAt, expiresAt, stale, scope, criteria (role, seniority, location, workMode, contractType, company opcional). Critérios e validação conforme data-model.md. Referência antiga mantém available e stale=true mesmo após erro de renovação.

glassdoorUrl: URL da estimativa Glassdoor validada, ou pesquisa salarial com cargo/localidade codificados; deve existir para toda vaga, mesmo com salário declarado. Implementação deve verificar rota real e permissão do destino, sem inventar parâmetros. Aceitar host Glassdoor aprovado, nunca outro host como Glassdoor. Abrir nova aba com noopener noreferrer. Clique não modifica vaga/candidatura.

## Atualização

POST /api/opportunities/{id}/salary-estimate/refresh, sem body, X-CSRF-Token e Idempotency-Key existentes. 202 devolve {taskRunId,state:queued|running|completed|failed|paused}; concorrência reutiliza tarefa ativa. 404 vaga ausente; 409 salary_declared; 422 insufficient_profile. Repetir chave não faz nova consulta. Consultar TaskRun pelo mapping já existente no código (confirmar rota antes da implementação). Erros seguem envelope atual. Falha externa no TaskRun preserva faixa antiga. Sucesso invalida queries do detalhe, início e listas; botão bloqueado enquanto pendente, erro explícito permite nova tentativa.

## Garantias de execução e repetição

A chave de atualização é persistida no escopo da vaga e da operação salarial, sem expiração
automática. Repetir a chave retorna a mesma tarefa e seu estado atual, inclusive quando a chamada
original reutilizou uma consulta da rotina ou de outra vaga com perfil idêntico. O endpoint não
recebe payload; uma nova intenção de atualização usa uma nova chave. Estados terminais podem
aparecer no replay do 202; o consumidor consulta o TaskRun para acompanhar o resultado.

A reserva é por perfil na JVM da instalação e é compartilhada entre rotina e atualização manual.
Perfis distintos podem progredir em paralelo. O executor manual admite quatro workers e dezesseis
pedidos em espera; rejeição registra falha e libera a reserva. Após reinício, tarefas salariais
queued/running e tentativas em execução são encerradas como interrompidas/falhas; uma nova ação
usa nova chave. Não há retomada automática nem promessa de execução exatamente uma vez.

## SalaryReferenceSource

lookup(profile,executionBudget) retorna found|not_found|blocked|unavailable|invalid|provider_limit|not_configured. found exige campos/evidência do modelo; fonte resolve empresa/mercado na mesma operação. Validar compatibilidade e dados fora do modelo. Timeout 30 s, sem retry automático, respeitar orçamento existente. Fonte sem permissão retorna not_configured sem rede. Nenhuma integração de produção deve ser ativada só por haver fixtures.

## Sessão

GET /api/session acrescenta authMode=local|password. local devolve authenticated=true, setupRequired=false, csrfToken válido e usa principal técnico fixo, somente loopback. Mutações mantêm CSRF/idempotência. SPA oculta setup/login/sair no modo local. Perfil padrão conserva comportamento anterior; falhar ao iniciar local com bind não loopback.

Consulta da porta: empresa + cargo é a busca primária. Resultado company exige identidade da empresa confirmada; sem resultado compatível, market deve ser explicitamente identificado como média de mercado. Não atribuir faixa genérica à empresa nem compartilhar referência company entre empresas distintas.
