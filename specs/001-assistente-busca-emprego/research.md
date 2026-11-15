# Research — Assistente pessoal de busca de emprego

Data: 2026-11-14. Status: **fase 0 resolvida para desenho; prova ao vivo PT-001/PT-002 pendente** (ver "Gate restante").

Fontes de evidência desta rodada:

- Instalação local `%LOCALAPPDATA%\Programs\OpenBot` (Electron, versão 0.70.0, publisher Norbert Bodziony).
- CLI empacotada `resources/cli/openbot-cli.mjs` (lida integralmente).
- Skills gerenciadas `resources/managed-skills/{openbot-data,openbot-site-hosting,openbot-skill-creator}/SKILL.md`.
- Catálogo `resources/plugin-catalog/catalog.json` e strings do bundle `app.asar` (somente leitura, sem descompactar nem executar).
- Configuração não secreta em `%APPDATA%\OpenBot` (`openbot-setup-v2.json`: provedor preferido `codex`, setup concluído em 2026-11-13). Arquivos de autenticação e o banco `openbot.db` **não** foram abertos.
- Decisões do usuário em 2026-11-14: stack Java (versão mais recente) + Vite/React; acesso móvel somente pela Wi-Fi de casa; usuário fará a prova ao vivo do OpenBot.

## Decisões técnicas

### D-01 — Linguagem e runtime do serviço local

- Decision: **Java 25 LTS** (LTS mais recente; confirmar na instalação se há LTS mais nova) com **Spring Boot 4.x**, Gradle (Kotlin DSL).
- Rationale: escolha do usuário ("Java mais recente"); alinha com o perfil profissional e facilita manutenção. LTS garante atualizações de segurança durante o uso prolongado; uma versão não-LTS mais nova pode ser adotada sem mudança de desenho.
- Alternatives considered: TypeScript/Node ponta a ponta (rejeitado pelo usuário); Python (sem afinidade com o perfil).
- Pré-requisito: nenhum JDK instalado hoje. Instalar JDK (ex.: Eclipse Temurin) é passo de setup, não dependência de runtime em produção além do próprio JDK.

### D-02 — Frontend

- Decision: **Vite + React + TypeScript**, SPA compilada para `dist/` e servida como estático pelo Spring Boot (um único processo e uma única origem).
- Rationale: escolha do usuário; mesma origem elimina CORS e simplifica cookie de sessão. React é listado no currículo.
- Alternatives considered: Angular (não escolhido); servidor Node separado para o frontend (dois processos, sem benefício para um usuário).
- **Versões verificadas (T001, 2026-11-14)**: Eclipse Temurin JDK 25.0.5+8 LTS (winget `EclipseAdoptium.Temurin.25.JDK`; Java 27 existe mas não é LTS); Node 26.13.1 LTS / npm 11.20.0 (winget `OpenJS.NodeJS.LTS`); Spring Boot 4.1.2; Gradle 9.8.2 (wrapper); Vite 8.3, React 19.3, React Router 8.5, TanStack Query 5.107. **TypeScript fixado em 6.0** (o template do Vite traz TS 7, mas `typescript-eslint` 8.72 só aceita TS < 6.1; mantido ESLint conforme T004).
- Pré-requisito: Node LTS só para build/dev do frontend. A CLI do OpenBot também exige Node 18+, mas o backend **não** depende dela (ver D-04).

### D-03 — Persistência

- Decision: **SQLite** em arquivo (`%LOCALAPPDATA%\Curriculotron\data\curriculotron.db`) via `sqlite-jdbc`, modo WAL, migrações **Flyway**, acesso com **Spring Data JPA** (Hibernate + `hibernate-community-dialects`/`SQLiteDialect`). O esquema é só do Flyway (`ddl-auto: none`); instantes são gravados em texto ISO-8601 UTC com milissegundos fixos (`InstantTextConverter`), o que mantém a ordem de texto igual à cronológica.
- Rationale: transacional, embarcado, zero serviço extra, cópia de segurança por arquivo. Volume de um usuário é trivial. As chaves idempotentes (NFR-004) ficam garantidas pela leitura-e-gravação dentro do executor único de escrita, não por `INSERT … ON CONFLICT`.
- Arquitetura: hexagonal por módulo — `domain/` (regras, sem Spring/JPA), `application/` (casos de uso) com `port/out/` (interfaces de repositório), `adapter/in/` (web, agendamento, startup) e `adapter/out/persistence/` (entidades JPA, repositórios Spring Data, adaptadores que implementam as portas). Entidades JPA não saem do adaptador.
- Alternatives considered: H2 em arquivo (formato menos portátil para inspeção/backup); PostgreSQL (serviço adicional sem ganho); banco compartilhado do OpenBot (`openbot.execute_data`) — rejeitado: limite de 256 MB, **sem transações**, tabelas visíveis e graváveis por qualquer agente, e o conteúdo vira dado não confiável para todos os agentes (contradiz NFR-003/NFR-004).
- Escrita única: um único `ExecutorService` de escrita serializa transações para evitar `SQLITE_BUSY`.

### D-04 — Integração com OpenBot (PT-001, PT-002)

- Decision: o backend fala diretamente com a **API "Local scripts"** do OpenBot via HTTP, reproduzindo o protocolo da CLI oficial em Java. A CLI Node é usada apenas na prova manual e em diagnóstico.
- Evidência (estática, da CLI oficial):
  - Descoberta: arquivos `url` e `token` em `%APPDATA%\OpenBot\automation\` (sobrescrevível por `OPENBOT_AUTOMATION_DIR`). Os arquivos só existem com o OpenBot aberto e ao menos um agente com Local scripts ligado. Hoje a pasta **não existe** (OpenBot fechado, nenhum agente em `%USERPROFILE%\OpenBot\Agents`).
  - Autenticação: `Authorization: Bearer <token>`; 401 ⇒ reler arquivos (OpenBot reiniciou); 403 ⇒ Local scripts desligado no agente.
  - Rotas usadas: `GET /v1/agents`; `POST /v1/agents/{id}/messages` com `{text, clientMessageId}` (chave de idempotência); `GET /v1/agents/{id}/deliveries/{deliveryId}` (estados `queued`, `running`, `completed`, `failed`, `interrupted`, `cancelled`; campos `answer`, `error`); `GET /v1/agents/{id}/pending` (`prompts`, `approvals`, `browserTakeovers`); `POST /v1/agents/{id}/settings` (`model`, `reasoningEffort`, `provider`, `providerAccountId`); `POST /v1/agents/{id}/context/clear`.
  - Limite de taxa configurável por agente/hora (strings "Local scripts sent this agent {limit} message or routine requests in the last hour") ⇒ tratar como pausa por limite (FR-018).
  - Local scripts só pode ser ligado no computador que roda o agente (sem uso remoto).
- Rationale: interface oficial e documentada pelo próprio produto; idempotência nativa por `clientMessageId` atende NFR-004; o estado "aguarda pessoa" (`pending`) mapeia para pausa visível.
- Alternatives considered: rotinas nativas do OpenBot (`/v1/agents/channel/routines`) — rejeitadas como gatilho: um bot não revisado no nosso app poderia rodar (FR-007). O agendamento fica no nosso backend, que só envia tarefas de bots com configuração aprovada. Compartilhar estado pelo banco do OpenBot — rejeitado (D-03).
- Lacunas conhecidas (validar na prova): não há rota visível para definir instruções de sistema do agente; o desenho envia as instruções aprovadas como preâmbulo versionado em cada tarefa. Cobrança/limites da assinatura Codex não são observáveis pela API além de falhas e limite de taxa.

### D-05 — Bots: onde ficam configuração, autorização e enforcement (PT-002, NFR-003)

- Decision: **a fonte de verdade dos bots é o nosso app.** Cada um dos quatro bots (Coordenador, Busca, Acompanhamento, Preparação) e cada bot criado por conversa é uma `BotConfig` versionada no SQLite, vinculada a um agente OpenBot dedicado. Ativação, pausa, rotinas e capacidades são aplicadas pelo backend. O modelo **nunca** recebe ferramenta de envio de e-mail, credencial ou permissão de mudar configuração; ele devolve JSON estruturado que o backend valida (contracts/bot-task-outputs.md) e transforma em ações permitidas pela `BotConfig` aprovada.
- Rationale: o README oficial descreve agentes com acesso amplo e edição por prompt; instruções textuais não são controle (pesquisa anterior). Enforcement fora do modelo é o único jeito de garantir FR-010, FR-014 e SC-008.
- Coordenador: implementado como orquestração determinística no backend (fila de tarefas) + um agente OpenBot para interpretar pedidos em linguagem natural em uma `TaskProposal`. A distribuição para os outros bots é feita pelo backend, não pelo modelo.
- Criador conversacional: usa o agente Coordenador para conversar; a saída é uma `BotProposal` (estado `draft`) restrita ao domínio emprego; ferramentas propostas só podem vir do catálogo fixo de capacidades do app (`job.search`, `job.rank`, `mail.classify`, `draft.prepare`, `interview.simulate`, `profile.read`).
- Agentes OpenBot: o usuário cria um agente por bot (nome fixo, ex.: `curriculotron-busca`), escolhe Codex e liga Local scripts. O app descobre por nome via `GET /v1/agents` e valida. Workspace do agente sem arquivos pessoais além do necessário.
- Risco residual: o agente OpenBot tem ferramentas próprias (shell, navegador). Conteúdo externo injetado em uma vaga poderia induzir o agente a usá-las. Mitigação: (1) para classificação/ranking, enviar apenas texto já extraído pelo backend, sem pedir navegação; (2) aprovações de comando do OpenBot ficam ligadas e qualquer `pending.approvals` pausa a tarefa e aparece no painel; (3) testes PT-006 com conteúdo malicioso.

### D-06 — Acesso privado e celular (PT-003, NFR-001, NFR-008)

- Decision: **somente rede local de casa.** O Spring Boot escuta em `0.0.0.0:8443` **com HTTPS** (certificado de CA local gerada com `mkcert`, CA instalada no celular) e regra de firewall do Windows restrita ao perfil **Privado** e à sub-rede local. Login próprio do app: senha única definida no primeiro uso (hash Argon2id), sessão em cookie `HttpOnly; Secure; SameSite=Strict`, expiração por inatividade, limitação de tentativas de login, CSRF por token sincronizado.
- Rationale: escolha do usuário. HTTPS na LAN impede leitura da senha/sessão por outros dispositivos da rede; `Secure` cookie exige HTTPS. O OpenBot site hosting é **público** (skill oficial: "Hosting is public", expira em 30 dias) e não pode hospedar dados privados.
- Alternatives considered: HTTP simples na LAN (credencial exposta a qualquer dispositivo da rede); Tailscale e Cloudflare Tunnel (não escolhidos pelo usuário; podem ser adicionados depois sem mudar a aplicação).
- Consequência declarada: fora de casa o painel fica inacessível; o painel deve mostrar isso como limitação, não como falha. Acesso móvel ao OpenBot não implica acesso ao painel (NFR-008).

### D-07 — Gmail (PT-005, NFR-002)

- Decision: backend integra **diretamente a Gmail API** com OAuth de aplicativo instalado (loopback redirect), projeto Google Cloud próprio do usuário. Escopo mínimo que cobre marcador em mensagem: `gmail.modify` (ler metadados/conteúdo, aplicar marcador, criar rascunho, enviar). Não solicitar `https://mail.google.com/` (exclusão permanente). Refresh token cifrado em repouso com Windows DPAPI (usuário atual), nunca em logs.
- Descoberta minimizada: consulta de servidor Gmail com `after:<início do acompanhamento>` + remetentes/palavras-chave ligados a candidaturas conhecidas e alertas de vagas; obter primeiro `format=metadata` (From, Subject, Date) e baixar corpo só quando houver candidatura plausível. Mensagens descartadas por relevância guardam apenas `gmail_message_id` + motivo (para não reprocessar).
- Envio: somente pelo backend, a partir de um `Draft` com `Approval` cujo hash cobre destinatários + assunto + corpo; qualquer edição invalida a aprovação. Envio via outbox com chave idempotente; antes de reenviar após falha incerta, procurar a mensagem em "Enviados" pelo cabeçalho `X-Curriculotron-Outbox-Id`.
- Alternatives considered: Gmail via Composio no OpenBot (terceiro adicional recebendo a caixa postal; enforcement de aprovação fora do nosso controle) — rejeitado. `gmail.readonly` + `gmail.send` + `gmail.labels` não permite aplicar marcador em mensagem — insuficiente para FR-012.
- A validar na prova: `gmail.modify` é escopo restrito. Em projeto no modo "Testing" o refresh token expira em 7 dias (reconexão semanal visível no painel). Verificar se publicar o app como "In production" não verificado, uso pessoal, elimina a expiração sem exigir verificação; caso contrário, manter Testing e documentar reconexão semanal como limitação aceita.

### D-08 — Fontes de vagas (PT-004)

Matriz inicial. Nada aqui contorna login, CAPTCHA, robots ou termos; "restrito" significa uso permitido porém incompleto.

| Fonte | Mecanismo V1 | Classificação | Validação pendente |
| --- | --- | --- | --- |
| LinkedIn | Sem API pública de busca para indivíduos; scraping proibido pelos termos. Usar e-mails de **alerta de vagas do LinkedIn** já recebidos no Gmail + importação manual de URL/texto colado. | Restrito | Formato dos alertas; extração de link e título |
| Gupy | Páginas públicas de carreira por empresa (`<empresa>.gupy.io`) e portal público de vagas. Coleta por HTTP simples, baixa frequência (1×/dia + manual), identificando o cliente. | Restrito | Termos de uso da Gupy; estabilidade do formato; quais empresas prioritárias usam Gupy |
| Sites das empresas | Adaptador por empresa: se usar ATS com board público oficial (Greenhouse, Lever, Workable, SuccessFactors), usar o feed público; senão, página de carreiras via HTTP. | Restrito | Levantar ATS de PicPay, BV, CWI, DB, CI&T, SAP |
| Entrada manual | Colar URL ou texto de vaga no painel. | Viável | — |

- Rationale: atende FR-003 com fontes permitidas, registra cobertura parcial (FR-017, US5-2). Coleta é feita pelo backend (Java `HttpClient`), não pelo agente, para manter o conteúdo externo como dado (D-05).
- Alternatives considered: navegação do agente OpenBot nos portais (risco de injeção com ferramentas amplas; reservado para uso explícito e futuro).

### D-09 — Deduplicação e evidência (PT-006)

- Identidade forte: `(fonte, id_externo)` ou URL canônica normalizada. Identidade fraca: hash normalizado de empresa + título + localização ⇒ apenas **sugere** fusão (`OpportunityMatchCandidate`), nunca funde automaticamente.
- Mudança de estado de candidatura por e-mail exige `Evidence` com nível `explicit` (texto da mensagem que afirma o fato) e associação única; ambiguidades criam `ReviewItem`.
- Correção manual cria `StatusChange` com `author=user`; evidência automática posterior conflitante não sobrescreve, gera `ReviewItem` de conflito.
- Conteúdo externo é sempre enviado ao modelo dentro de bloco delimitado como dado; a saída é validada contra JSON Schema e enum de ações permitidas; campos fora do schema são descartados.

### D-10 — Agendamento, retomada e limites (PT-007, FR-018)

- `Routine` persistida com `next_run_at` em America/Sao_Paulo; tick de 1 minuto (Spring `@Scheduled`) lê rotinas vencidas. Em atrasos (computador desligado), **colapsa** ocorrências perdidas em uma execução e registra as não executadas (Edge case de reinício).
- `TaskRun` com checkpoint por etapa e chave idempotente `routine_id + data local`; teto diário de 10 seleções calculado em transação sobre `DailySelection` (compartilhado entre manual e agendado).
- Chamada OpenBot: `clientMessageId = taskRunId + ':' + etapa` ⇒ reenvio após reinício não duplica. Estados `pending` (aguarda pessoa) ⇒ `TaskRun.paused(reason=needs_person)`; 429/limite de taxa/falha de cota ⇒ `paused(reason=provider_limit)`, sem troca de provedor ou modelo.
- Política de tentativas: fontes HTTP 3 tentativas com backoff exponencial; OpenBot não repete `failed` automaticamente (usuário retoma); envio de e-mail nunca repete sem reconciliação.

### D-11 — Dados e provedores (PT-008)

| Dado | Onde fica | Sai do computador para |
| --- | --- | --- |
| Perfil/currículo | SQLite | Agente OpenBot (Codex/OpenAI) somente trechos necessários à tarefa |
| Vagas | SQLite | Agente OpenBot para ranking/explicação |
| Mensagens Gmail | Metadados + corpo apenas de relevantes, SQLite | Agente OpenBot para classificação de mensagens relevantes |
| Tokens Gmail | Cifrados (DPAPI) em arquivo | Google apenas |
| Token OpenBot | Lido do arquivo a cada uso, só em memória | Loopback local |
| Senha do app | Hash Argon2id | — |

- Execução local **não** é inferência local: o conteúdo enviado ao agente vai ao provedor configurado no OpenBot. O painel exibe esse inventário.
- Retenção: corpo de e-mail relevante mantido enquanto a candidatura estiver ativa + 90 dias após encerramento (ajustável); exclusão manual por candidatura e "apagar tudo" (inclui revogar token Gmail). Backup diário rotativo (7 cópias) do arquivo SQLite em `%LOCALAPPDATA%\Curriculotron\backups`.
- Analytics do OpenBot: existe preferência própria do app (`openbot-analytics-preference-v1.json`); recomendação ao usuário de revisá-la — fora do controle deste sistema.

### D-12 — Testes

- Backend: JUnit 5, Mockito, AssertJ, Spring Boot Test; **WireMock** para OpenBot Local scripts e Gmail API; SQLite real em diretório temporário para testes de integração e de interrupção.
- Frontend: Vitest + Testing Library; **Playwright** para jornadas J1–J6 em 360 px e 1280 px, incluindo verificação de rolagem horizontal e navegação por teclado; axe-core para acessibilidade.
- Contratos: testes que validam fixtures de saída de bots contra os JSON Schemas de `contracts/bot-task-outputs.md`.

## Status das investigações

| ID | Classificação | Evidência | Restante |
| --- | --- | --- | --- |
| PT-001 | **Restrito — interface identificada, não demonstrada** | API Local scripts e CLI oficial; setup com provedor `codex` concluído | Prova ao vivo (quickstart §1) |
| PT-002 | **Restrito** | Rotas de agentes, entregas, pendências e settings; sem rota de instruções de sistema | Prova ao vivo; ciclo de revisão é do nosso app (D-05) |
| PT-003 | **Viável (desenho)** | Site hosting do OpenBot é público ⇒ painel próprio; LAN + HTTPS + login | Teste no celular (quickstart §5) |
| PT-004 | **Restrito** | Matriz D-08 | Termos Gupy; ATS das empresas |
| PT-005 | **Restrito** | Escopos oficiais; fluxo D-07 | Conexão real, expiração de token, revogação |
| PT-006 | **Viável (desenho)** | Regras D-09 | Casos controlados na implementação |
| PT-007 | **Viável (desenho)** | D-10; idempotência nativa do OpenBot | Testes de interrupção |
| PT-008 | **Viável (desenho)** | Inventário D-11 | — |

Nenhuma limitação encontrada impede requisito central: LinkedIn restrito a alertas e entrada manual é cobertura parcial explicitamente permitida por FR-003 ("fontes disponíveis e permitidas"), mas o usuário deve saber disso (registrado no plano).

## Gate restante

A prova ao vivo PT-001/PT-002 (quickstart §1) é **pré-requisito bloqueante da primeira tarefa que integra o OpenBot**. Se falhar (ex.: Codex indisponível via Local scripts, cobrança adicional, limite incompatível), a decisão volta ao usuário como mudança de escopo; não haverá troca silenciosa de provedor.

## Prova ao vivo PT-001/PT-002 — 2026-11-14 16:02 (America/Sao_Paulo)

Ambiente: OpenBot 0.70.0 local; agentes importados do zip `openbot-agents/` (formato `openbot-agent-import` v1); Local scripts ligado só em **Dohko de Libra** (`agent-a0d9bcd6-…`), provedor Codex. CLI oficial executada com o Bun embutido no OpenBot (`%APPDATA%\OpenBot\provider-runtimes\bun\win32-x64\1.4.4\bin\bun.exe`), pois não há Node instalado. O token não foi lido nem exibido.

| Passo | Resultado |
| --- | --- |
| `list --json` | exit 0; só Dohko listado (agentes sem Local scripts ficam ocultos, como esperado) |
| `run … --wait --id prova-001` | exit 0, `status: completed`, `answer: {"ok": true}`, ~4 s |
| Repetição com a mesma `--id prova-001` | exit 0, **mesmos** `messageId` e `deliveryId` ⇒ idempotência confirmada, sem novo turno |
| Pasta `automation` | contém `url`, `token` e também `headers` (não documentado na CLI; não lido) |

Conclusão: PT-001 e PT-002 passam de "restrito — não demonstrado" para **viável**: o backend consegue enviar tarefa, aguardar e receber a resposta de um agente Codex pela API Local scripts, com reenvio idempotente. Pendências menores: (1) confirmar recusa (exit 5) com Local scripts desligado; (2) usuário confirmar que não houve cobrança extra; (3) runtime da CLI pode ser o Bun embutido, então Node não é requisito para diagnóstico. A T025 está atendida para fins de gate.

### Validação do incremento frontend — 2026-11-14

- Implementadas as telas principais com o visual local já existente: Hoje, Vagas/detalhe, Duplicatas, Candidaturas/detalhe, Revisão, Atividades, Integrações, Perfil, Critérios, Empresas e consulta de Dados.
- `tsc -b`, build Vite, ESLint e Prettier: aprovados.
- Vitest: 23 testes aprovados (5 arquivos), incluindo origem dos dados de vaga, inscrição explícita, restauração, erro de API e urgência de prazos no fuso de São Paulo.
- Playwright/Chromium: 32 testes aprovados em 360 e 1280 px; 13 telas verificadas com axe-core, largura dos elementos, foco pelo link de pular conteúdo e ausência de erros de JavaScript. Fluxos de inscrição, correção de candidatura/próxima ação e privacidade sem sessão incluídos.
- Encontrado e corrigido no logo: falta de espaço no texto entre Curriculotron e 2000 gerava divergência com o nome acessível.
- Fixtures de API existem somente em testes. Endpoints de domínio ainda pendentes; não foi validada integração ponta a ponta com backend, OpenBot ou Gmail. T036/T065/T091/T105 continuam abertas para os checkpoints integrados e a cobertura restante.
- Execução reproduzível e limites documentados em `frontend/README.md`; formatos JSON detalhados em `contracts/panel-api.md`.

## Piloto T000c — busca via Dohko → Aiolos — 2026-11-14

Pedidos enviados ao Dohko pela CLI Local scripts (`--id piloto-t000c-busca-001`, `piloto-t000c-criterios-001`).

- **Delegação assíncrona**: no primeiro turno o Dohko encaminhou ao Aiolos e respondeu sem o resultado. O resultado só apareceu numa segunda pergunta. Lacuna para o backend: delegar não devolve o resultado no mesmo `run`, então é preciso acompanhar ou perguntar de novo (relevante para T029/T032).
- **Seleção vazia**: o Aiolos não confirmou nenhuma vaga com link e trecho.
- **Fonte não permitida**: as pistas vieram do Indeed (Iterative, Mirante, SeedTS, CI&T só sênior, PicPay presencial em SP, CWI) e ficaram sem verificação. A regra de fontes foi respeitada no resultado, mas o bot gastou esforço numa fonte fora da lista.
- **URLs de carreira com 404** para CI&T, PicPay, BV e CWI, enquanto o backend lê essas mesmas empresas por endpoints verificados (`jobs.lever.co/bv`, `jobs.lever.co/ciandt`, Oracle `CX_3001`, `cwi.com.br/talentos/oportunidades`, `db.gupy.io`). Ação concluída: endpoints e restrição contra agregadores registrados na skill `busca-vagas`.
- **Critérios atualizados** pelo chat (DB no lugar de Digital Business; júnior na SAP é compatível) foram aceitos e guardados na memória. Salário mínimo mantido.
- Pendentes do T000c: candidatura com Mu e simulação com Camus.

### Ajuste de produto após revisão do piloto

O usuário confirmou que a busca do Aiolos funcionou corretamente, apesar das ressalvas registradas acima. A seleção vazia não significa falha do fluxo de busca. Preferência atual: a busca manual deve cobrir por padrão vagas publicadas nos últimos 30 dias e permitir que o usuário escolha outro intervalo; rotina diária automática fica desligada até haver uma base suficiente e o usuário decidir habilitá-la. A skill do bot foi atualizada; a implementação do painel/API permanece pendente.

### Implementação US1 — janela de publicação

Busca manual aceita datas inicial/final inclusivas e usa os últimos 30 dias por padrão. Datas ausentes ou inválidas não são substituídas pela consulta; vagas assim ficam identificadas como data desconhecida. Datas disponíveis de Lever, Gupy e Oracle são extraídas. Rotina de busca começa desabilitada, inclusive em instalações existentes, e pode ser habilitada na tela Vagas. Endpoints: GET/PUT /api/settings/search-routine. O período filtra a coleta e as novas inclusões na seleção; seleções anteriores permanecem preservadas. Fontes podem não expor a data, limitando a filtragem temporal dessas vagas.

Validação: compilação Java e build frontend aprovados; suíte backend aprovada, incluindo três testes novos de intervalo e datas; 23 testes frontend aprovados; ESLint aprovado. Sem validação ao vivo de novas buscas nas fontes nesta rodada.
