<!--
Sync Impact Report — 2026-11-15
Version: template sem ratificação → 1.0.0 (primeira constituição efetiva).
Princípios: placeholders substituídos pelos princípios I–VIII abaixo.
Seções adicionadas: restrições técnicas e de produto; fluxo de revisão; governança.
Seções removidas: exemplos e placeholders do template.
Templates e planos existentes não foram alterados; consultar esta constituição nas próximas execuções.
Pendências: reavaliar gates dos planos 001/002, que ainda registram ausência de constituição;
conciliar contratos de idempotência na revisão posterior, antes de alterar endpoints.
Este comentário é material temporário de revisão e deve ser removido antes do commit.
-->
# Constituição do Curriculotron 2000

## Core Principles

### I. Usar o ecossistema antes de recriar infraestrutura

Antes de implementar infraestrutura, DEVE-SE consultar dependências instaladas e recursos oficiais
da versão usada. Validação estrutural usa Jakarta Bean Validation integrado ao Spring; segurança
usa Spring Security; logs usam SLF4J; estado remoto no frontend usa TanStack Query.
Implementação própria ou nova dependência DEVE ter requisito concreto, alternativa avaliada e
justificativa proporcional registrada na descrição da mudança. Não se adiciona biblioteca para
regra trivial nem se recriam autenticação, caches ou executores por conveniência.

### II. Responsabilidades separadas e casos de uso compartilhados

Controllers e agendadores DEVEM adaptar entradas/saídas e delegar ao mesmo caso de uso quando
acionam a mesma operação. Invariantes ficam no domínio; coordenação fica na aplicação; HTTP,
persistência e fontes externas ficam em adaptadores. Domínio não depende de HTTP ou React.
Controllers NÃO DEVEM possuir filas, executores, mapas de tarefas ativas ou regras de negócio.
A estrutura por domínio e portas/adaptadores existente DEVE ser respeitada, sem interfaces,
camadas ou serviços genéricos criados apenas para aumentar a estrutura.

### III. Contratos explícitos e validação nas fronteiras

Endpoints com formato conhecido DEVEM declarar DTOs de entrada/saída e retornos concretos.
Não se usa `Object`, `ResponseEntity<?>` ou mapas para ocultar um contrato estável; respostas sem
corpo usam `void`/`ResponseEntity<Void>`. Payload dinâmico exige contrato documentado.
Estados finitos usam enums ou uniões tipadas. Validação estrutural ocorre na fronteira; invariantes
DEVEM valer também para rotinas, importações e outros adaptadores. TypeScript DEVE manter `strict`;
assertions e generics NÃO equivalem a validação de dados em runtime.

### IV. Erros previsíveis e diagnóstico útil

Exceções de negócio DEVEM expressar a falha sem depender de códigos HTTP. Adaptadores web traduzem
essas falhas com handlers específicos e tratamento global consistente. Falhas da cadeia de
segurança e de workers DEVEM ser tratadas nas respectivas fronteiras.
Não se usa exceção genérica como contrato de ausência de entidade nem se descarta a causa da falha.
A resposta pública DEVE ser segura e estável; diagnóstico interno DEVE permitir localizar o erro
sem expor credenciais, tokens, mensagens privadas ou outros dados sensíveis.

### V. Concorrência e idempotência orientadas a requisitos

Proteção de concorrência DEVE identificar invariante, recurso e alcance: threads, processo ou
instâncias. Rotina e ação manual DEVEM compartilhar a garantia. Não se sincroniza um controller
inteiro para proteger uma regra da aplicação. Consultar e reservar uma tarefa exclusiva DEVE ser
atômico no alcance declarado, com liberação e recuperação definidas.
Chave de idempotência do cliente só DEVE ser exigida quando repetir uma operação lógica precisar
recuperar o mesmo resultado. O contrato DEVE definir escopo, validade, replay e conflito de payload.
Evitar trabalho simultâneo, renovar cache e repetir requisição são problemas distintos.
`@Async`, `@Transactional` e coleções concorrentes NÃO constituem isoladamente essas garantias.

### VI. Persistência e execução com ciclo de vida definido

Transações DEVEM ser curtas; rede e processamento demorado ficam fora da transação de escrita.
Esquema evolui por Flyway. Garantias de unicidade e atomicidade DEVEM ser verificadas com SQLite
real e considerar o executor de escrita existente.
Workers DEVEM usar execução gerenciada e definir rejeição, encerramento, timeout e falhas.
Operações persistidas DEVEM definir recuperação após reinício entre criação e execução.
Não se promete execução exatamente uma vez sem demonstrar a garantia de ponta a ponta.

### VII. Segurança, proveniência e controle da pessoa

Sessão, autorização e CSRF DEVEM permanecer sob Spring Security, inclusive no modo local conforme
seu contrato. Candidatura e mensagem externa exigem a aprovação prevista na especificação.
Não se troca fonte/provedor nem se assume custo pago silenciosamente. Conteúdo externo é dado
não confiável. Fatos, inferências e dados ausentes DEVEM ser distinguíveis; referências salariais
preservam fonte, data, unidade e critérios, sem substituir salário declarado.
GET NÃO DEVE disparar coleta externa nem comando de negócio.

### VIII. Simplicidade e evidência na entrega

Mudança DEVE resolver requisito identificado com o menor mecanismo suficiente. Abstrações e
otimizações exigem uso concreto ou evidência. Testes DEVEM verificar comportamento e riscos:
contratos, regras, falhas, concorrência, segurança e persistência conforme o impacto.
Não se exige teste que apenas copie implementação ou para edição exclusivamente documental.
A entrega DEVE informar em português o que mudou, validações e resultados, pendências e limitações.
Não se declara comportamento testado ou integração demonstrada sem execução correspondente.

## Restrições técnicas e de produto

- Stack na ratificação: Java 25, Spring Boot 4.1.2, Gradle, JPA, SQLite, Flyway; TypeScript ~6.0,
  React, Vite e TanStack Query. Manifests são a referência das versões vigentes.
- Aplicação privada de um usuário por instalação. Outro banco, serviço ou infraestrutura
  distribuída exige requisito; o alcance das garantias deve ser explícito.
- Reutilizar relógio e infraestrutura de escrita existentes; testes usam dados e bancos descartáveis.
- Contratos HTTP e tipos TypeScript DEVEM evoluir juntos. Mudança incompatível exige explicar
  impacto e atualizar consumidores e especificações afetados.
- O [guia de boas práticas](../../docs/boas-praticas.md) detalha os critérios de implementação e
  revisão. Fontes oficiais sustentam mecanismos; escolhas normativas pertencem ao projeto.

## Fluxo de desenvolvimento e revisão

1. Ler instruções, constituição, guia, especificação e manifests relevantes.
2. Explicitar requisito e comportamento esperado; verificar soluções existentes no ecossistema.
3. Para concorrência/idempotência, descrever cenários manual × rotina, repetição, falha e reinício.
4. Implementar na responsabilidade correta e atualizar contratos/documentação afetados.
5. Executar checks aplicáveis descritos no guia e registrar resultados reais. Checks não executados
   ou sem casos de teste DEVEM ser identificados como tal.
6. Na revisão, apontar localização, comportamento afetado, princípio violado, severidade e evidência.
   Um padrão suspeito é pista de investigação; sozinho não comprova um defeito.

## Governance

Primeira constituição ratificada: 1.0.0. Aplica-se a novas mudanças e à revisão posterior do legado;
sua publicação não certifica conformidade do código atual. Registros históricos dos planos 001/002
sobre ausência de constituição devem ser reavaliados no próximo planejamento/revisão.

Alterações exigem motivo, princípios afetados e impacto em código e contratos. Remoção ou
redefinição incompatível incrementa MAJOR; novas obrigações incrementam MINOR; esclarecimentos
sem mudança de obrigação incrementam PATCH. Registrar versão e datas ISO.

Esta constituição rege práticas técnicas; instruções explícitas do usuário e restrições de produto
aplicáveis devem ser respeitadas. Conflitos com especificações existentes DEVEM ser expostos e
conciliados antes de alterar comportamento. Exceções técnicas DEVEM ter justificativa, alcance,
risco e condição de remoção registrados na documentação da mudança.

**Version**: 1.0.0 | **Ratified**: 2026-11-15 | **Last Amended**: 2026-11-15
