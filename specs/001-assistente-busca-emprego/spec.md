# Feature Specification: Assistente pessoal de busca de emprego

**Feature Branch**: Não criada; workspace sem repositório Git.

**Created**: 2026-11-14

**Status**: Draft — especificação validada para planejamento; integrações ainda não validadas.

**Input**: Especificação solicitada pelo usuário para assistente pessoal local, gerenciado por bots, integrado ao OpenBot, com interface web privada e revisão diária de aproximadamente dez minutos.

## User Scenarios & Testing *(mandatory)*

As prioridades indicam ordem de entrega; todas as jornadas integram a V1. Os testes independentes podem usar vagas e mensagens de exemplo, sem depender de acesso real aos portais.

### User Story 1 - Revisar oportunidades e próximas ações (Priority: P1)

Como candidato com pouco tempo, quero abrir o painel no computador ou celular e decidir quais vagas disputar e quais pendências atender.

**Why this priority**: Entrega o principal benefício: reduzir o trabalho diário de organização.

**Independent Test**: Carregar oportunidades conhecidas, algumas duplicadas, descartadas ou já vinculadas a candidaturas, e completar uma revisão.

**Acceptance Scenarios**:

1. **Given** 15 vagas compatíveis inéditas, **When** a seleção diária é produzida, **Then** aparecem no máximo dez, com as demais acessíveis na lista secundária.
2. **Given** três vagas compatíveis e sete fracas, **When** a busca termina, **Then** somente as três compatíveis entram na seleção, sem preenchimento artificial.
3. **Given** vagas repetidas, descartadas e já disputadas, **When** ocorre nova busca manual ou diária, **Then** elas não voltam como novas recomendações.
4. **Given** uma vaga sem remuneração e com requisito inferido, **When** abro seu detalhe, **Then** vejo “salário a confirmar”, a inferência separada dos fatos, fonte, link e data de consulta.
5. **Given** entrevistas, respostas e vagas pendentes, **When** abro a página inicial, **Then** vejo vagas para avaliar, candidaturas com próximas ações e respostas que exigem atenção, com prazos próximos destacados.
6. **Given** que solicito uma busca manual sem escolher datas, **When** ela consulta as fontes, **Then** prioriza vagas publicadas nos últimos 30 dias; posso informar outro período nessa busca.
7. **Given** que a data de publicação não está disponível na fonte, **When** a vaga aparece, **Then** ela fica identificada como data desconhecida e não é apresentada como publicada dentro do período escolhido.

### User Story 2 - Criar, revisar e controlar bots por conversa (Priority: P1)

Como usuário, quero explicar uma função ligada à busca de emprego, revisar a proposta do criador e compreender a atuação dos bots.

**Why this priority**: O criador conversacional é parte central do produto e torna a autonomia compreensível e controlável.

**Independent Test**: Solicitar um bot de busca de vagas Java, revisar a proposta, ativar, pausar e consultar uma atividade registrada.

**Acceptance Scenarios**:

1. **Given** um pedido de bot de emprego, **When** converso com o criador, **Then** ele esclarece lacunas e propõe função, instruções, ferramentas, limites e rotinas consultáveis antes da ativação.
2. **Given** uma proposta ainda não revisada, **When** chega o horário da rotina, **Then** o bot não executa; após aprovação explícita pode executar apenas o que foi aprovado.
3. **Given** um bot ativo, **When** peço alteração de ferramentas ou instruções, **Then** a nova configuração exige revisão antes de entrar em vigor.
4. **Given** um pedido fora de emprego, **When** solicito criação, **Then** o criador informa o limite da V1 e não ativa esse bot.
5. **Given** uma tarefa distribuída pelo coordenador, **When** consulto a atividade, **Then** vejo responsáveis, ação, fontes, resultado, falhas e pendências, sem exigir raciocínio interno.

### User Story 3 - Registrar e corrigir candidaturas (Priority: P1)

Como candidato, quero realizar a candidatura no portal e manter um histórico confiável no assistente.

**Why this priority**: Evita confundir intenção com envio e perder o controle do processo.

**Independent Test**: Abrir um link, confirmar manualmente a candidatura, registrar evidência e corrigir um status.

**Acceptance Scenarios**:

1. **Given** uma vaga recomendada, **When** abro seu link, **Then** nenhuma candidatura é considerada enviada.
2. **Given** envio realizado no portal, **When** aciono “Me candidatei”, **Then** a candidatura aparece no acompanhamento, com data e origem manual da confirmação.
3. **Given** uma associação ou status incorreto, **When** corrijo manualmente, **Then** o painel reflete a correção e preserva o histórico da alteração.
4. **Given** uma candidatura sem resposta, **When** o tempo passa, **Then** ela continua sem resposta e não vira rejeição automaticamente.
5. **Given** material preparado para uma vaga, **When** reviso o conteúdo, **Then** as afirmações são sustentadas pelo currículo ou informação explícita do usuário, sem inventar resultados ou competências.

### User Story 4 - Acompanhar Gmail e responder com controle (Priority: P1)

Como candidato, quero identificar confirmações, contatos, entrevistas e resultados sem procurar manualmente em toda a caixa postal.

**Why this priority**: Reduz perdas de prazos e centraliza evidências.

**Independent Test**: Usar mensagens de candidaturas novas e antigas, incluindo mensagens ambíguas, e revisar os vínculos e rascunhos.

**Acceptance Scenarios**:

1. **Given** autorização válida e início de uso registrado, **When** chega confirmação de uma candidatura nova, **Then** a mensagem recebe marcador dedicado, é vinculada quando houver evidência suficiente e atualiza o histórico.
2. **Given** mensagem sobre candidatura anterior ao início de uso, **When** ela chega, **Then** não é importada como candidatura acompanhada; data recente da mensagem não torna a candidatura nova.
3. **Given** duas candidaturas plausíveis para uma mensagem, **When** ela é interpretada, **Then** fica pendente de revisão sem alterar arbitrariamente uma candidatura.
4. **Given** convite com prazo explícito, **When** ele é associado, **Then** a próxima ação e o prazo aparecem no painel com referência à mensagem.
5. **Given** um rascunho para recrutador, **When** falta aprovação explícita do destinatário e conteúdo, **Then** não há envio; alterações após aprovação exigem nova aprovação.
6. **Given** nenhuma novidade nem ação útil, **When** chega a rotina de resumo, **Then** nenhum e-mail vazio é enviado; havendo conteúdo útil e resumo habilitado, o usuário recebe novidades e próximas ações.

### User Story 5 - Configurar perfil e operar com falhas visíveis (Priority: P1)

Como usuário único da instalação, quero revisar meu perfil, configurar prioridades e saber se os dados estão atualizados.

**Why this priority**: Recomendações dependem de critérios corretos e da visibilidade das limitações locais.

**Independent Test**: Editar perfil e empresas, simular indisponibilidade de fonte, desligamento e limite do provedor.

**Acceptance Scenarios**:

1. **Given** o currículo inicial, **When** reviso o perfil e edito empresas, **Then** as próximas buscas usam os critérios salvos e preservam a origem das informações profissionais.
2. **Given** uma fonte indisponível e outra disponível, **When** a busca termina, **Then** vejo resultados parciais e a fonte não consultada, sem indicação de cobertura completa.
3. **Given** limite do provedor, **When** uma tarefa é interrompida, **Then** vejo motivo, trabalho concluído e pendente e opção de retomada, sem cobrança adicional ou troca automática para serviço pago.
4. **Given** período com computador desligado, **When** retorno ao sistema, **Then** vejo a última atualização real e as rotinas não executadas; retomadas não duplicam registros.
5. **Given** visitante não autorizado no computador ou celular, **When** tenta acessar painel, arquivos ou mensagens, **Then** não recebe dados privados.

### User Story 6 - Preparar entrevista para uma vaga (Priority: P2)

Como candidato, quero treinar uma entrevista técnica ou comportamental com base na vaga e no meu currículo.

**Why this priority**: Aumenta a preparação direcionada, separada do tempo de revisão diária.

**Independent Test**: Escolher uma vaga e realizar uma simulação curta em português e outra em inglês.

**Acceptance Scenarios**:

1. **Given** vaga escolhida, idioma e modalidade definidos, **When** inicio a simulação, **Then** recebo uma pergunta por vez, com feedback ao encerrar.
2. **Given** respostas e requisitos da vaga, **When** a sessão termina, **Then** recebo pontos de melhoria, assuntos, exercícios e perguntas vinculados à vaga.
3. **Given** competência sugerida para estudo, **When** consulto meu perfil e currículo, **Then** ela não aparece automaticamente como adquirida; experiência documentada, conhecimento informado e lacuna não avaliada permanecem distintos.

### Edge Cases

- Nenhuma vaga compatível: mostrar seleção vazia com fontes e limitações, sem fabricar oportunidades.
- Mesma vaga em vários portais: consolidar fontes e manter uma oportunidade; identidade incerta exige sinalização, sem fusão destrutiva.
- Vaga encerrada: sinalizar e retirar da seleção ativa, preservando eventual candidatura. Disponibilidade não confirmada: aviso explícito, sem afirmar encerramento.
- Remuneração em unidade ou regime ambíguo: não presumir equivalência aos mínimos mensais; solicitar confirmação.
- Gmail desconectado ou autorização revogada: interromper processamento, manter histórico e mostrar ação para reconectar.
- Mensagem sem candidatura identificável: manter pendente de revisão de relevância e vínculo; não criar candidatura antiga por inferência.
- Evidências conflitantes ou mensagem tardia: registrar conflito e solicitar revisão, sem sobrescrever silenciosamente correção manual.
- Datas sem fuso ou prazo ambíguo: sinalizar incerteza; não inventar horário preciso.
- Reinício durante tarefa ou entrega de e-mail incerta: preservar progresso e reconciliar resultado antes de repetir ação externa.
- Vaga ou e-mail contendo instruções aos bots: tratar como conteúdo externo, sem mudar ferramentas, permissões ou destinatários.
- Computador inacessível: site pode ficar indisponível; se dados anteriores estiverem visíveis, exibir sua data e não apresentá-los como atuais.

## Requirements *(mandatory)*

### Functional Requirements

Cada requisito é verificado pelas jornadas indicadas e pela inspeção de seus resultados observáveis.

- **FR-001 (J5)**: Importar o perfil de `curriculo-en-draft.md` e permitir revisão e atualização de critérios e informações, preservando origem e distinção entre informação documentada e declarada pelo usuário.
- **FR-002 (J1, J5)**: Aplicar o perfil inicial e regras de negócio abaixo; permitir editar empresas prioritárias e consultar sugestões em lista secundária sem promovê-las automaticamente.
- **FR-003 (J1)**: Executar busca sob demanda com fontes disponíveis e permitidas, incluindo investigação de Gupy, LinkedIn e sites das empresas. O período padrão cobre os 30 dias anteriores; cada busca manual pode receber datas inicial e final. Registrar fontes consultadas, falhas e limitações. Busca diária automática fica desabilitada por padrão até o usuário habilitá-la.
- **FR-004 (J1)**: Comparar, deduplicar e priorizar vagas; selecionar de cinco a dez novas oportunidades quando houver compatibilidade suficiente, aceitar menos e nunca ultrapassar dez novas seleções no mesmo dia, inclusive em buscas manuais.
- **FR-005 (J1, J3)**: Manter na lista secundária apenas oportunidades compatíveis excedentes e pendências sem incompatibilidade demonstrada (excluindo incompatibilidades demonstradas de função ou tecnologia de ambas as listas), além de excluir descartadas e vagas vinculadas a candidaturas de futuras recomendações, salvo reversão explícita do usuário.
- **FR-006 (J1)**: Exibir título, empresa, localização, modalidade, requisitos, salário disponível, fonte, link, data de consulta, disponibilidade e motivo da recomendação. Separar fatos, ausências e inferências.
- **FR-007 (J2)**: Oferecer criador conversacional restrito a emprego que proponha função, instruções, ferramentas, limites e rotinas; permitir revisar, editar, ativar e pausar bots. Ativação inicial e alterações de configuração dependem de revisão explícita.
- **FR-008 (J2)**: Disponibilizar propostas iniciais de Coordenador (pedidos e distribuição), Busca de vagas (descoberta e priorização), Acompanhamento (candidaturas e Gmail) e Preparação (simulação e revisão direcionada), sujeitas à mesma revisão.
- **FR-009 (J2)**: Permitir conversar com coordenador e bots, solicitar buscas, ajustar critérios e obter justificativas objetivas e registros de atividades, sem exposição obrigatória de raciocínio interno.
- **FR-010 (J3)**: Fornecer link e materiais revisáveis, ações “Me candidatei” e “Descartar”; nunca enviar candidatura nem preencher portal automaticamente.
- **FR-011 (J3, J4)**: Manter histórico de status, próximas ações, evidências e mensagens por candidatura; aceitar correção manual de vínculos e estados e registrar autoria e data das mudanças.
- **FR-012 (J4)**: Autorizar conexão ao Gmail, registrar início do acompanhamento e processar apenas mensagens necessárias a candidaturas novas; organizar mensagens relevantes com marcador dedicado.
- **FR-013 (J4)**: Identificar confirmação, recrutador, entrevista, prazo e resultado; atualizar somente com evidência suficiente e encaminhar associações ou interpretações incertas à revisão.
- **FR-014 (J4)**: Preparar rascunhos e exigir aprovação explícita do conteúdo e destinatários antes de cada envio a terceiros; nenhuma rotina pode dispensar esse controle.
- **FR-015 (J4)**: Oferecer resumo por e-mail ao próprio usuário, habilitável, com novidades e próximas ações úteis; suprimir resumos vazios e destacar prazos e entrevistas próximos.
- **FR-016 (J1–J5)**: Oferecer página inicial focada em vagas, candidaturas/ações e respostas; acesso a histórico, conversa, criação/configuração de bots, atividades/fontes, perfil/empresas e estado das integrações.
- **FR-017 (J5)**: Mostrar última atualização bem-sucedida por rotina/integração, última tentativa, falhas, pausas e pendências. Uma tentativa malsucedida não deve atualizar a data de sucesso.
- **FR-018 (J5)**: Pausar tarefas dependentes de provedor ao atingir limites, informar motivo e permitir retomada preservando progresso; não contratar consumo nem migrar automaticamente para serviço pago.
- **FR-019 (J6)**: Simular entrevistas por texto, técnicas ou comportamentais, em português ou inglês, com uma pergunta por vez e feedback final baseado na vaga, currículo e respostas.
- **FR-020 (J6)**: Recomendar revisão, exercícios e perguntas específicos à vaga, diferenciando experiência documentada, conhecimento declarado e lacunas ainda não avaliadas, sem promover estudos recomendados a competências adquiridas.

### Regras de negócio confirmadas

- **RB-001 — Perfil**: Carga inicial configurável (seed): desenvolvedor pleno com foco backend Java e experiência fullstack; alvo inicial fullstack/backend pleno Java com Java, Java 17, Spring Boot, Angular, React, REST, Kafka, microsserviços, JUnit e Mockito. Os valores editados e persistidos em `/criterios` (`targetRoles` e `stackKeywords`) passam a governar tanto novas buscas (manuais e por rotina) quanto a reavaliação do acervo, sem fixar Java após alteração pelo usuário.
- **RB-002 — Localização**: Preferir remoto. Considerar híbrido na região metropolitana de Porto Alegre, ponderando empresa, remuneração, crescimento e deslocamento, sem limite fixo de dias presenciais. Não presumir disponibilidade para mudança ou trabalho presencial fora desse recorte.
- **RB-003 — Contratação**: Preferir CLT com bruto mensal mínimo de R$ 6.000; PJ é alternativa menos desejada, mínimo de R$ 9.000 mensais. Salário ausente não exclui, mas exige “salário a confirmar”. Valores explicitamente abaixo do mínimo não entram na seleção principal. Faixa que cruza o mínimo exige confirmação de oferta compatível.
- **RB-004 — Flexibilidade**: Considerar júnior se salário, empresa e crescimento justificarem, registrando o motivo. Justificativa por empresa definida pelo usuário: na SAP, júnior vale a pena (menos carga, salário maior) — 2026-11-14. Aceitar exigência de inglês sem inferir fluência não informada. Ausência de evidência de competência é lacuna, não experiência inventada.
- **RB-005 — Empresas**: Prioridades iniciais: PicPay, BV, CWI, DB (db.tec.br, vagas em db.gupy.io), CI&T e SAP. Registrar experiência prévia na CWI e interesse em retornar, sem presumir contratação preferencial. Sugerir grandes empresas brasileiras e similares em lista secundária editável.
- **RB-006 — Veracidade**: Não inventar experiências, resultados, métricas, conhecimentos ou qualificações. Preparar materiais não implica alterar automaticamente o currículo original.
- **RB-007 — Evidência**: Clique em link não confirma envio; ausência de resposta não significa rejeição. Atualizações automáticas devem apontar sua evidência. Informação incerta deve ficar visível e corrigível.
- **RB-008 — Autonomia**: Pesquisa, classificação, organização, atualização sustentada por evidência e preparação de materiais podem ocorrer automaticamente. Candidatura é feita pelo usuário no portal; mensagens externas e ativação de bots dependem dos controles definidos.
- **RB-009 — Fronteira temporal**: Acompanhar somente candidaturas iniciadas a partir do início do uso. Mensagens posteriores sobre candidaturas antigas continuam fora do escopo.
- **RB-010 — Instalação**: Uma pessoa por instalação local; futura cópia para amigo deve usar contas, currículo e dados independentes. Computador fica ligado aproximadamente 90% do tempo; isso é contexto, não garantia de disponibilidade.

### Requisitos não funcionais

- **NFR-001 — Privacidade (J5)**: Acesso privado no computador e celular; testes sem autorização devem negar dados, arquivos e mensagens. Credenciais não devem aparecer em páginas, conversas, materiais ou registros de atividade.
- **NFR-002 — Minimização (J4)**: Ler e reter somente conteúdo necessário ao acompanhamento; mensagens alheias à busca não devem compor histórico nem resumo. O planejamento deve especificar como restringir descoberta, processamento e retenção.
- **NFR-003 — Segurança de instruções (J2, J4)**: Conteúdo externo não pode alterar permissões nem comandar bots. Testar vagas e e-mails com pedidos de envio, exposição de credenciais e ativação de ferramentas; nenhuma ação deve ser autorizada por esse conteúdo.
- **NFR-004 — Continuidade (J3–J5)**: Reinícios e retomadas devem preservar decisões confirmadas, evidências e pendências, sem duplicar candidaturas, mensagens ou recomendações. Testar interrupção antes e depois de uma atualização.
- **NFR-005 — Usabilidade (J1–J6)**: Fluxos principais utilizáveis por toque e teclado, com rótulos legíveis e estados não comunicados apenas por cor. Validar em larguras de 360 e 1280 pixels sem perda de ações essenciais nem rolagem horizontal da página.
- **NFR-006 — Responsividade (J1, J5)**: Meta assumida: apresentar dados locais já disponíveis em até três segundos em pelo menos 95% de 20 aberturas no ambiente do piloto. Buscas demoradas devem mostrar andamento sem bloquear consulta ao histórico.
- **NFR-007 — Auditabilidade (J2–J5)**: Cada ação automatizada que muda dados deve registrar bot, horário, ação, fonte/evidência, resultado e eventual falha, sem registrar segredos ou exigir raciocínio interno.
- **NFR-008 — Operação local (J5)**: Não prometer execução com computador desligado; retomada deve distinguir execução pendente de concluída. Acesso pelo celular ao OpenBot não comprova acesso ao novo site.

### Key Entities *(include if feature involves data)*

- **Perfil de busca**: currículo e versões revisadas, fatos profissionais com origem, preferências, critérios e empresas prioritárias/secundárias.
- **Bot e configuração revisada**: função, instruções, ferramentas, limites, rotinas, estado e revisão que autorizou ativação.
- **Conversa e pedido**: interlocutor, mensagens, solicitação e tarefas relacionadas.
- **Tarefa/execução**: responsável, gatilho diário/manual, progresso, datas, fontes, resultado, falha e motivo de pausa.
- **Oportunidade**: identidade consolidada, dados da vaga, fontes, disponibilidade, compatibilidade, incertezas e decisão do usuário.
- **Seleção diária**: dia, vagas inéditas priorizadas e motivos; limitada a dez inclusões diárias.
- **Candidatura**: oportunidade, confirmação de envio, data, estado atual, histórico, evidências e próximas ações.
- **Mensagem e associação**: origem, conteúdo necessário, data, marcador, candidatura vinculada ou pendência de revisão.
- **Rascunho e aprovação**: destinatários, conteúdo revisado, autorização específica e estado de envio.
- **Integração**: autorização, início do acompanhamento, última tentativa/sucesso e condições de falha ou pausa, sem segredos visíveis.
- **Sessão de preparação**: vaga, perfil usado, idioma, tipo, perguntas/respostas, feedback e recomendações.

## Success Criteria *(mandatory)*

### Measurable Outcomes

Piloto de duas semanas. Metas numéricas não fornecidas no pedido são hipóteses de avaliação explicitadas abaixo; não são alegações de resultados já obtidos. Ausência de eventos reais deve ser registrada e complementada por cenários controlados, sem afirmar validação real inexistente.

- **SC-001**: Mediana da revisão diária em até dez minutos nas sessões do piloto, excluindo candidaturas nos portais e treino de entrevistas; registrar duração e pendências restantes.
- **SC-002**: Todas as vagas selecionadas respeitam critérios objetivos conhecidos e sinalizam ausências; meta assumida de pelo menos 70% das recomendações avaliadas como “consideraria disputar” pelo usuário, com quantidade e motivos de recusa registrados.
- **SC-003**: Zero duplicatas reconhecidas, descartes ou vagas já vinculadas a candidaturas reaparecem como novas na seleção; nenhum dia ultrapassa dez novas seleções.
- **SC-004**: Todas as mensagens relevantes avaliadas no piloto e nos casos controlados estão corretamente vinculadas ou explicitamente pendentes de revisão; zero mudança automática sem evidência identificável.
- **SC-005**: Usuário consegue consultar a origem de uma recomendação, inspecionar a configuração de um bot e corrigir vínculo/status em todos os respectivos cenários de aceitação, sem editar arquivos internos.
- **SC-006**: Ao menos uma simulação técnica e uma comportamental, cobrindo português e inglês, passam pelos critérios de uma pergunta por vez e feedback relacionado à vaga, sem acréscimo de competências ao currículo.
- **SC-007**: Todos os testes de falha de fonte, revogação do Gmail, limite de uso e interrupção local mostram estado e atualização real corretos; retomadas não duplicam efeitos.
- **SC-008**: Zero candidaturas automáticas, mensagens externas sem aprovação específica, bots ativados sem revisão ou gastos adicionais automáticos em todo o piloto e nos testes de controle.
- **SC-009**: Uma busca sem período explícito usa os 30 dias anteriores; um período manual escolhido pelo usuário é aplicado e datas de publicação desconhecidas ficam identificadas como desconhecidas.

## Assumptions

- Não há dúvidas de produto bloqueantes para escrever a especificação. Questões de mecanismo e viabilidade permanecem no planejamento, sem pressupor respostas positivas.
- A [constituição do projeto](../../.specify/memory/constitution.md) (v1.0.0, ratificada em 2026-11-15) estabelece os princípios técnicos vigentes aplicáveis a novas alterações e à revisão posterior de conformidade do legado.
- O currículo disponível é fonte inicial, não prova de competências além do texto. Atualidade das informações deve ser revisada pelo usuário.
- Para tornar o comportamento testável, assume-se dia e horários locais em America/Sao_Paulo. Busca manual usa os 30 dias anteriores por padrão e permite definir outro período; rotina diária automática começa desabilitada e só deve ser habilitada quando o usuário decidir que já há base suficiente. Buscas adicionais alimentam a lista secundária depois do teto diário, sem apagar a seleção já avaliada.
- Assume-se destaque de prazos nos próximos sete dias, com maior urgência para vencidos e próximas 48 horas; limites podem ser ajustados pelo usuário.
- Assume-se resumo diário opcional ao próprio usuário, autorizado ao habilitar a rotina. Essa autorização não permite enviar mensagens a recrutadores.
- Estados iniciais propostos: enviada/aguardando resposta, contato recebido, entrevista, proposta, encerrada por rejeição e retirada pelo usuário. Só evidência explícita ou ação manual muda estado; os termos podem ser refinados no planejamento sem mudar os controles.
- Reinício deve permitir recuperar uma busca pendente atual sem tentar reproduzir todas as seleções dos dias desligados. Mecanismo de recuperação será definido tecnicamente.

### Dúvidas técnicas para investigação no planejamento

| ID | Investigação | Evidência de saída esperada |
| --- | --- | --- |
| PT-001 | Viabilidade de Codex pelo OpenBot com a assinatura existente, autenticação, limites e disponibilidade das ferramentas necessárias | Documentação e teste mínimo no ambiente alvo; registrar restrições, sem presumir API, gratuidade adicional ou contratação |
| PT-002 | Criação/configuração de bots por conversa, revisão, rotinas, comunicação e execução no OpenBot | Demonstrar criar uma proposta inativa, revisar, ativar e pausar; identificar recursos nativos e lacunas |
| PT-003 | Comunicação entre novo site e OpenBot e acesso privado pelo celular | Validar autenticação, conectividade e proteção dos dados no computador e celular; distinguir acesso ao OpenBot de acesso ao site |
| PT-004 | Meios disponíveis e permitidos para Gupy, LinkedIn e sites corporativos | Matriz por fonte com acesso validado, limitações e frequência viável; não contornar restrições nem presumir acesso completo |
| PT-005 | Autorização Gmail, marcador, descoberta mínima de mensagens e envio aprovado | Testar permissões mínimas, revogação, filtro temporal de candidaturas e limites; documentar fluxo de aprovação e envio |
| PT-006 | Deduplicação, força das evidências e proteção contra instruções externas | Casos de teste com vagas repetidas, mensagens ambíguas, conflitos e conteúdo malicioso; definir regras conservadoras verificáveis |
| PT-007 | Persistência, retomada, limites do provedor e entrega incerta | Demonstrar recuperação sem duplicatas e pausa sem gastos; definir frequência de atualização e política de tentativas |
| PT-008 | Retenção, exclusão, recuperação e tratamento de dados por provedores externos | Inventário do conteúdo necessário, destinos e controles de proteção/remoção; execução local não significa inferência inteiramente local |

O planejamento deve registrar viável, restrito ou não suportado para cada integração, com evidência. Uma limitação que impeça requisito central deve voltar como decisão de escopo ao usuário, sem substituir silenciosamente serviços ou adicionar custos. Nenhuma stack de implementação é escolhida aqui.

### Limites de escopo

Inclui instalação pessoal local, interface privada responsiva, criador de bots de emprego, quatro bots iniciais revisáveis, busca, acompanhamento Gmail e preparação textual por vaga.

Fora da V1: envio automático de candidaturas, preenchimento automático de portais, entrevista por voz, plano amplo de evolução profissional, bots de outras finalidades, hospedagem multiusuário compartilhada, execução garantida com computador desligado e importação de candidaturas antigas. Uma futura instalação de amigo é independente, não uma segunda conta nesta instalação.

### Referências e limites de evidência

- [Currículo inicial](../../curriculo-en-draft.md): fonte profissional local consultada; não reproduzir contatos pessoais desnecessariamente.
- [Dr Eggbot](https://x.ai/bot/marketplace/bots/dr-eggbot-v2), consultado em 2026-11-14: referência de criação conversacional com função e limites explícitos. Não implica copiar implementação ou disponibilidade das mesmas ferramentas.
- [OpenBot](https://openbot.run/pt), consultado em 2026-11-14: referência do produto de integração desejado. A descrição pública não comprova os mecanismos necessários para este assistente; validação permanece em PT-001 a PT-003.
