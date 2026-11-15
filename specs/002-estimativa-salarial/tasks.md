# Tasks: Estimativa salarial

**Input**: spec.md, plan.md, research.md, data-model.md, contracts/salary-api.md e quickstart.md nesta pasta.
**Estado**: implementação parcial; ver evidências e pendências no fim deste arquivo. [P] indica arquivos independentes na mesma etapa, após dependências. Caminhos são relativos à raiz.
**Testes**: incluídos pelos cenários e SC-102/104/105 da spec; escrevê-los antes do código correspondente e verificar falha inicial relevante.

## Fase 1 — Setup

- [X] T001 Confirmar baseline, versões livres de migração, mudanças existentes e comandos de teste em specs/002-estimativa-salarial/research.md; preservar trabalho não commitado e conferir ignores sem mudanças desnecessárias.
- [X] T002 Registrar gate de fonte em specs/002-estimativa-salarial/research.md: Glassdoor restrito; separar fixtures de produção e evidenciar que fonte alternativa depende de decisão do usuário. Não bloquear infraestrutura por essa ativação pendente.

## Fase 2 — Fundação compartilhada

Checkpoint: contratos, persistência e execução local prontos antes das histórias.

- [X] T003 [P] Criar teste de segurança local/padrão em backend/src/test/java/dev/curriculotron/auth/LocalModeSecurityTest.java cobrindo sessão sem senha, CSRF, principal técnico, idempotência e falha de startup com bind não loopback.
- [X] T004 [P] Criar domínio em backend/src/main/java/dev/curriculotron/salary/domain/SalaryProfile.java e SalaryEstimate.java com chave canônica versionada; currency BRL, contractType clt|pj, originalUnit monthly|yearly|hourly|daily|unknown, scope company|market; critérios insuficientes recusam consulta.
- [X] T005 Criar backend/src/main/resources/db/migration/V10__salary_estimates.sql (ajustar versão se ocupada), tabelas estimate/link/attempt conforme data-model.md: `min >= 0 e max >= min`, `nulo ou inteiro >= 0` para sampleSize, `obrigatórios para sucesso` para proveniência, `um vínculo corrente por oportunidade`, `único (taskRunId, profileKey)` e `máximo um running por perfil`; FKs e índices de perfil/validade.
- [X] T006 Criar portas em backend/src/main/java/dev/curriculotron/salary/application/port/out/SalaryEstimateRepository.java e SalaryReferenceSource.java, resultado tipado e orçamento; implementar adapter/out/persistence/SalaryEstimatePersistenceAdapter.java com WriteExecutor existente, histórico e transações curtas sem rede.
- [X] T007 Criar backend/src/main/resources/application-local.yml e adaptar backend/src/main/java/dev/curriculotron/auth/adapter/in/web/SecurityConfig.java e SessionController.java: perfil local loopback, HTTP/cookie Secure=false, principal técnico, authMode e CSRF; manter autenticação padrão e validar T003.
- [X] T008 Adaptar frontend/src/api/client.ts, frontend/src/app/App.tsx e Layout.tsx para authMode do backend: entrar direto no modo local e ocultar setup/login/sair; acrescentar cenários local/password em frontend/tests/unit/app.test.tsx sem bypass apenas visual.

## Fase 3 — US1: ver faixa estimada (P1, MVP)

**Teste independente**: fixtures mensal/anual/horária/ausente/regime divergente e salário declarado em início/lista/detalhe; inferências separadas; seleção idêntica.

- [X] T009 [P] [US1] Escrever backend/src/test/java/dev/curriculotron/salary/SalaryNormalizationTest.java: mensal, anual/12 HALF_UP, horário/dia/ambíguo sem conversão, BRL, `min >= 0 e max >= min`, `valores monetários decimais finitos, nunca ponto flutuante binário`, amostra `nulo ou inteiro >= 0`, divergência de regime e critérios insuficientes.
- [ ] T010 [P] [US1] Escrever backend/src/test/java/dev/curriculotron/salary/SalarySourceContractTest.java com fixtures em backend/src/test/resources/fixtures/salary/: sucesso com source/sourceUrl/consultedAt/expiresAt/evidence `obrigatórios para sucesso`, URL `HTTPS pública sem credenciais`, rejeição de dados inválidos/instruções, timeout, bloqueio e provider_limit.
- [X] T011 [P] [US1] Escrever frontend/tests/unit/salary-estimate.test.tsx cobrindo rótulos, fonte/mês, poucos dados, referência antiga, origem anual, unidade não mensal, ausência e precedência do declarado.
- [X] T012 [US1] Implementar backend/src/main/java/dev/curriculotron/salary/domain/SalaryNormalizer.java e application/SalaryEstimateService.java: validação T009/T010 fora do modelo, consulta primária empresa + cargo, confirmação da identidade da empresa, compatibilidade dos demais critérios e fallback rotulado média de mercado, cache de sucesso <90 dias e tentativa única inclusive falhas no TaskRun; preservar fonte/data antigas em falha.
- [X] T013 [US1] Implementar backend/src/main/java/dev/curriculotron/salary/adapter/out/source/UnavailableSalarySource.java como padrão not_configured sem rede e fake somente em testes; manter porta pronta para fonte autorizada sem criar dependência obrigatória do OpenBot incompleto.
- [X] T014 [US1] Integrar enriquecimento isolado em backend/src/main/java/dev/curriculotron/sources/SearchService.java após seleção: uma operação por perfil/TaskRun, timeout 30 s, sem retry, orçamento, falhas em ActivityLogService/IntegrationStatusService sem alterar lastSuccessAt ou impedir busca; nenhuma alteração em ObjectiveFilter/RuleBasedRanker.
- [X] T015 [US1] Acrescentar salaryReference de leitura em backend/src/main/java/dev/curriculotron/opportunities/adapter/in/web/OpportunitySummaries.java e OpportunityController.java conforme contracts/salary-api.md, inclusive início por resumos; nenhuma rede em GET, salário declarado retorna referência nula.
- [X] T016 [US1] Tipar DTO em frontend/src/api/panel.ts e criar frontend/src/components/SalaryEstimate.tsx com formatação pt-BR/mês/ano e avisos do contrato; exibir em OpportunityCard.tsx e pages/OpportunityDetail.tsx, neste último apenas em inferências com critérios/amostra.
- [ ] T017 [US1] Criar backend/src/integrationTest/java/dev/curriculotron/salary/SalarySelectionInvariantTest.java e frontend/tests/e2e/salary-estimates.spec.ts com seeds em frontend/tests/e2e/support/seed.ts; provar mesmos IDs/ordem/score/motivos antes/depois e salário acrescentado depois ocultando referência, início/lista/detalhe a 360/1280px; executar testes T009–T017.

## Fase 4 — US2: abrir Glassdoor (P1)

**Teste independente**: fonte Glassdoor/alternativa/ausente e salário declarado abrem destino relacionado em nova aba sem mutação.

- [X] T018 [P] [US2] Escrever backend/src/test/java/dev/curriculotron/salary/GlassdoorLinkTest.java para host permitido, rejeição de credenciais/esquemas/hosts falsos, cargo e localidade codificados e fallback sem referência.
- [ ] T019 [US2] Verificar destino real/permitido da pesquisa Glassdoor e registrar evidência em specs/002-estimativa-salarial/research.md; se restrito, registrar impedimento de ativação da ação em produção e não inventar rota funcional ou declarar SC-104 aprovado.
- [ ] T020 [US2] Implementar backend/src/main/java/dev/curriculotron/salary/domain/GlassdoorLink.java e expor glassdoorUrl em opportunities/adapter/in/web/OpportunitySummaries.java e OpportunityController.java após validação T019; URL fonte apenas se Glassdoor aprovado, senão pesquisa cargo/localidade para todas as vagas.
- [X] T021 [US2] Adicionar ação ver no Glassdoor em frontend/src/components/OpportunityCard.tsx e pages/OpportunityDetail.tsx com target=_blank e rel=noopener noreferrer; testar nova aba e ausência de mutação em frontend/tests/e2e/salary-estimates.spec.ts e ampliar frontend/tests/unit/salary-estimate.test.tsx.

## Fase 5 — US3: reaproveitar e renovar (P2)

**Teste independente**: três vagas similares em buscas diferentes reutilizam uma faixa; renovar após 90 dias ou manualmente preserva histórico, falha mantém antiga.

- [ ] T022 [P] [US3] Escrever backend/src/integrationTest/java/dev/curriculotron/salary/SalaryCacheTest.java com SQLite temporário/relógio controlado: <90 dias, fronteira exata, restart, falha única por TaskRun, cache por empresa/mercado, duas empresas distintas com o mesmo cargo sem cruzar faixas, fallback identificado e separação CLT/PJ.
- [X] T023 [P] [US3] Escrever backend/src/test/java/dev/curriculotron/salary/SalaryRefreshControllerTest.java: 202/404/409/422, CSRF, Idempotency-Key, consulta concorrente retorna mesma tarefa e falha preserva faixa/data/lastSuccessAt.
- [X] T024 [US3] Completar renovação em backend/src/main/java/dev/curriculotron/salary/application/SalaryEstimateService.java e adapter/out/persistence/SalaryEstimatePersistenceAdapter.java: expiração >=90 dias, fallback antigo, append-only, transação atômica dos vínculos, recuperação após restart e exclusão mútua por perfil entre busca e renovação.
- [X] T025 [US3] Criar backend/src/main/java/dev/curriculotron/salary/adapter/in/web/SalaryRefreshController.java e application/SalaryRefreshService.java com TaskRun e executor existentes, endpoint do contrato, idempotência, orçamento e estado da fonte; confirmar mapping de consulta TaskRun no código antes de integrar.
- [X] T026 [US3] Adicionar cliente de atualização em frontend/src/api/panel.ts e ação em frontend/src/pages/OpportunityDetail.tsx: estado pendente, consulta TaskRun, erro recuperável e invalidação de detalhe/listas/início; ampliar frontend/tests/unit/salary-estimate.test.tsx e frontend/tests/e2e/salary-estimates.spec.ts; executar T022–T026.

## Fase 6 — Acabamento e ativação real

- [ ] T027 Documentar modo local, perfil padrão e limites de fonte em README.md; executar specs/002-estimativa-salarial/quickstart.md e registrar comandos/resultados reais em research.md, sem marcar piloto como testado por fixtures.
- [ ] T028 Resolver gate externo em specs/002-estimativa-salarial/research.md com evidência de fonte autorizada e decisão do usuário antes de substituir Glassdoor; então implementar backend/src/main/java/dev/curriculotron/salary/adapter/out/source/AuthorizedSalarySource.java e validar contrato com amostras reais. Se não houver autorização/fonte, manter esta tarefa pendente e comunicar entrega parcial.
- [ ] T029 Executar piloto real e uma semana de acompanhamento descritos em specs/002-estimativa-salarial/quickstart.md e registrar em research.md métricas SC-101–SC-105, consultas por perfil e limitações; não concluir cobertura >=70% sem medida.

## Dependências e execução

T001 -> T002 -> fundação T003–T008 -> histórias. T004 -> T005 -> T006; T003 antecede T007, T007 antecede T008. US1: testes T009–T011 antes de T012–T016; T012 depende de T006 e testes de domínio/contrato; T014 depende de T012/T013; T015 antes de T016; T017 encerra incremento. US2 depende da fundação e pode iniciar testes/pesquisa sem US1; T020 depende de T018/T019, T021 de T020. US3 depende do serviço/cache e DTO da US1, não do link US2; T022/T023 antes de T024/T025 e T026 por último. Acabamento local T027 após histórias; T028 é gate da ativação real, T029 depende de T028 e das histórias completas. T019 bloqueado impede declarar US2 concluída.

## Paralelismo possível

US1: T009, T010 e T011 em arquivos distintos; depois implementação sequencial nos mesmos arquivos. US2: teste/pesquisa T018/T019 podem ocorrer independentemente; T020/T021 sequenciais. US3: T022 e T023 independentes antes de implementar. Fundação: T003 e T004 independentes. Não paralelizar histórias que alterem OpportunityController, panel.ts, OpportunityDetail ou os mesmos testes.

## Estratégia incremental e rastreabilidade

MVP local: fundação + US1, claramente demonstrado com fixtures enquanto fonte estiver restrita. Adicionar US2 e US3; só declarar entrega integral após ativação e piloto. Implementação iniciada em 2026-11-15; não declarar conclusão integral enquanto gates e validações pendentes existirem.

FR-101/102: T002/T004/T012/T013/T028; FR-103: T009/T012/T016; FR-104: T005/T006/T012/T022/T024; FR-105: T011/T015–T017; FR-106: T018–T021; FR-107: T014/T017; FR-108: T023–T026; FR-109: T010/T014/T023/T025; FR-110: T010/T012/T014/T022/T024/T025. Login local: T003/T007/T008/T027. SC-101/103 reais: T029, demais cenários: T017/T021/T022/T026.


## Evidência da implementação (2026-11-15)

As tarefas marcadas foram implementadas e verificadas; validações parcialmente cobertas mantêm checkbox aberto. Testes SQLite consolidados em backend/src/test/java/dev/curriculotron/salary/SalaryCacheTest.java (não na source set integrationTest); SalarySourceContractTest.java cobre respostas/compatibilidade/falhas; segurança local cobre sessão/CSRF e LocalAccessTest cobre bind loopback. UI em salary-estimate.test.tsx e jornada salary-estimates.spec.ts (360/1280). T009/T010/T017/T019/T020/T022/T023/T027 têm partes concluídas, mas faltam critérios específicos, fixtures/paths ou cenários completos pedidos. Não marcar como integrais por aproximação.

T028: coleta assistida real via web e importação estão disponíveis, porém executor autônomo não existe. T029: três referências e piloto pontual realizados; semana de métricas permanece pendente. Dataset real unknown não foi alterado para CLT/PJ. Catálogo adicional permite conferência sem aplicar referência incompatível.

Validação final: 104 testes backend, 28 frontend e 12 e2e passaram; dados reais persistidos após reinício. Fonte autônoma e critérios específicos dos checkboxes abertos continuam pendentes.



## Continuação — 2026-11-15

T009 concluída com precisão decimal, HALF_UP na fronteira, unidades sem conversão, faixas inválidas, BRL/amostra e perfis incompletos; divergência CLT/PJ também coberta por SalaryCacheTest. T023 concluída em SalaryRefreshControllerTest: 202/404/409/422, CSRF, cabeçalho obrigatório, repetição da chave após término, duas vagas do mesmo perfil durante consulta bloqueada reutilizando TaskRun e falha preservando faixa/data/lastSuccessAt. Testes novos expuseram resposta contraditória da fonte (blocked com estimativa era aceita) e ausência de Idempotency-Key retornando 500; ambos corrigidos. T010 recebeu regressão adicional, mas continua parcial pelos demais critérios.

Validação desta continuação: Gradle spotlessApply test spotlessCheck passou; 110 testes backend, zero falhas. Frontend/e2e não reexecutados nesta continuação. Nenhum extensions.yml presente no início/fim. Demais tarefas abertas e gates externos permanecem pendentes; não constitui entrega integral da feature.
