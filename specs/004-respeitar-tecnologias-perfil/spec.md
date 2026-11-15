# Feature Specification: Respeitar tecnologias e cargos do perfil nas vagas

**Feature Branch**: `feature/US-003-company-card-colors` (branch atual da especificação; diretório da feature independente)

**Created**: 2026-11-15

**Status**: Draft

**Input**: User description: "acredito que ele não tá respeitando sobre a técnologia que a pessoa colocou", com capturas da lista secundária mostrando design, produto, relacionamento comercial, Salesforce e liderança de engenharia.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Receber vagas relacionadas ao perfil configurado (Priority: P1)

Como pessoa buscando emprego, quero que cargos e tecnologias configurados determinem as recomendações, inclusive na lista secundária, para evitar analisar vagas fora da minha área.

**Why this priority**: A lista perde utilidade ao misturar oportunidades aderentes com funções sem relação com o perfil, mesmo de empresas prioritárias.

**Independent Test**: Configurar cargos de desenvolvimento backend/fullstack e tecnologias Java e Spring Boot; avaliar anúncios controlados com descrições completas, incluindo os tipos das capturas; conferir ambas as listas e os motivos.

**Acceptance Scenarios**:

1. **Given** esse perfil, **When** uma vaga de backend exige Java e atende aos demais critérios, **Then** ela pode entrar na seleção principal com evidência de função e tecnologia correspondentes.
2. **Given** esse perfil, **When** vagas exercem funções de Service Designer, Data Product Manager ou Gerente de Relacionamento de Veículos/Canal Solar, **Then** ficam fora das duas listas por função incompatível, mesmo de empresa prioritária ou com menção incidental a Java.
3. **Given** esse perfil, **When** uma vaga de desenvolvimento com descrição completa exige exclusivamente Salesforce Marketing Cloud e nenhuma tecnologia configurada aplicável ao trabalho, **Then** fica fora das duas listas por incompatibilidade tecnológica.
4. **Given** uma vaga de AI Lead Software Engineer ou Coordenação de Engenharia, **When** suas responsabilidades não correspondem aos cargos configurados, **Then** fica fora das duas listas; título técnico e empresa não comprovam aderência.
5. **Given** quinze vagas compatíveis inéditas, **When** a seleção diária é produzida, **Then** no máximo dez novas vagas são selecionadas e as demais compatíveis continuam na lista secundária.

---

### User Story 2 - Entender coincidências e informações insuficientes (Priority: P2)

Como pessoa buscando emprego, quero distinguir correspondência comprovada de informação ausente, para entender por que uma vaga merece atenção ou revisão.

**Why this priority**: Uma palavra isolada não comprova uso da tecnologia na função; descrição ausente também não comprova incompatibilidade.

**Independent Test**: Avaliar anúncios com nomes próximos, negações, menções incidentais, diferenciais e descrições incompletas; conferir classificação e explicações.

**Acceptance Scenarios**:

1. **Given** Java configurado, **When** o anúncio exige somente JavaScript, **Then** Java não é considerado encontrado.
2. **Given** Java e Spring Boot configurados, **When** uma função compatível utiliza "JAVA" ou "spring boot", **Then** diferenças de caixa e espaços externos não impedem correspondência e o detalhe mostra o termo e o trecho de origem.
3. **Given** Java configurado, **When** os únicos trechos relevantes dizem "não utilizamos Java", "Java não é necessário" ou citam Java apenas em outra equipe, **Then** esses trechos não comprovam aderência tecnológica.
4. **Given** função compatível e descrição ausente ou truncada sem evidência tecnológica suficiente, **When** a vaga aparece na lista secundária, **Then** fica identificada como "compatibilidade tecnológica não confirmada", com a informação faltante, sem promoção à seleção principal.
5. **Given** função compatível e Java como diferencial explicitamente aplicável ao trabalho, **When** a vaga é avaliada, **Then** a coincidência pode contar, mas a explicação identifica diferencial, sem afirmar exigência obrigatória.

---

### User Story 3 - Aplicar alterações do perfil às vagas existentes (Priority: P2)

Como pessoa buscando emprego, quero atualizar recomendações novas e existentes conforme os critérios salvos, preservando minhas decisões e histórico.

**Why this priority**: Corrigir somente novas buscas mantém vagas incompatíveis já armazenadas visíveis indefinidamente.

**Independent Test**: Alterar critérios com vagas já armazenadas, atualizar a avaliação e conferir listas, candidaturas, descartes e contagem diária antes e depois.

**Acceptance Scenarios**:

1. **Given** vagas backend Java e Python armazenadas e perfil inicialmente Java, **When** a pessoa salva cargos e tecnologias voltados a Python e conclui a atualização da avaliação, **Then** ambas as listas usam o perfil salvo, sem exigir Java implicitamente.
2. **Given** vagas incompatíveis já na lista secundária, **When** a avaliação é atualizada, **Then** deixam as recomendações sem exclusão dos registros nem descarte automático em nome da pessoa.
3. **Given** candidaturas, descartes e seleções diárias anteriores, **When** os critérios mudam, **Then** decisões e histórico são preservados; vagas incompatíveis deixam as recomendações atuais, mas todas as seleções já realizadas no dia continuam contando para o limite diário.
4. **Given** mesmos anúncios e critérios, **When** a avaliação é acionada por busca manual ou rotina, **Then** a compatibilidade resultante é a mesma.
5. **Given** atualização pendente ou com falha, **When** a pessoa consulta as listas, **Then** vê essa condição e a indicação de avaliação desatualizada, sem falsa confirmação de aplicação do novo perfil.

### Edge Cases

- Java e JavaScript, C e C++, C# e C não são o mesmo termo; não aceitar coincidências por partes de nomes.
- Java 17 e Java 21 pertencem à família Java para triagem; versões diferentes permanecem explícitas como possível lacuna, sem presumir experiência.
- Vários cargos/tecnologias: basta um cargo e uma tecnologia configurados aplicáveis à função; não exigir todas as tecnologias.
- Tecnologias vazias ou apenas linhas em branco: não impor restrição tecnológica e não afirmar coincidência. Cargos vazios deixam função não confirmada e impedem promoção à seleção principal.
- Função comprovadamente incompatível continua excluída mesmo com descrição incompleta.
- Função compatível, tecnologias preenchidas no perfil e descrição completa sem menção aplicável a nenhuma delas: incompatibilidade tecnológica. Descrição insuficiente ou completude desconhecida sem evidência: não confirmado, com a limitação explícita.
- Empresa prioritária, salário alto ou trabalho remoto não compensam incompatibilidade de função ou tecnologia.
- Sem vagas compatíveis, mostrar estado vazio ou apenas pendências identificadas na lista secundária, sem completar quantidade com incompatíveis.
- Siglas ou nomes ambíguos sem equivalência demonstrada não comprovam aderência. Conteúdo externo não altera os critérios do usuário.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE usar cargos e tecnologias salvos na avaliação manual, por rotina e de vagas existentes, sem exigir Java ou outra tecnologia fixa além do perfil vigente. Aceitação: US1.1 e US3.1/4.
- **FR-002**: Com cargos e tecnologias preenchidos, a aderência nesses eixos DEVE exigir função correspondente a pelo menos um cargo e evidência de pelo menos uma tecnologia configurada aplicável ao trabalho. Não exigir todas as tecnologias. Aceitação: US1.1–4 e casos de múltiplos critérios.
- **FR-003**: A avaliação DEVE distinguir correspondência, incompatibilidade demonstrada e informação insuficiente por eixo. Somente correspondência de ambos permite seleção principal, sujeita aos demais filtros; tecnologias vazias deixam esse eixo sem restrição, explicitamente, e cargos vazios deixam função não confirmada. Aceitação: US2.4 e critérios vazios.
- **FR-004**: Incompatibilidade demonstrada em qualquer eixo DEVE excluir a vaga das duas listas. A lista secundária DEVE reunir compatíveis não selecionadas e pendências sem incompatibilidade demonstrada, identificadas como pendências. Aceitação: US1.2–5 e US2.4.
- **FR-005**: Correspondências DEVEM respeitar identidade dos nomes, diferenças de caixa e espaços externos, e versões da mesma família sem ocultar diferenças. Equivalências exigem significado inequívoco; menções negadas, incidentais ou de outra função não comprovam aderência. Aceitação: US2.1–3 e casos de nomes/versões.
- **FR-006**: Cada vaga exibida DEVE permitir consultar resultados de função e tecnologia, critérios usados, coincidências e trechos de origem disponíveis, distinguindo obrigação, diferencial e ausência. Não atribuir experiência não declarada à pessoa. Aceitação: US1.1 e US2.2/4/5.
- **FR-007**: Prioridade de empresa e demais preferências NÃO DEVEM superar incompatibilidades nem preencher a seleção com vagas incompatíveis. Aceitação: US1.2/4 e ausência de vagas.
- **FR-008**: Após salvar critérios, a pessoa DEVE receber indicação da necessidade ou andamento de atualização; concluída a atualização, ambas as listas DEVEM refletir os novos critérios. Avaliação pendente ou com falha DEVE ficar identificada como desatualizada. Consultar listas não dispara coleta externa. Aceitação: US3.1/2/5.
- **FR-009**: A reavaliação DEVE abranger vagas existentes nas recomendações e preservar registros, fontes, candidaturas, descartes e seleções históricas. Retirar uma vaga da exibição não devolve sua posição no limite de dez novas seleções do dia. Aceitação: US3.2/3.
- **FR-010**: DEVEM permanecer o teto diário de dez novas seleções, a deduplicação e a exclusão de encerradas, descartadas e vinculadas a candidaturas das recomendações, além das regras existentes de salário, localização, senioridade e aprovação. Aceitação: US1.5, US3.3 e regressão das regras existentes.

### Key Entities *(include if feature involves data)*

- **Critérios de busca**: cargos e tecnologias revisados pela pessoa e preferências existentes; referência para a avaliação vigente.
- **Evidência da vaga**: título e trechos com fonte, função/tecnologia, contexto e indicação de informação incompleta.
- **Avaliação de compatibilidade**: resultado por eixo, coincidências, incompatibilidades, ausências, critérios usados e atualidade; distinta da decisão da pessoa.
- **Recomendação e histórico**: participação nas listas, seleções anteriores e decisões; remover recomendação não elimina oportunidade ou histórico.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em um conjunto controlado de ao menos vinte anúncios completos, incluindo funções dos exemplos e vagas aderentes, 100% das incompatíveis ficam fora das duas listas e 100% das aderentes em cargo e tecnologia são reconhecidas nesses eixos, sem prejuízo dos demais filtros.
- **SC-002**: Há zero correspondências tecnológicas falsas nos cenários de nomes próximos, negação e menção incidental desta especificação.
- **SC-003**: Em 100% das vagas exibidas no conjunto de aceitação, a pessoa identifica correspondência ou informação faltante em ambos os eixos e acessa a evidência disponível no detalhe.
- **SC-004**: Após alterar cargos/tecnologias e concluir atualização, 100% das recomendações atuais do conjunto controlado refletem o novo perfil; nenhuma exige Java se não estiver configurado.
- **SC-005**: Em todos os cenários de reavaliação, 100% das candidaturas, descartes e seleções históricas são preservados, sem ultrapassar dez novas seleções no mesmo dia.

## Assumptions

- As imagens mostram ruído na lista secundária, mas não os critérios salvos ou descrições completas. Títulos são exemplos para cenários controlados; compatibilidade real depende do anúncio e perfil vigentes.
- Tecnologias são alternativas de aderência. Novas categorias obrigatórias, pesos editáveis ou novo editor de perfil estão fora do escopo.
- Correspondência de função considera responsabilidades descritas, sem exigir igualdade literal de títulos; liderança técnica não é automaticamente compatível com desenvolvimento pleno.
- Informação insuficiente permanece revisável na lista secundária; incompatíveis permanecem armazenadas para histórico/reavaliação, sem nova tela obrigatória de arquivo.
- Esta feature refina FR-005 da [spec 001](../001-assistente-busca-emprego/spec.md): “oportunidades restantes” não inclui incompatibilidades demonstradas nas recomendações secundárias. RB-001 continua como perfil inicial, editável, sem tornar Java obrigatório para todo perfil. Planejamento deve atualizar contratos e documentação afetados antes de implementar.
- A constituição vigente 1.0.0 prevalece sobre a premissa histórica da spec 001 de ausência de constituição ratificada.
- Coleta continua limitada às fontes disponíveis e permitidas, sem novos provedores ou custos. Reavaliar evidências armazenadas não exige coleta externa; suas limitações devem ficar visíveis.
- Esta entrega especifica comportamento. Correção do código, reclassificação dos dados pessoais e validação funcional pertencem às próximas fases.
