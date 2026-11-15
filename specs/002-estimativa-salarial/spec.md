# Feature Specification: Estimativa salarial para vagas sem salário

**Feature Branch**: Não criada; trabalho em `main`.

**Created**: 2026-11-14

**Status**: Draft

**Input**: User description: "Quando a vaga não informa salário, buscar um salário médio em uma fonte como o Glassdoor para vagas parecidas, salvar no banco para não refazer a pesquisa e mostrar a faixa por salário mensal. Também permitir abrir o Glassdoor para ver."

**Relação com a especificação 001**: complementa FR-006 e RB-003 de [001-assistente-busca-emprego](../001-assistente-busca-emprego/spec.md). Não altera a regra de que salário ausente não exclui a vaga; acrescenta uma referência de mercado claramente separada do salário declarado.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver faixa estimada em vaga sem salário (Priority: P1)

Como candidato, ao revisar uma vaga que não informa remuneração, quero ver uma faixa mensal de referência para vagas parecidas, para decidir se vale a pena disputar sem precisar pesquisar por conta própria.

**Why this priority**: É o valor central do pedido: hoje a vaga mostra só "salário a confirmar", o que obriga pesquisa manual durante a revisão diária de ~10 minutos.

**Independent Test**: Carregar uma vaga sem salário e uma estimativa de exemplo compatível; abrir o cartão e o detalhe e conferir faixa, rótulo de estimativa, fonte e data.

**Acceptance Scenarios**:

1. **Given** uma vaga sem salário e uma estimativa compatível disponível, **When** vejo o cartão na página inicial ou na lista, **Then** vejo "salário a confirmar" e, logo abaixo, a faixa mensal estimada em reais (ex.: "~ R$ 8.500 – 11.000/mês"), rotulada como estimativa, com o nome da fonte e o mês da consulta.
2. **Given** a mesma vaga, **When** abro o detalhe, **Then** a estimativa aparece entre as inferências, nunca entre os fatos, com a descrição do que foi comparado (cargo, senioridade, local ou modalidade, regime) e o tamanho da amostra quando a fonte informar.
3. **Given** uma vaga com salário declarado, **When** vejo o cartão ou o detalhe, **Then** o salário declarado é exibido normalmente e nenhuma estimativa substitui ou se sobrepõe a ele.
4. **Given** uma vaga sem salário e sem estimativa encontrada, **When** vejo o cartão, **Then** aparece "salário a confirmar" e "sem referência de mercado", sem número inventado.

---

### User Story 2 - Abrir o Glassdoor para conferir (Priority: P1)

Como candidato, quero abrir a página do Glassdoor relacionada à vaga para conferir os números e ver detalhes que o resumo não mostra.

**Why this priority**: Dá controle e transparência sobre a estimativa (RB-007), com custo baixo.

**Independent Test**: Em uma vaga com estimativa vinda do Glassdoor e em outra sem estimativa, acionar "ver no Glassdoor" e conferir o destino.

**Acceptance Scenarios**:

1. **Given** uma estimativa obtida do Glassdoor, **When** aciono "ver no Glassdoor", **Then** abre em nova aba a página consultada para aquela estimativa.
2. **Given** uma vaga sem estimativa, ou com estimativa de outra fonte, **When** aciono "ver no Glassdoor", **Then** abre em nova aba a pesquisa salarial do Glassdoor para o cargo e a localidade da vaga.
3. **Given** que abri o Glassdoor, **When** volto ao painel, **Then** nada na vaga ou na candidatura muda por causa do clique.

---

### User Story 3 - Reaproveitar estimativas sem pesquisar de novo (Priority: P2)

Como usuário, quero que uma estimativa já pesquisada seja reutilizada por vagas parecidas, para que a busca diária seja mais rápida e não repita consultas desnecessárias às fontes.

**Why this priority**: Reduz tempo de busca, consumo do provedor (FR-018) e acessos repetidos às fontes; não é visível isoladamente, por isso P2.

**Independent Test**: Processar duas vagas parecidas na mesma busca e uma terceira dias depois; conferir que só a primeira gera consulta e que as outras reutilizam a mesma estimativa.

**Acceptance Scenarios**:

1. **Given** uma estimativa salva há menos de 90 dias para o mesmo perfil de vaga, **When** chega outra vaga parecida sem salário, **Then** a estimativa salva é usada e nenhuma nova consulta é feita.
2. **Given** uma estimativa salva há mais de 90 dias, **When** chega uma vaga parecida, **Then** uma nova consulta é tentada; se falhar, a estimativa antiga continua visível com a data original e o aviso "referência antiga".
3. **Given** uma estimativa salva, **When** peço para atualizá-la manualmente pelo detalhe da vaga, **Then** uma nova consulta é feita e a anterior fica no histórico.

---

### Edge Cases

- **Valor anual na fonte**: converter para mensal dividindo por 12 e indicar "convertido de valor anual". O 13º salário e outros benefícios não são somados nem descontados.
- **Valor por hora, por dia ou unidade ambígua**: não converter; guardar o valor original e mostrar "unidade não mensal — confira na fonte" (mesma regra do edge case de remuneração ambígua da 001).
- **Amostra pequena**: se a fonte informar menos de 5 salários, mostrar a faixa com o aviso "poucos dados".
- **Regime diferente**: estimativa CLT não é apresentada como referência de vaga PJ, nem o contrário. Se só houver dado de outro regime, mostrar "sem referência de mercado".
- **Empresa sem dados próprios**: usar a referência de mercado para cargo, senioridade e localidade, indicando "média de mercado", não "salário na empresa X".
- **Fonte indisponível, bloqueada ou pedindo login**: registrar a falha como limitação da fonte (FR-017), não tentar contornar o bloqueio e manter a vaga com "salário a confirmar".
- **Salário declarado depois**: se a vaga passar a informar salário, o valor declarado prevalece e a estimativa deixa de aparecer no cartão.
- **Faixa estimada abaixo do mínimo**: mostrar a faixa normalmente, sem esconder a vaga nem a mover para a lista secundária.
- **Conteúdo da fonte com instruções aos bots**: tratar como dado externo (NFR-003); só números, unidade, amostra, data e endereço são aproveitados.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-101**: Para cada vaga sem salário declarado, o sistema MUST buscar uma referência salarial de vagas parecidas, primeiro no Glassdoor e, se não houver resultado, em outras fontes públicas de faixa salarial permitidas.
- **FR-102**: "Vaga parecida" MUST ser definida por cargo normalizado, senioridade, localidade ou modalidade (remoto, cidade ou região) e regime (CLT ou PJ). A consulta MUST priorizar o par empresa + cargo da vaga, confirmando a identidade da empresa e a compatibilidade dos demais critérios. Quando houver dados da própria empresa, eles MUST ter preferência sobre a média de mercado e ser identificados como tal. Na ausência de dados compatíveis da empresa, a referência geral MUST ser identificada como "média de mercado", sem atribuí-la à empresa (refinamento do usuário em 2026-11-15).
- **FR-103**: Toda estimativa MUST ser apresentada como faixa **mensal** bruta em reais (mínimo–máximo), com valor central opcional. Valores anuais são convertidos dividindo por 12, e a conversão fica indicada. Unidades não mensais ou ambíguas não são convertidas.
- **FR-104**: O sistema MUST salvar cada estimativa com fonte, endereço consultado, data da consulta, unidade original, tamanho da amostra (quando informado) e critérios de comparação, e MUST reutilizá-la para vagas parecidas por 90 dias sem nova consulta.
- **FR-105**: A estimativa MUST ser exibida separada do salário declarado, rotulada como "estimativa", com fonte e mês da consulta, no cartão, na lista e no detalhe da vaga. No detalhe, ela MUST aparecer entre as inferências (FR-006 da 001).
- **FR-106**: Cada vaga MUST oferecer a ação "ver no Glassdoor", que abre em nova aba a página da estimativa usada ou, sem estimativa do Glassdoor, a pesquisa salarial do Glassdoor para o cargo e a localidade da vaga.
- **FR-107**: A estimativa MUST NOT excluir a vaga da seleção principal nem movê-la para a lista secundária. Somente salário declarado abaixo do mínimo exclui (RB-003). A estimativa também MUST NOT influenciar a ordem, a pontuação de compatibilidade ou a escolha das vagas da seleção do dia; serve apenas para exibição (decisão do usuário em 2026-11-14).
- **FR-108**: O usuário MUST poder pedir a atualização de uma estimativa pelo detalhe da vaga. A estimativa anterior é preservada no histórico.
- **FR-109**: Falhas de consulta MUST ser registradas na atividade (NFR-007) e no estado da fonte (FR-017), sem atualizar a data de sucesso e sem impedir a busca de vagas.
- **FR-110**: Consultas de estimativa MUST respeitar os limites do provedor e das fontes (FR-018, PT-004): no máximo uma consulta por perfil de vaga por busca e nenhuma tentativa de contornar login, captcha ou bloqueio.

### Key Entities

- **Estimativa salarial**: faixa mensal (mínimo, máximo, valor central opcional), moeda, regime, critérios de comparação (cargo normalizado, senioridade, localidade/modalidade, empresa opcional), fonte, endereço consultado, data da consulta, unidade original, indicação de conversão, tamanho da amostra, validade e situação (vigente, antiga, substituída).
- **Vínculo vaga–estimativa**: qual estimativa foi aplicada a qual oportunidade e quando; permite várias vagas usarem a mesma estimativa e preserva o histórico quando ela é atualizada.
- **Oportunidade** (da 001): ganha a referência à estimativa aplicada, sem alterar o salário declarado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-101**: Em pelo menos 70% das vagas sem salário da seleção diária do piloto, o cartão mostra uma faixa mensal estimada com fonte e data.
- **SC-102**: Zero casos em que uma estimativa aparece como salário declarado, entre os fatos da vaga, como motivo de exclusão ou alterando a ordem da seleção do dia.
- **SC-103**: Em uma semana de buscas diárias, no máximo uma consulta externa por perfil de vaga; vagas parecidas reaproveitam a estimativa salva.
- **SC-104**: Em todas as vagas, "ver no Glassdoor" abre uma página do Glassdoor relacionada ao cargo e à localidade em um clique.
- **SC-105**: Todos os testes com valor anual, por hora, amostra pequena, regime diferente e fonte indisponível mostram o rótulo correto, sem número inventado.

## Assumptions

- O Glassdoor não oferece acesso público por API e restringe coleta automatizada. A viabilidade de consultá-lo pelo bot de busca (navegação comum, sem contornar login ou bloqueio) precisa ser investigada no planejamento, como em PT-004. Se for inviável, o link "ver no Glassdoor" continua valendo e a estimativa vem de outras fontes públicas permitidas; essa troca será levada ao usuário como decisão.
- Validade de 90 dias: salários de mercado mudam devagar, e o prazo evita consultas repetidas sem deixar a referência velha demais. O usuário pode atualizar manualmente.
- A faixa é de salário bruto mensal, a mesma base dos mínimos de RB-003 (CLT R$ 6.000, PJ R$ 9.000). Benefícios, bônus e 13º não entram no cálculo.
- A pesquisa é feita pelo bot de Busca de vagas durante a busca diária ou manual, dentro do limite de consumo do provedor (FR-018).
- Abrir o Glassdoor é só navegação: não registra candidatura nem muda dados (RB-007).
- Fora do escopo: estimar benefícios, comparar ofertas recebidas, negociar salário e mostrar estimativa para vagas que já declaram salário.

