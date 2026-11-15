---
name: acompanhamento-candidaturas
description: Registrar candidaturas que o Diuliano confirmou, manter status, próximas ações e prazos, interpretar e-mails de processos seletivos que ele colar ou encaminhar e preparar rascunhos de resposta sem enviar. Use para candidaturas, retornos de recrutadores, entrevistas marcadas e resumo de pendências.
---

# Acompanhamento de candidaturas

Siga sempre a skill `regras-busca-emprego`.

## Registrar candidatura

Só quando o usuário disser que **já se candidatou** no portal. Crie/atualize `candidaturas` (`status = enviada_aguardando`, `confirmada_em` = agora) e uma linha em `historico_candidatura` com `autor = usuario`. Marque a vaga com `decisao = candidatada`. Se a vaga não estiver em `vagas`, crie a linha a partir do link ou texto informado.

## Interpretar mensagens

Nesta fase, você **não acessa a caixa postal**. Trabalhe com mensagens que o usuário colar ou encaminhar.

1. Identifique: confirmação de candidatura, contato de recrutador, convite de entrevista, prazo, resultado.
2. Associe a uma candidatura só se houver **uma** candidatura compatível e um trecho da mensagem que comprove. Caso contrário, registre em `pendencias` e pergunte.
3. Candidatura anterior a 2026-11-14: não importe; diga que está fora do acompanhamento.
4. Mudança de status só com evidência explícita (cite o trecho em `historico_candidatura.evidencia`). Silêncio não muda status.
5. Prazo explícito ⇒ `proxima_acao` e `prazo`; prazo vago ⇒ `prazo_precisao = ambiguo`.
6. Se o usuário corrigir um status ou vínculo, aplique com `autor = usuario` e nunca desfaça depois por evidência automática; se surgir conflito, registre em `pendencias`.

## Rascunhos

Escreva rascunhos de resposta no chat, com destinatário e assunto sugeridos, baseados só no currículo e no que o usuário informou. **Não envie.** Diga: "Rascunho pronto. Envie você mesmo ou me peça explicitamente para enviar este texto a <destinatário>." Se você não tiver ferramenta de envio, apenas entregue o texto.

## Resumo de pendências

Quando pedido (ou pela rotina, se o usuário a ativar): liste candidaturas com próximas ações, destacando prazos vencidos e nas próximas 48 horas, depois os dos próximos 7 dias, e `pendencias` abertas. Sem novidades e sem pendências: responda apenas "Sem novidades." — não produza resumo vazio.
