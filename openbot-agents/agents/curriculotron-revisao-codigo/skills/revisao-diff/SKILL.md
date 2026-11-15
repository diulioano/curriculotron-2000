---
name: revisao-diff
description: Revisar diffs de código do Curriculotron 2000 contra tarefa, contratos, constituição e guia de boas práticas. Use quando o usuário pedir revisão de alterações, commit ou diff.
---

# Revisão de diff

## Entrada necessária

Use o diff completo, a descrição da tarefa e os contratos relevantes. Leia a constituição
(`.specify/memory/constitution.md`) e o guia (`docs/boas-praticas.md`) quando estiverem disponíveis
no contexto. Se o usuário enviar apenas um link de pull request e você não tiver uma ferramenta
conectada para acessá-lo, solicite que cole o diff ou os arquivos alterados.

## Escopo da revisão

- Revise o diff e o contexto necessário para avaliar seu comportamento; não audite o repositório
  inteiro sem solicitação.
- Procure defeitos concretos de comportamento, segurança, contrato HTTP/TypeScript, persistência
  JPA/SQLite/Flyway, concorrência, tratamento de erros, acessibilidade e cobertura de riscos.
- Verifique se controllers delegam casos de uso, se a fronteira valida entradas e se alterações de
  contratos atualizam consumidores e documentação relevante.
- Considere a estrutura real do projeto: Java/Spring no backend, React/TypeScript no frontend,
  SQLite e Flyway. Não imponha infraestrutura ou dependências sem requisito.
- Não execute nem declare testes, builds, lint ou acesso ao GitHub sem ferramenta e resultado
  observável. Indique o que ficou sem validação.
- Não trate buscas por `catch`, `synchronized`, `?` ou `as` como defeitos por si só. Cada achado
  deve descrever evidência e impacto no código alterado.

## Formato da resposta

```text
VEREDITO: APPROVED | BLOCKED

ACHADOS:
- [CRITICAL|MAJOR|MINOR] caminho/arquivo:linha — evidência e problema; impacto; correção sugerida.

VERIFICAÇÕES: comandos executados e resultado, ou "não executadas".
PENDÊNCIAS: contexto ou validação que faltou, ou "nenhuma".
```

Use `BLOCKED` se houver qualquer achado CRITICAL ou MAJOR. Use `APPROVED` se houver apenas
achados MINOR ou nenhum achado. Se faltou contexto, explique a limitação sem inventar um bloqueio.
