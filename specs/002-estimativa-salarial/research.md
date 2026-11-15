# Research: estimativa salarial

## D1 — Fontes e viabilidade (investigado em 2026-11-14)

**Decisão**: coleta real permanece restrita até demonstrar fonte autorizada. Não implementar scraper Glassdoor como fonte liberada. Adaptador padrão retorna not_configured sem rede; fixtures só em testes. Não escolher alternativa silenciosamente.

**Evidência**: [termos Glassdoor](https://www.glassdoor.com/about/terms/), revisão 1 jul 2026, seção 4.3 itens 16/17 restringe agentes/coleta automatizada sem permissão escrita e deep-links. Login/captcha/bloqueio não podem ser contornados. Destino da ação de pesquisa também precisa de verificação, sem presumir rota ou licença.

**Alternativa**: [microdados MTE RAIS/CAGED](https://www.gov.br/trabalho-e-emprego/pt-br/acesso-a-informacao/acoes-e-programas/programas-projetos-acoes-obras-e-atividades/estatisticas-trabalho/microdados-rais-e-caged) são públicos, em TXT/FTP, mas não API salarial pronta; não demonstram cobertura de senioridade, remoto e PJ. Ingestão/agregação teria custo e escopo próprios. Nenhuma fonte substituta aprovada nesta etapa.

**Gate**: documentar permissão, formato e amostra verificável; levar substituição ao usuário conforme Assumptions da spec; só então ativar adaptador de produção e medir cobertura. Planejamento e tarefas possíveis com esse bloqueio declarado. Não prometer SC-101 >=70% ou afirmar que FR-101 já é viável.

## D2 — Integração determinística

**Decisão**: porta SalaryReferenceSource e resultado tipado; separar enriquecimento de SearchService e ranking. Dados do provedor não executam instruções; validar faixa, moeda, regime e evidência no backend.
**Motivo**: não existe cliente OpenBot implementado no código atual, embora a spec suponha pesquisa pelo bot. Integração futura deve usar porta e orçamento, após os gates da 001; não criar cliente duplicado nem bypass de aprovação.
**Alternativas**: pesquisar em GET atrasaria painel e repetiria acessos; alterar RuleBasedRanker violaria FR-107.

## D3 — Cache e histórico

**Decisão**: chave canônica versionada por cargo/senioridade/localidade-modalidade/regime/empresa; procurar empresa e depois mercado compatível na mesma operação lógica. Cache de sucesso 90 dias; tentativa única persistida por TaskRun/perfil inclui falhas. Não cachear falha por 90 dias. Concorrência por perfil impede duplicidade entre busca e atualização manual. Histórico append-only; manter última faixa/data após falha.
**Alternativas**: só cache em memória perde deduplicação no reinício; sobrescrever estimativa perde FR-108.

## D4 — Sem referência e unidades

**Decisão**: regime desconhecido => insufficient_profile; não inventar CLT/PJ. Anual divide por 12 com decimal, duas casas HALF_UP; hora/dia/ambíguo mantém original sem mensal. Menos de 5 observações => poucos dados. Estimativa nunca preenche Opportunity.salary.

## D5 — Login local

**Decisão autorizada pelo usuário**: perfil local sem senha, bind 127.0.0.1, HTTP e cookie Secure=false só nesse perfil. Principal técnico fixo conserva auditoria/idempotência; CSRF permanece. Configuração local exposta fora de loopback deve falhar no startup. Perfil padrão mantém login/TLS para uso futuro. SPA consulta authMode retornado pelo backend, sem bypass só visual.
**Evidência local**: SecurityConfig exige sessão nas APIs, application.yml usa 0.0.0.0/TLS, App.tsx contém SessionGate. Ainda não existe opção para desligar login.

## Validação e limites

Pesquisa por agente delegado conforme skill: fontes primárias acima e leitura do código. Nenhum teste funcional ou acesso salarial real executado. Meta do piloto, permissões e destino Glassdoor continuam pendentes de validação operacional. A constituição é template sem governança ratificada. Não há .specify/extensions.yml nesta execução, portanto nenhum hook registrado.

## D6 — Prova de acesso e orientação atualizada (2026-11-15)

O usuário pediu coleta pela ferramenta web para uso pessoal em seu PC ou no de um amigo. Isso define o canal desejado; não demonstra acesso do backend à ferramenta nem altera os termos da fonte.

Teste HTTP direto via Invoke-WebRequest nesta máquina: 403 Forbidden, página Security | Glassdoor. Teste pela ferramenta web da sessão: leitura bem-sucedida da página Desenvolvedor Java Brasil, faixa base mensal BRL 4.000–8.000, central 6.000. Observação estruturada em glassdoor-web-observation.json, com URL/data/método. Não é mock, mas também não é estimativa aplicada a vaga: regime/senioridade e amostra da faixa agregada não foram identificados. Os 50 relatos da lista não foram usados como sampleSize da faixa.

A afirmação anterior de impossibilidade técnica de coleta foi excessiva. Acesso por ferramenta web foi demonstrado; execução autônoma pelo backend local permanece não demonstrada. Canal desejado para integração futura: executor assistido com ferramenta web e saída estruturada validada, pela porta SalaryReferenceSource; não scraper HTTP direto. Não existe endpoint da ferramenta desta sessão que possa ser embutido automaticamente no aplicativo distribuído. O executor escolhido precisa comprovar essa capacidade em cada instalação. Enquanto isso, observação salva serve como evidência de viabilidade, sem alterar banco ou ranking.

## Refinamento aprovado — empresa + cargo (2026-11-15)

A consulta primária é pelo par empresa + cargo da vaga. Normalizar nome da empresa e cargo sem misturar homônimos ou empresas distintas; confirmar a identidade da empresa na página consultada. Senioridade, localidade/modalidade e regime continuam como critérios de compatibilidade, não devem ser presumidos quando ausentes.

A faixa de mercado só pode aparecer como fallback explicitamente identificado, nunca como salário daquela empresa. Referências específicas de empresas diferentes não compartilham cache. A observação genérica de Desenvolvedor Java já coletada demonstra acesso, mas não valida consulta empresa/cargo.

## D7 — Teste empresa + cargo (2026-11-15)

Consulta pela ferramenta web para CWI Software + Desenvolvedor Java: sucesso. Navegação da lista geral até página específica E489537 confirmou empresa/cargo no título e no conteúdo. URL/data/valores em glassdoor-company-role-observation.json. Seção mensal mostra faixa base 4.000–8.000 e média 6.000 BRL, atualização 2026-09-09 e 148 salários enviados. Texto descreve faixa total 4.000–9.000; componentes variáveis apresentam divergências, portanto não foram somados nem tratados como salário base. Regime e senioridade da faixa não identificados; sem aplicar automaticamente às vagas. Validada leitura assistida para um par empresa/cargo, não coletor autônomo local nem cobertura de outras empresas.

## Execução de implementação — 2026-11-15

Baseline: main com mudanças pré-existentes de candidaturas/Home/estilos/tests da 001, preservadas. V10 estava livre, V9 já presente. Ignores Git/ESLint/Prettier existentes cobrem outputs, dependências, temporários e dados locais; frontend private, sem publicação npm; sem Docker/Terraform/Helm detectado. Sem extensions.yml antes/depois: nenhum hook.

Implementado domínio salarial, migração V10, portas e adapter SQLite com histórico imutável, importação assistida atômica, cache/expiração e vínculos, tentativas por perfil/TaskRun, timeout 30 s e recuperação de tentativas interrompidas. Leituras sem rede, projeções separadas no início/lista/detalhe, catálogo consultável, links validados, atualização explícita via TaskRun com pausa needs_person na ausência de executor web. Modo local sem login em loopback, CSRF preservado; scripts de execução/importação e README.

Ajuste de desenho necessário para dados reais: source contractType=unknown é aceito como observação armazenável, mas SalaryProfile.complete=false impede aplicação automática a CLT/PJ. Isso não inventa regime ou relaxa comparação. Catálogo adicional mostra essas referências com aviso. Três páginas específicas de Java Pleno foram lidas via ferramenta web; lote real em pleno-web-observations.json. Coletado somente componente base mensal: CWI 6000–7000, Act Digital 7000–9000, Stefanini 5000–7000 BRL. Datas de atualização na fonte são de 2025 e ficam visíveis, separadas da consulta de 2026. Nenhuma componente variável somada. Atualização por importação conserva data original, não simula coleta recente.

Prova operacional: uma instância real em perfil local na porta 18445 importou 3 observações e GET /api/salary-estimates retornou as três persistidas, regime unknown; GET /api/session devolveu authenticated=true, setupRequired=false, authMode=local. Dados salvos no diretório padrão LOCALAPPDATA/Curriculotron, sem alterar o banco dev-home que já estava em uso por outro processo. Instância de verificação será encerrada ao final; dados permanecem.

Testes: LocalModeSecurityTest inicialmente falhou antes da implementação; SalaryNormalizationTest inicialmente não compilava sem os tipos novos. Falha de cache detectada com SQLite: getResultStream devolvia vazio; corrigido para getResultList. Testes com fonte simulada inicialmente compartilharam referência de mercado entre cenários, corrigida a independência dos cenários por localidade, sem alterar a regra de fallback.

E2E: 3 vagas controladas (Java Pleno CWI, Act Digital e Stefanini), lote de referências reais e uma fixture CLT explicitamente marcada e restrita ao banco descartável. Dois projetos (360px/1280px) passaram para importação, faixa em início/lista/inferências, precedência do declarado, links, ordem preservada e ausência de overflow. Teste inicialmente duplicava lote entre projetos; chaves idempotentes fixas no banco descartável corrigiram o isolamento. Não alegar que fixture CLT é evidência da fonte real.

Limitações: integração autônoma com ferramenta web ainda não existe; provider disponível é not_configured sem rede. Busca continua e solicita pesquisa assistida em atualização explícita. Matching exige critérios completos, portanto as três referências reais não viram salário aplicado à vaga. Rota de pesquisa Java Pleno validada pela web; cargo/localidade arbitrários e navegação real para outras rotas não garantidos. Provas de reinício/concorrrência HTTP/último sucesso e métricas de seleção completa ainda não cobrem todos os critérios do plano. Não executado piloto de uma semana nem demonstrada cobertura de 70%. Não declarar 29 tarefas completas.

### Resultado final das verificações

- Gradle `spotlessApply test spotlessCheck bootJar`: BUILD SUCCESSFUL; 104 testes, 0 falhas, 0 ignorados (JUnit inclui integração SQLite em src/test). integrationTest foi chamado anteriormente e é NO-SOURCE, não conta como teste adicional.
- Frontend Vitest: 28 testes, 6 arquivos, todos passaram; ESLint e TypeScript -b sem erros; Vite build passou.
- Playwright completo: 12 cenários passaram em mobile-360/desktop-1280, incluindo J1, J3 e salário. Sem verificação visual por screenshot ou preview nesta etapa.
- Reinício do processo local: GET confirmou as mesmas 3 observações, todas pleno e regime unknown, sem reimportar. Processo de verificação encerrado; banco preservado no diretório padrão. Serviço anterior na porta 18443 não foi interrompido.
- Nenhum hook de extensão configurado; nenhum commit/push realizado. Pendências registradas em tasks.md permanecem abertas.

## Continuação da implementação — 2026-11-15

Ampliados testes de normalização (T009) e adicionada suíte HTTP SalaryRefreshControllerTest (T023), com SQLite temporário e fonte simulada controlada por CountDownLatch. Fonte suspensa permite verificar duas requisições para vagas do mesmo perfil antes de liberá-la: mesmo TaskRun, uma chamada externa. Após blocked, estimativa e consultedAt originais e lastSuccessAt permanecem; repetição da chave após conclusão mantém tarefa.

Duas regressões comprovadas antes da correção: estado blocked com estimate era convertido em succeeded; pedido sem Idempotency-Key retornava 500. SalaryEstimateService agora exige estado found para aceitar estimate; SalaryRefreshController valida cabeçalho ausente/vazio com 400. Nenhuma integração externa ativada.

Gradle inicialmente indisponível por JAVA_HOME ausente; usado JDK 25 já instalado em C:/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot apenas no ambiente do comando. Resultado final spotlessApply test spotlessCheck: BUILD SUCCESSFUL, 110 testes, zero falhas. Frontend/e2e não reexecutados. Sem extensions.yml antes/depois, portanto sem hooks. T010/T017/T019/T020/T022/T027/T028/T029 continuam abertas.
