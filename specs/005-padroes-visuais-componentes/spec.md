# Feature Specification: Alinhamento aos Padrões Visuais e Biblioteca de Componentes

**Feature Branch**: `[005-padroes-visuais-componentes]` *(nenhuma branch criada nesta etapa)*

**Created**: 2026-11-16

**Status**: Draft

**Input**: User description: "não commite o design ele é somente oq, tem coisas q precisamos ajustar no projeto, fiz a parte da lib grafica da tela de como usar os componentes"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Distinguir fatos, inferências, ausências e evidências em todas as telas (Priority: P1)

Como pessoa usuária do painel, quero que cada informação exibida sobre vagas, salários, perfil e candidaturas siga uma linguagem visual única para saber de imediato o que veio da fonte, o que foi inferido pelo sistema, o que está ausente e de onde cada dado veio.

**Why this priority**: A confiança no painel depende de nunca misturar dado declarado pela fonte com estimativa/inferência ou lacuna de informação, além de permitir corrigir qualquer dado a partir da sua origem.

**Independent Test**: Abrir cartões de vaga, o detalhe de uma vaga com salário ausente/estimado e requisitos inferidos, referências salariais e candidaturas, verificando que cada um dos quatro tipos (fato, inferência, ausência e evidência) usa seu marcador visual próprio e que a evidência permite iniciar correção.

**Acceptance Scenarios**:

1. **Given** uma vaga com dados declarados na fonte (salário, local, modalidade ou requisitos oficiais), **When** visualizo o cartão ou o detalhe da vaga, **Then** esses itens aparecem em tinta sólida com marca de confirmação (`✓`) e sem contorno de inferência.
2. **Given** uma informação inferida pelo sistema (como requisito inferido, nível estimado a partir da descrição ou estimativa salarial de referência quando não há salário declarado), **When** visualizo o cartão, o resumo ou o detalhe da vaga, **Then** ela aparece prefixada com til (`~`), dentro de contorno tracejado e acompanhada do motivo ou critério entre parênteses, deixando claro que não é requisito ou valor oficial.
3. **Given** um dado não informado pela fonte (salário ausente, referência de mercado ausente, local/modalidade/disponibilidade não confirmados ou ausência de próxima ação/atualização), **When** visualizo qualquer tela do painel, **Then** o campo exibe uma área hachurada cinza com o rótulo de ausência sobre fundo legível (ex.: "salário a confirmar"), nunca preenchida por suposição.
4. **Given** qualquer informação com origem registrada (vaga, referência salarial, próxima ação ou item de revisão), **When** consulto sua evidência, **Then** vejo fonte e data/hora em tipografia monoespaçada e um acionador claro para corrigir algum dado.

---

### User Story 2 - Exibir os seis estados padronizados de tela e carregamento (Priority: P1)

Como pessoa usuária do painel, quero que estados vazio, carregando, parcial, falha, desatualizado e pendente de revisão tenham aparência e estrutura consistentes em todas as páginas para entender rapidamente o que aconteceu e qual ação posso tomar.

**Why this priority**: Atualmente várias telas exibem estados com textos simples ou estilos genéricos que não comunicam progresso, cobertura parcial, desatualização ou pendência humana segundo a biblioteca gráfica.

**Independent Test**: Simular os seis estados (lista vazia, busca em andamento, coleta parcial de fontes, erro/falha de rotina ou requisição, dados/referências antigos e itens aguardando decisão humana) nas telas do painel e verificar que cada estado exibe rótulo, contorno/textura, explicação e ação correspondentes.

**Acceptance Scenarios**:

1. **Given** uma lista ou seção sem resultados (vagas, candidaturas, referências salariais, duplicatas, fila de revisão ou atividades), **When** a tela é exibida, **Then** ela mostra o bloco de estado `VAZIO` com moldura rabiscada, título direto e contexto do que foi verificado e por que não há itens.
2. **Given** uma consulta inicial ou operação em andamento (incluindo buscas com progresso por fontes), **When** aguardo o resultado, **Then** vejo o bloco de estado `CARREGANDO` com contorno tracejado, rótulo `CARREGANDO`, descrição do progresso e barra hachurada visível.
3. **Given** uma coleta ou atualização em que parte das fontes falhou ou atingiu limite (ex.: 5 de 6 fontes consultadas), **When** o resultado é apresentado, **Then** vejo o bloco de estado `PARCIAL` com borda sólida contínua, contagem explícita de fontes consultadas, explicação do motivo e previsão ou opção de nova tentativa.
4. **Given** uma falha de rotina, integração ou carregamento de tela, **When** o erro é exibido (inclusive em Atividades, Integrações e alertas gerais), **Then** vejo o bloco de estado `FALHA` com contorno tracejado em tom de alerta, rótulo `FALHA`, descrição do que aconteceu e ação clara de recuperação.
5. **Given** dados de tela ou referências salariais cuja última coleta bem-sucedida está antiga ou não reflete o dia atual, **When** consulto a seção correspondente, **Then** vejo o bloco de estado `DESATUALIZADO` com hachura suave de fundo, texto legível sobre base clara, data real da coleta e opção para atualizar.
6. **Given** itens que exigem aprovação ou decisão da pessoa (rascunhos, vínculos incertos, mensagens ambíguas, duplicatas e itens na fila de revisão), **When** aparecem na revisão do dia ou na fila de revisão, **Then** são destacados com o padrão `PENDENTE DE REVISÃO` (marca-texto amarelo e borda sólida) e ação direta para revisar.

---

### User Story 3 - Padronizar identidade visual por empresa, contraste e componentes em todas as páginas (Priority: P2)

Como pessoa usuária do painel, quero que todas as telas — incluindo páginas secundárias e formulários — utilizem os mesmos componentes visuais, hierarquia de botões, caixas de seleção e regras de cor por empresa, preservando legibilidade e navegação por teclado ou celular.

**Why this priority**: Há páginas e controles no projeto (como referências salariais, formulários de busca/período, caixas de seleção e parte dos botões) que ainda não aplicam os componentes padronizados da biblioteca visual.

**Independent Test**: Percorrer todas as rotas do painel no desktop e no celular (390px) usando mouse e teclado, verificando consistência visual dos componentes, contraste AA dos textos e ausência de rolagem horizontal.

**Acceptance Scenarios**:

1. **Given** qualquer cartão ou barra de empresa com cor associada, **When** o componente é exibido, **Then** a cor da empresa aparece apenas em traço, hachura, barra lateral ou carimbo, enquanto todo texto e elemento de foco permanecem em tinta escura com contraste mínimo AA (4,5:1 para texto normal e 3:1 para texto grande).
2. **Given** páginas de listagem, formulários e administração (incluindo Referências salariais, Vagas, Candidaturas, Perfil/Critérios/Empresas, Integrações, Atividades e a área de Bots), **When** navego por elas, **Then** cabeçalhos, cartões, botões primários, botões secundários, campos de formulário e caixas de seleção seguem a mesma família de componentes da biblioteca gráfica.
3. **Given** o acesso em tela estreita (celular, ~390px), **When** abro a revisão do dia ou o detalhe de uma vaga, **Then** o layout se adapta sem rolagem horizontal, mantendo título resumido, até três vagas em destaque com link para as demais, áreas clicáveis com altura mínima acessível e foco visível ao navegar por teclado.
4. **Given** um requisito declarado no detalhe de uma vaga e o perfil profissional configurado, **When** consulto a lista de requisitos declarados, **Then** cada requisito indica visualmente se está presente no perfil ("você tem") ou ausente ("não está no perfil") mantendo contraste AA.

---

### User Story 4 - Alinhar blocos contextuais da Revisão do Dia e do Detalhe da Vaga (Priority: P3)

Como pessoa usuária que revisa vagas diariamente, quero que o painel inicial destaque pendências acionáveis diretamente e que o detalhe da vaga reúna resumo completo, linha do tempo da vaga e atalhos de preparação no mesmo padrão visual do protótipo.

**Why this priority**: Completa a experiência de revisão em ~10 minutos conectando os dados já existentes às seções visuais previstas no design de referência.

**Independent Test**: Abrir a revisão do dia com pendências distintas e abrir o detalhe de uma vaga com histórico de eventos e dados publicados, verificando a exibição das pendências individuais, resumo com nível, linha do tempo e seção de preparação.

**Acceptance Scenarios**:

1. **Given** pendências de naturezas diferentes aguardando ação (ex.: rascunho para aprovar, vínculo incerto de e-mail, mensagem ambígua), **When** abro a seção "Precisa de você" na página inicial, **Then** cada pendência aparece com tipo em monoespaçada, descrição e link direto para a ação correspondente.
2. **Given** uma vaga aberta em sua página de detalhe, **When** consulto a página, **Then** vejo o resumo (incluindo nível quando conhecido ou inferido), a explicação de recomendação com eventuais pontos de atenção do perfil, a linha do tempo de descoberta/ações da pessoa na vaga e o bloco de preparação em estado padronizado.
3. **Given** a abertura do link externo de uma vaga para leitura no site da empresa, **When** retorno ao detalhe da vaga, **Then** esse acesso pode aparecer apenas como evento informativo na linha do tempo ("isso não registra candidatura"), sem criar candidatura automática.
4. **Given** a ação "Corrigir algum dado →" ou o clique na evidência de uma vaga, **When** aciono a correção, **Then** abro o formulário de correção direta dos campos da vaga no próprio detalhe, salvando a alteração com autoria e data da pessoa sem apagar o registro original da fonte.

### Edge Cases

- **Empresas sem marca conhecida vs lista expandida de marcas**: empresas identificadas sem marca conhecida na lista explícita e conferida (Itaú, SAP, PicPay, Sicredi, BV, CWI, DB e CI&T) DEVEM usar grafite neutro (`#6B6B73`), emendando a regra de paleta determinística da especificação `003`; novas marcas ou cores ainda não conferidas exigem validação com o manual de marca e contraste AA.
- **Empresa não informada**: deve usar grafite neutro e indicação textual de ausência, nunca assumindo cor de marca conhecida.
- **Cores de marca em contraste**: valores de cor da empresa nunca aparecem em texto corrido de leitura; permanecem restritos a traço, hachura, barra lateral e carimbo sobre papel claro.
- **Inferência sem motivo registrado**: se uma inferência vier sem explicação detalhada da origem, ainda assim deve manter o marcador `~`, contorno tracejado e explicitação de que foi inferida pelo sistema.
- **Preferência por movimento reduzido**: animações decorativas (como nuvens da paisagem no rodapé e deslocamento suave em botões) devem ser desativadas quando a pessoa tiver preferência por movimento reduzido ativada no dispositivo.
- **Telas ainda em construção (`Bots` e bloco `Preparar`)**: neste escopo, a rota `Bots` e o bloco `Preparar / Simular entrevista` no detalhe da vaga apenas padronizam seu estado atual com os componentes da biblioteca visual (ex.: estado `VAZIO` ou aviso estruturado de recurso ainda não habilitado), sem implementar o simulador nem o criador conversacional completo.
- **Correção a partir da evidência**: a correção direta no detalhe da vaga deve preservar o histórico anterior de fonte/coleta e identificar claramente o dado corrigido pela pessoa ("corrigido por você" e data/hora).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Todo o painel DEVE adotar os quatro padrões epistêmicos da biblioteca visual em todas as telas aplicáveis: **Fato declarado** (tinta sólida + `✓`), **Inferência do bot** (`~` + contorno tracejado + motivo entre parênteses), **Ausência não informada** (hachura cinza visível sobre fundo legível) e **Evidência** (fonte e data/hora em tipografia monoespaçada com ação para correção).
- **FR-002**: Estimativas salariais de referência exibidas na ausência de salário declarado DEVEM ser apresentadas visualmente como inferência (`~` + contorno tracejado + critério/fonte entre parênteses), mantendo a faixa declarada ausente visível com o padrão de ausência ("salário a confirmar").
- **FR-003**: Todas as telas e seções com estados assíncronos, vazios, parciais, com falha, desatualizados ou pendentes de revisão DEVEM utilizar os seis padrões de estado da biblioteca visual (`VAZIO`, `CARREGANDO`, `PARCIAL`, `FALHA`, `DESATUALIZADO` e `PENDENTE DE REVISÃO`), com rótulo textual explícito e ação de próximo passo quando aplicável.
- **FR-004**: Estados de carregamento com etapas ou múltiplas fontes DEVEM exibir progresso textual e indicador visual hachurado; estados parciais DEVEM informar quantas fontes responderam em relação ao total e o motivo/próxima tentativa, sem se passar por sucesso completo.
- **FR-005**: Dados e referências desatualizados DEVEM usar o padrão `DESATUALIZADO` (textura hachurada suave com texto sobre faixa legível), informando a data/hora real da última atualização e oferecendo atalho para atualizar.
- **FR-006**: Itens que exigem aprovação ou decisão da pessoa (na fila de revisão, duplicatas e seção "Precisa de você") DEVEM usar o padrão `PENDENTE DE REVISÃO` (destaque amarelo e borda sólida) com descrição do item e link/ação direta de resolução.
- **FR-007**: A cor associada a cada empresa DEVE ser aplicada exclusivamente em elementos de contorno, hachura, carimbo e barra lateral, mantendo todo texto de leitura em tinta escura com contraste mínimo de 4,5:1 (texto normal) e 3:1 (texto grande). A lista explícita de marcas conferidas (Itaú, SAP, PicPay, Sicredi, BV, CWI, DB e CI&T) mantém suas cores definidas; qualquer empresa fora dessa lista ou sem nome informado DEVE usar grafite neutro (`#6B6B73`).
- **FR-008**: Todas as páginas existentes do painel (incluindo `Referências salariais`, formulários de `Vagas`, `Candidaturas`, `Atividades`, `Integrações`, `Perfil`, `Critérios`, `Empresas`, `Dados e privacidade` e `Bots`) DEVEM aplicar consistentemente os componentes de cabeçalho, cartões, botões primários/secundários, campos, caixas de seleção e emblemas definidos na biblioteca gráfica. A página `Bots` DEVE apenas padronizar seu estado atual neste ciclo, ficando fora do escopo a execução completa de criação conversacional de bots (que, em ciclo futuro, poderá seguir layout semelhante ao controle de busca de vagas).
- **FR-009**: No detalhe da vaga, os requisitos declarados na vaga DEVEM indicar visualmente a correspondência com o perfil da pessoa ("você tem" ou "não está no perfil"), mantendo distinção clara em relação às inferências do bot.
- **FR-010**: O detalhe da vaga DEVE incluir o bloco de evidência completo (fonte, data de consulta, data de publicação quando conhecida ou ausência explícita, e ação "Corrigir algum dado →" abrindo formulário de correção direta dos campos da vaga no próprio detalhe com preservação de histórico e autoria), linha do tempo de eventos da vaga e bloco "Preparar" em estado visual padronizado sem fluxo completo de simulação.
- **FR-011**: Todos os controles interativos em desktop e celular (~390px) DEVEM manter alvo mínimo acessível (44px de altura), foco visível via teclado e ausência de rolagem horizontal na página.
- **FR-012**: Artefatos de design locais (`design/`) DEVEM ser tratados exclusivamente como referência de especificação visual e NÃO DEVEM ser versionados ou incluídos nos pacotes de produção da aplicação.

### Key Entities

- **Indicador Epistêmico**: Classificação visual obrigatória de qualquer dado exibido (Fato declarado, Inferência do bot, Ausência não informada, Evidência de origem) que preserva procedência e evita preenchimento artificial.
- **Estado de Superfície**: Representação padronizada da condição atual de uma tela, lista ou integração (`VAZIO`, `CARREGANDO`, `PARCIAL`, `FALHA`, `DESATUALIZADO`, `PENDENTE DE REVISÃO`), com rótulo, contexto e ação associada.
- **Identidade Visual de Empresa**: Conjunto de sigla, cor de destaque restrita a traços/hachuras/carimbos (com grafite padrão fora das marcas conferidas) e classificação de prioridade usada de forma uniforme em cartões, barras laterais e cabeçalhos.
- **Correção Manual de Evidência**: Registro explícito de alteração feita pela pessoa em dados de uma vaga a partir do bloco de evidência, mantendo a origem anterior rastreável.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% das telas existentes do painel utilizam exclusivamente os componentes padronizados da biblioteca visual para cabeçalhos, cartões, botões, campos, caixas de seleção e os 6 estados de superfície, sem páginas com marcação visual improvisada ou sem estilização.
- **SC-002**: Em inspeção das telas principais (Revisão do dia, Vagas, Detalhe da vaga, Referências salariais, Candidaturas, Fila de revisão, Atividades e Integrações), 100% dos dados exibidos distinguem visualmente e textualmente fatos declarados (`✓`), inferências (`~` + contorno tracejado + motivo), ausências (hachura cinza) e evidências (origem e data em monoespaçada).
- **SC-003**: Todos os textos e elementos interativos em qualquer estado (inclusive `DESATUALIZADO`, `FALHA`, `PENDENTE DE REVISÃO` e cartões com cor de empresa) atingem contraste mínimo de 4,5:1 para texto normal e 3:1 para texto grande, sem violações de acessibilidade automatizadas nem rolagem horizontal em larguras de 390px e 1280px.
- **SC-004**: Pessoas usuárias conseguem identificar em uma única visualização, sem abrir menus ocultos, quando uma coleta está parcial, desatualizada, falhada ou aguardando decisão humana, e alcançam a ação corretiva ou de atualização em 1 clique a partir do próprio bloco de estado ou evidência.

## Assumptions

- A identidade visual de "papel pautado e rabisco" (paleta papel/tinta, tipografias locais sem dependência externa, traços angulosos, hachura a 48° e paisagem no rodapé) já estabelecida permanece como base da biblioteca gráfica.
- As regras de negócio já consolidadas nas especificações `001` a `004` permanecem válidas, com exceção explícita da emenda à `FR-004` da especificação `003`: empresas fora da lista explícita de marcas conferidas passam a usar grafite neutro (`#6B6B73`) em vez de paleta determinística por hash.
- Quando a tela dedicada de `Bots` for especificada e implementada em ciclo futuro, seu modelo de controle e acompanhamento poderá seguir um padrão próximo ao já usado em `Vagas` (controle de rotina, parâmetros de execução e acompanhamento).
- A biblioteca de padrões visuais criada no arquivo local de design serve como especificação visual de referência (o "quê"), permanecendo fora do controle de versão.
