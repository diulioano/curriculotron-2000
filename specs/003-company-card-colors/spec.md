# Feature Specification: Cores dos cards por empresa

**Feature Branch**: sem branch criada nesta etapa documental (branch atual: `main`)

**Created**: 2026-11-15

**Status**: Draft

**Input**: User description: "como lembrado eu gostaria que os cards tivessem cor baseado na empresa"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reconhecer a empresa pela cor do card (Priority: P1)

Como pessoa que acompanha vagas, quero que os cards tenham identidade de cor baseada na empresa para reconhecer rapidamente oportunidades da mesma organização.

**Why this priority**: É o comportamento central solicitado e facilita a leitura de várias vagas.

**Independent Test**: Exibir vagas de PicPay, SAP, BV, CWI, DB e CI&T, incluindo duas vagas da mesma empresa, e verificar a identidade de cor de cada card.

**Acceptance Scenarios**:

1. **Given** duas vagas da mesma empresa, **When** vejo seus cards na página Hoje e na lista de vagas, **Then** ambos apresentam a mesma cor de empresa nos elementos de destaque do card.
2. **Given** cards de PicPay, SAP, BV, CWI, DB e CI&T, **When** consulto a lista, **Then** cada empresa apresenta uma identidade de cor definida, sem cair na cor neutra por ausência de cobertura.
3. **Given** uma empresa identificada sem cor de marca definida, **When** sua vaga aparece, **Then** o card recebe uma cor alternativa estável, sem alegar que seja a cor oficial da marca.

---

### User Story 2 - Manter a identidade ao navegar (Priority: P2)

Como pessoa que consulta e organiza vagas, quero que a cor da empresa permaneça consistente ao filtrar, ordenar, descartar, restaurar e retornar ao aplicativo.

**Why this priority**: A cor só ajuda no reconhecimento se não mudar conforme a posição ou o estado da vaga.

**Independent Test**: Consultar o mesmo conjunto de vagas, alterar filtros e ordem, descartar/restaurar uma vaga e recarregar a página.

**Acceptance Scenarios**:

1. **Given** cards coloridos, **When** filtro, ordeno ou recarrego a página, **Then** cada empresa conserva sua cor, inclusive após reabrir o aplicativo.
2. **Given** uma vaga descartada, **When** consulto seu card e depois a restauro, **Then** a identidade de cor da empresa permanece igual.
3. **Given** nomes da mesma empresa com diferenças apenas de maiúsculas ou espaços externos, **When** os cards aparecem, **Then** recebem a mesma cor.

---

### User Story 3 - Ler e operar os cards com clareza (Priority: P1)

Como pessoa que usa os cards, quero identificar empresa e estados por texto e operar as ações mesmo quando não distingo suas cores.

**Why this priority**: A identidade visual não pode comprometer a leitura ou esconder ações e estados já existentes.

**Independent Test**: Consultar cards com diferentes cores em larguras de 360 e 1280 pixels e percorrer suas ações com teclado.

**Acceptance Scenarios**:

1. **Given** qualquer cor de empresa, **When** leio um card, **Then** nome da empresa, título, salário, estados e ações permanecem legíveis, com contraste mínimo de 4,5:1 para texto normal e 3:1 para texto grande.
2. **Given** cards com estados distintos, **When** consulto a lista sem distinguir as cores, **Then** os estados e a empresa continuam identificáveis por texto.
3. **Given** uma tela de 360 ou 1280 pixels, **When** navego pelos cards com teclado, **Then** todas as ações existentes ficam acessíveis, com foco visível e sem rolagem horizontal da página.

### Edge Cases

- Empresa ausente, vazia ou composta apenas por espaços: usar destaque neutro e rótulo de empresa não informada.
- Empresa identificada sem marca conhecida: usar cor alternativa estável; não depender de busca externa para exibir o card.
- Empresas com cores de marca iguais ou semelhantes: manter os nomes visíveis; não prometer cor exclusiva para cada organização.
- Nomes parecidos de empresas diferentes: não presumir que são a mesma empresa por prefixo ou substring.
- Empresa corrigida na vaga: a próxima exibição deve refletir a cor da empresa corrigida.
- Cor de marca com baixo contraste: adaptar sua aplicação visual para preservar a legibilidade.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Todo card de vaga na página Hoje e nas listagens de vagas, inclusive descartadas, DEVE exibir destaque de cor baseado na empresa, preservando a identidade visual de rabisco existente.
- **FR-002**: Cards da mesma empresa DEVEM usar a mesma identidade de cor entre páginas, filtros, ordenações, estados da vaga, recarregamentos e reabertura do aplicativo.
- **FR-003**: PicPay, BV, CWI, DB, CI&T e SAP DEVEM ter cores de empresa explicitamente definidas. Identidades de marca já definidas para outras empresas DEVEM ser preservadas, salvo ajuste necessário de contraste.
- **FR-004**: Empresa identificada sem cor de marca definida DEVE receber uma cor alternativa estável. Essa cor NÃO DEVE ser apresentada como identidade oficial da marca.
- **FR-005**: Diferenças apenas de maiúsculas/minúsculas e espaços no início/fim do nome NÃO DEVEM alterar a cor. Nomes distintos NÃO DEVEM ser unificados apenas por semelhança textual. Quando a empresa da vaga for corrigida, seu destaque DEVE acompanhar a nova empresa.
- **FR-006**: Empresa não informada DEVE usar cor neutra e indicação textual de ausência, sem associação com uma empresa conhecida.
- **FR-007**: A identidade de cor DEVE aparecer nos elementos de destaque do card, como traços, hachuras e marca da empresa, mantendo texto escuro e fundo de leitura claro do padrão existente. Não basta colorir um indicador fora do card.
- **FR-008**: Nome da empresa e estados DEVEM continuar explícitos por texto. Texto normal DEVE ter contraste mínimo de 4,5:1 e texto grande de 3:1; foco e ações DEVEM permanecer identificáveis com qualquer cor aplicada.
- **FR-009**: A aplicação de cores NÃO DEVE mudar conteúdo, classificação, filtros ou ações das vagas e NÃO DEVE exigir configuração manual nem consulta externa durante a visualização.
- **FR-010**: O comportamento DEVE funcionar em larguras de 360 e 1280 pixels sem perda de ações essenciais nem rolagem horizontal da página.

### Key Entities *(include if feature involves data)*

- **Empresa**: organização associada à vaga; sua identificação determina a identidade de cor. Ausência de empresa é um caso próprio.
- **Identidade de cor**: cor de marca definida ou alternativa estável usada nos destaques dos cards; não representa prioridade, salário ou status.
- **Card de vaga**: apresentação resumida de uma oportunidade, com nome da empresa, informações e ações existentes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos cenários de aceitação com PicPay, BV, CWI, DB, CI&T e SAP, os cards apresentam a cor definida para a empresa.
- **SC-002**: Em 100% dos cenários de navegação, ordenação, filtragem, descarte, restauração e reabertura, a mesma empresa mantém a mesma cor.
- **SC-003**: Todos os cards avaliados, incluindo empresa desconhecida e ausente, atendem aos limites de contraste de FR-008 e preservam identificação textual de empresa e estados.
- **SC-004**: Em 360 e 1280 pixels, 100% das ações previamente disponíveis nos cards continuam operáveis por teclado, com foco visível e sem rolagem horizontal da página.

## Assumptions

- O pedido refere-se aos cards de vagas da página Hoje e das listagens. Cards administrativos, de atividades e de candidaturas ficam fora deste recorte.
- O contexto existente já aplica cores a algumas marcas; esta entrega especifica sua cobertura e consistência, sem certificar o comportamento atual.
- Preserva-se o padrão visual existente: cor nos destaques, textos em tinta escura e fundo claro. Pintar o fundo inteiro do card não é uma exigência presumida.
- Empresas prioritárias seguem RB-005 de [001-assistente-busca-emprego](../001-assistente-busca-emprego/spec.md); acessibilidade preserva NFR-005 da mesma especificação.
- Definir os tons exatos e conferir referências de marca ocorre no planejamento visual; não haverá editor de cores, coleta automática de logotipos ou cadastro novo de empresas nesta feature.
- A regra usa a identificação de empresa já disponível. Conciliar razões sociais, apelidos e grupos econômicos não faz parte desta demanda.
