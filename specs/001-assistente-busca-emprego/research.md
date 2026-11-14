# Research — Assistente pessoal de busca de emprego

Data: 2026-11-14. Status: pesquisa parcial; gate da fase 0 bloqueado.

## PT-001 — OpenBot e Codex

- Decision: classificar como restrito / viabilidade no ambiente alvo não comprovada.
- Rationale: [OpenBot oficial](https://openbot.run/pt) anuncia Codex e assinatura existente; isso não valida conta, ferramentas, limites ou ausência de custo adicional neste ambiente. Não foi encontrado comando openbot no PATH; isso não comprova ausência de instalação gráfica.
- Alternatives considered: API paga ou troca automática de provedor rejeitadas por falta de autorização e FR-018.
- Evidência necessária: versão instalada, autenticação com assinatura existente, tarefa mínima e pausa por limite sem fallback pago.

## PT-002 — Ciclo dos bots

- Decision: manter integração bloqueada até testar proposta inativa, revisão, ativação e pausa, incluindo alteração de configuração.
- Rationale: [README oficial](https://github.com/nightly-labs/openbot) descreve edição por prompt e execução persistente; acesso amplo do agente não constitui controle de autorização para este produto.
- Alternatives considered: apenas instruções textuais ao agente não garantem FR-014 ou NFR-003. Enforcement deverá existir fora do conteúdo gerado pelo modelo, com capacidades restritas e aprovação vinculada à versão do conteúdo e destinatários.

## PT-003 — Site e acesso móvel

- Decision: não escolher transporte antes de identificar interface suportada e validar autenticação.
- Rationale: acesso remoto ao OpenBot não demonstra acesso ao novo painel; não existe adaptador no workspace.
- Alternatives considered: publicar painel sem autenticação ou reutilizar interface interna não validada não satisfaz NFR-001.
- Evidência necessária: chamada autenticada, rejeição de acesso sem autorização e teste do novo site no celular.

## PT-004 — Fontes de vagas

Pendente: matriz Gupy, LinkedIn e sites das empresas com mecanismo permitido, cobertura e falhas verificadas. Não há prova de acesso no ambiente alvo; não afirmar suporte completo ou contornar restrições. Nenhuma integração aprovada nesta etapa.

## PT-005 — Gmail

- Decision: integração restrita até prova OAuth e controles de descoberta/envio.
- Rationale: [documentação oficial de escopos](https://developers.google.com/workspace/gmail/api/auth/scopes) distingue leitura, marcadores e envio. gmail.modify permite leitura, composição e envio e é restrito; permissão OAuth não substitui aprovação por mensagem. Não solicitar escopo de exclusão permanente.
- Alternatives considered: leitura integral indiscriminada rejeitada por NFR-002. Descoberta deve filtrar candidatos antes de obter conteúdo, e data recente do e-mail não comprova início recente da candidatura.
- Pendente: demonstrar revogação, marcador, fronteira temporal, retenção mínima e reconciliação de entrega incerta. Não houve conexão a conta pessoal.

## PT-006 — Evidências e deduplicação

Direção de desenho: identidade forte por fonte/identificador; correspondência incerta gera revisão sem fusão destrutiva. Correção manual preservada contra evidência conflitante; atualizações vinculadas a evidência e autoria. Conteúdo externo nunca concede capacidades. Ainda requer casos controlados e contrato após liberação do gate.

## PT-007 — Persistência e retomada

Direção de desenho: transações, chaves idempotentes, checkpoint e registro de execução; teto diário compartilhado entre buscas manuais e agendadas. Envio incerto deve ser reconciliado antes de qualquer repetição. Testes de interrupção não executados; armazenamento e scheduler não selecionados.

## PT-008 — Dados e provedores

Pendente inventário de dados e destinos efetivos do OpenBot instalado. Segredos fora de logs e conversas; retenção mínima e exclusão/restauração devem ser especificadas no desenho. Não assumir que execução local equivale a inferência local. Não enviar currículo ou mensagens a provedor durante a pesquisa.

## Resultado

NEEDS CLARIFICATION: interface suportada do OpenBot e provas PT-001–PT-003. Instalação identificada no registro do Windows como OpenBot 0.70.0; isso não confirma ser a versão mais recente disponível. Usuário confirmou uso de seus acessos existentes ao Codex e Claude, sem especificar planos comerciais. PT-004–PT-008 permanecem sem validação completa. Nenhuma integração classificada como não suportada apenas pela ausência de teste. Pesquisa parcial não libera fase 1. Identificar o ambiente e realizar as provas antes de produzir contratos ou guia supostamente executável.

## Atualização do ambiente — 2026-11-14

Registro de aplicativos instalados do Windows: DisplayName OpenBot 0.70.0, DisplayVersion 0.70.0. Nenhum processo com nome openbot foi encontrado na consulta. Provedores desejados: Codex e Claude existentes do usuário. Essa escolha não autoriza contratação adicional nem fallback pago. Compatibilidade da autenticação, limites e integração continuam sujeitos a teste; não é necessário pedir credenciais em conversa.

