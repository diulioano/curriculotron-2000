# Instruções do projeto

## Constituição e boas práticas

Antes de implementar ou revisar código, ler a [constituição](.specify/memory/constitution.md)
e o [guia de boas práticas de Java e TypeScript](docs/boas-praticas.md).
Aplicar seus critérios à mudança e informar conflitos com contratos existentes antes de alterá-los.

## Persistência e revisão obrigatória por task/US

- Usar Spring Data JPA nos adaptadores de persistência: métodos derivados para consultas simples
  e JPQL em `@Query` para consultas explícitas. Não usar SQL nativo como padrão.
- SQL nativo exige necessidade concreta não atendida adequadamente por JPA/JPQL, justificativa
  registrada e revisão da compatibilidade com SQLite. Migrações Flyway continuam em SQL.
- Antes de finalizar cada task ou US, executar revisão por agentes com contexto focado na mudança,
  lendo a constituição, o guia de boas práticas e os contratos relevantes. A revisão deve cobrir boas práticas
  Java/TypeScript, recursos do Spring, responsabilidades arquiteturais e persistência.
- Os revisores devem informar localização, evidência, impacto e severidade. Corrigir achados
  impeditivos, executar verificações aplicáveis e submeter as correções à nova revisão antes
  de declarar conclusão. Pendências não resolvidas devem permanecer explícitas.
- Cada US deve ter entrega no Git com commits delimitados e push para a branch correspondente,
  após revisão e checks locais. Não incluir alterações de outras demandas no mesmo commit.
- A esteira de qualidade no Git deve executar checks automatizados e revisão por agente.
  Não declarar essa esteira ativa sem configuração e execução verificadas; revisão local não
  comprova execução no Git. Não encerrar US com falhas impeditivas ou revisão pendente.

## Relatório ao finalizar demandas

Sempre que um agente finalizar uma demanda, deve apresentar um relatório em português contendo:

- O que foi feito e qual resultado foi entregue.
- O que mudou, indicando os arquivos relevantes e o efeito das alterações.
- Como o trabalho foi validado, com os testes e verificações executados e seus resultados. Se não houve validação, informar isso explicitamente.
- O que permanece pendente, limitações e impedimentos, quando houver.

O relatório deve distinguir trabalho concluído de trabalho parcial e não declarar como testado algo que não foi verificado. Adaptar o tamanho à demanda, mantendo essas informações claras.

Esta regra vale para o agente principal e para agentes delegados. Agentes delegados devem incluir o relatório na entrega ao agente principal, que consolida os resultados no relatório ao usuário.
