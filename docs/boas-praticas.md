# Guia de boas práticas — Java e TypeScript

Pesquisa documental: 2026-11-15. Escopo: Java/Spring no backend e TypeScript/React no frontend.
A [constituição](../.specify/memory/constitution.md) estabelece as obrigações; este guia detalha
decisões práticas. Não é uma auditoria nem afirma conformidade do código atual.

As recomendações são escolhas do projeto, fundamentadas nas fontes oficiais vinculadas.
Conferir a versão instalada antes de aplicar exemplos da documentação. Stack observada:
Java 25, Spring Boot 4.1.2, JPA, SQLite, Flyway; TypeScript ~6.0, React, Vite e TanStack Query.
Os manifests prevalecem sobre descrições antigas das versões nos planos.

## 1. Bibliotecas antes de infraestrutura própria

Antes de criar infraestrutura, procurar um equivalente no projeto e na biblioteca instalada.
Implementação própria ou nova dependência exige requisito, alternativa avaliada e justificativa
na descrição da mudança. Não construir um framework interno para uma única chamada.

| Necessidade | Recurso já adotado | Responsabilidade da aplicação |
| --- | --- | --- |
| Campos obrigatórios, tamanho e formato | Jakarta Bean Validation/Spring MVC | Restrições específicas do negócio |
| Sessão, autenticação, autorização e CSRF | Spring Security | Política de acesso |
| Logs | SLF4J e backend de logging do Boot | Eventos e contexto seguro |
| JSON | Jackson | DTOs e mapeamentos |
| Persistência e transações | Spring/JPA, SQLite, executor de escrita existente | Invariantes e consultas |
| Execução em segundo plano e rotina | Executor gerenciado/Spring Scheduling | Coordenação e recuperação |
| Estado remoto no frontend | TanStack Query | Chaves, política de atualização e interface |

Bibliotecas novas exigem avaliar manutenção, licença, compatibilidade e custo operacional.
Uma função simples de negócio não precisa de uma dependência nem de uma nova camada.

## 2. Java: responsabilidades e tipos

- Organizar por domínio e seguir as portas/adaptadores existentes. Caso de uso coordena; domínio
  protege invariantes; adaptador integra HTTP, banco ou rede. Injetar dependências por construtor.
- Domínio não depende de `ResponseEntity` ou servlet. Não criar interfaces para toda classe:
  usar portas para fronteiras concretas, sem abstrações de uso hipotético.
- Agregados simples podem usar `record`. Componentes são finais, mas coleções internas podem ser
  mutáveis: fazer cópias defensivas quando necessário. Não substituir automaticamente entidades
  JPA por records. [Base: Record Classes](https://docs.oracle.com/en/java/javase/25/language/records.html).
- Usar DTOs nomeados para contratos estáveis, sem retornar entidades JPA diretamente. Evitar
  tipos raw, `Object`, mapas aninhados e `ResponseEntity<?>` para formatos conhecidos. Wildcards
  são válidos em APIs realmente genéricas; sua presença isolada não comprova um defeito.
- Estados fechados usam enums, com nomes de transporte definidos. `var` é adequado quando o tipo
  é evidente. Usar imports explícitos, nomes claros e blocos com chaves. Preferir laço a stream
  quando efeitos e ramificações ficam mais legíveis.
- Ausência de entidade deve ser resultado explícito (`Optional`) ou exceção específica;
  `NoSuchElementException` não deve servir de contrato implícito de negócio.
- Dinheiro que exige precisão decimal usa `BigDecimal`, moeda, escala e arredondamento definidos;
  evitar construção a partir de `double`. Unidade mensal/anual e regime são parte dos dados.
  [Base: BigDecimal](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html).
- Distinguir data civil e instante. Reutilizar `AppClock` para tempo controlável nos testes.

## 3. Spring MVC: controllers e validação

Controller recebe entrada, chama o caso de uso e traduz o resultado. Não mantém filas, executores,
mapas de tarefas ativas ou regras de negócio. Agendador e endpoint chamam o mesmo caso de uso.

Validação estrutural usa constraints como `@NotBlank`, `@Size` e validação aninhada com `@Valid`.
Header obrigatório deve ser declarado obrigatório; ausência e conteúdo em branco são casos
distintos. Cobrir validação de parâmetros conforme o Spring MVC instalado, inclusive
`HandlerMethodValidationException` quando aplicável.
[Base: MVC Validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html).

Regras como “vaga já possui salário” ou “não pode haver duas atualizações ativas por perfil”
pertencem ao domínio/aplicação e valem também fora do HTTP. Importações devem ter limite,
validação antes dos efeitos e transação quando o contrato exigir atomicidade.

Exemplo ilustrativo de contrato, sem implementar o refresh nesta demanda:

```java
public record SalaryRefreshResponse(String taskRunId, TaskRunState state) {}

@PostMapping("/opportunities/{id}/salary-estimate/refresh")
public ResponseEntity<SalaryRefreshResponse> refresh(@PathVariable String id) {
  var result = refreshSalary.execute(id);
  return ResponseEntity.accepted()
      .body(new SalaryRefreshResponse(result.taskRunId(), result.state()));
}
```

O exemplo pressupõe um caso de uso tipado, não define a política final de idempotência e não
substitui o contrato existente. Respostas sem corpo usam `void`/`ResponseEntity<Void>`.
Exceções traduzidas pelo advice não precisam integrar o tipo de retorno de sucesso.

## 4. Exceções, segurança e logs

Traduzir falhas de domínio por `@RestControllerAdvice`/`@ExceptionHandler`, mantendo o envelope
de erro do projeto. Erros esperados têm mapeamento 4xx definido; inesperados usam 500.
Não converter toda `IllegalArgumentException` em erro do cliente sem verificar sua origem.
Cobrir corpo e parâmetros inválidos, preservando headers HTTP como `Allow` e `Retry-After`.
[Base: ExceptionHandler](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html).

Advice MVC não é tratamento universal: Spring Security atua em filtros anteriores ao controller.
Configurar seus pontos de tratamento de autenticação/acesso quando necessário para o formato de
erro. Não criar filtros caseiros equivalentes às abstrações de segurança existentes.
[Base: Security Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html).

Manter CSRF para mutações com sessão por cookie, inclusive no modo local conforme o contrato
do perfil. Cookie HttpOnly não elimina CSRF; não desativar proteção para fazer testes passarem.
[Base: CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

Logs usam SLF4J e parâmetros `{}`. O logger aceita a exceção para registrar sua causa, mas o projeto
exige revisar/redigir dados sensíveis antes do registro, inclusive mensagens e cadeia de causas.
Preferir contexto seguro (`taskRunId`, código de falha, operação) e um ponto responsável pelo log.
Não registrar senha, token, cookie, corpo de e-mail ou payload privado.
[Base: SLF4J Manual](https://www.slf4j.org/manual.html).

Capturar `Exception` na fronteira final de um worker pode ser necessário para registrar falha e
atualizar estado. Não descartar a causa nem registrar sucesso após falha. Tratar falhas da própria
persistência do estado. Se capturar `InterruptedException` sem propagação, preservar o sinal de
interrupção e respeitar cancelamento. Exceção de worker não chega automaticamente ao advice MVC.

## 5. Transações, SQLite e execução assíncrona

Reutilizar `WriteExecutor` e entender a transação real antes de adicionar `@Transactional`.
No modo por proxies, chamada interna ao próprio objeto não ativa a interceptação transacional;
transação também não passa automaticamente para outra thread. Fazer rede fora da transação de
escrita e usar Flyway para evolução de esquema.
[Base: Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html).

SQLite admite várias leituras e somente uma transação de escrita simultânea. Não presumir locks
de linha de outros bancos ou usar `SELECT FOR UPDATE` sem comprovar suporte. Avaliar constraints,
atualizações condicionais e transações compatíveis, verificadas com SQLite temporário.
[Base: SQLite Transactions](https://www.sqlite.org/lang_transaction.html).

Executores devem ter lifecycle gerenciado, capacidade, rejeição e encerramento definidos. Avaliar
`TaskExecutor`, `@Async` e `@Scheduled` antes de recriar sua infraestrutura. `@Async` usa proxy por
padrão, não intercepta chamada interna e não oferece durabilidade ou deduplicação. Tratar falhas
do trabalho e da submissão, com timeout e cancelamento apropriados.
[Base: Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html).

Persistir uma tarefa antes de submetê-la cria uma janela de falha entre commit e execução. Definir
recuperação após reinício, evitando tarefas eternamente pendentes. Fila durável/outbox exige
necessidade concreta; não prometer execução exatamente uma vez sem prova de ponta a ponta.

## 6. Concorrência, idempotência e renovação

Estas são decisões de projeto, não exigências automáticas do Spring:

| Problema | Mecanismo a avaliar |
| --- | --- |
| Repetir a mesma ação após timeout | Idempotência da operação lógica |
| Rotina e pessoa atualizam o mesmo perfil simultaneamente | Reserva atômica por perfil |
| Pesquisar novamente apesar do cache recente | Política de renovação |
| Atualizar perfis diferentes em paralelo | Capacidade e limites do executor/fonte |

Antes de adicionar bloqueio, documentar a invariante, chave do recurso e alcance: threads,
processo ou instâncias. Rotina e endpoint devem compartilhar a coordenação. A sequência consultar
e reservar precisa ser atômica nesse alcance, com comportamento para quem perde a corrida.
Definir liberação após sucesso, erro, submissão rejeitada e reinício.

`ConcurrentHashMap` não torna `get` seguido de `put` atômico. `synchronized` protege um monitor
local, não outra JVM. Bloqueio local pode ser adequado com justificativa, mas não sincronizar o
controller inteiro para proteger perfis independentes. `@Transactional` sozinho também não impede
duplicatas. Não adicionar infraestrutura distribuída por antecipação à aplicação de um usuário.

Não exigir `Idempotency-Key` em todo POST nem gerar nova chave a cada retry da mesma ação. Quando
necessária, definir escopo da operação/identidade/recurso, payload relevante, validade, replay,
falha e conflito de payload. A mesma chave com payload diferente precisa de resposta definida.
Não usar uma chave sem escopo quando puder colidir entre recursos.

Para refresh simultâneo do mesmo perfil, o caso de uso pode devolver a tarefa ativa; depois do
término, uma nova ação segue a política de cache/renovação. A revisão deve conciliar contratos
atuais antes de remover headers ou mudar a semântica de repetição.

## 7. TypeScript: tipos que descrevem dados reais

- Manter `strict`; corrigir incompatibilidades em vez de enfraquecer o compilador. Revisar erros
  novos ao atualizar TypeScript. [Base: strict](https://www.typescriptlang.org/tsconfig/strict.html).
- Entrada sem garantia começa como `unknown` e é refinada por verificações/type guards.
  Representar variantes com uniões discriminadas e tratar alternativas exaustivamente quando
  necessário. [Base: Narrowing](https://www.typescriptlang.org/docs/handbook/2/narrowing.html).
- Não usar `any`, `as unknown as T` ou `!` para esconder incompatibilidades. Assertions `as T`
  e `api<T>()` não validam JSON em runtime. Dados externos/importados e respostas críticas exigem
  parsing/validação na fronteira, sem repetir validação em toda função interna.
  [Base: Everyday Types](https://www.typescriptlang.org/docs/handbook/2/everyday-types.html).
- Nomear DTOs em `api/`, usar `import type` e generics quando preservam uma relação útil entre
  tipos. Distinguir ausente, `null` e vazio conforme o contrato. Backend e frontend evoluem juntos.
- Centralizar transporte, encoding de URL, CSRF, erros e cancelamento no cliente existente.
  Tratar resposta sem corpo explicitamente; não espalhar `fetch` e formatos divergentes por página.
- Não mascarar erro com lista vazia ou sucesso. Cancelamento não é falha de negócio.

## 8. React e estado remoto

Componentes renderizam a interface; hooks/funções coordenam interações reutilizáveis; o cliente faz
transporte. Não criar um hook para toda expressão nem replicar regras do backend como fonte
definitiva. Validação de formulário melhora a experiência, sem substituir a validação no servidor.

Hooks comuns (`useState`, `useEffect`, hooks de Query) ficam no topo de componentes/custom hooks,
antes de retornos condicionais. Não suprimir regras de dependências para esconder closures antigas.
APIs com regras próprias, como `use`, seguem sua documentação específica.
[Base: Rules of Hooks](https://react.dev/reference/rules/rules-of-hooks).

Derivar dados durante renderização quando possível. Reservar Effects para sincronização com
sistemas externos e limpar timers/subscriptions. Refresh/importação parte de evento/mutação,
não de Effect acionado indiretamente por uma flag.
[Base: You Might Not Need an Effect](https://react.dev/learn/you-might-not-need-an-effect).

Usar TanStack Query para estado remoto; query keys incluem parâmetros que afetam o resultado.
Após mutação, invalidar/atualizar os dados afetados. Não copiar o cache para outro estado local
como segunda fonte de verdade. Atualização otimista exige rollback definido.
[Base: Query Invalidation](https://tanstack.com/query/latest/docs/framework/react/guides/query-invalidation).

Definir retry de mutação pelo contrato da operação. Desabilitar botão pendente melhora a UI,
mas não garante exclusividade no servidor. Mostrar carregamento, vazio, falha e sucesso; usar
HTML semântico, labels, foco acessível e anúncio apropriado de erros. Não renderizar HTML externo
com `dangerouslySetInnerHTML` sem requisito e tratamento apropriado.

## 9. Testes e verificações proporcionais

Testar comportamentos e riscos, não copiar a implementação. Java: invariantes, contrato MVC,
segurança, SQLite real temporário e integrações simuladas conforme a mudança. Frontend: interações
observáveis com Testing Library; Playwright para jornadas integradas relevantes. Não usar banco
pessoal ou coleta externa real em testes de rotina. Documentação isolada não exige teste novo.

Comandos existentes, a partir da raiz:

```powershell
./backend/gradlew.bat -p backend test spotlessCheck
# Quando a mudança envolver os testes desta source set:
./backend/gradlew.bat -p backend integrationTest

Push-Location frontend
try {
  npm test
  npm run lint
  npm run build
  npm run format:check
  # Conforme a jornada afetada, com banco e ambiente descartáveis:
  npm run test:e2e
} finally {
  Pop-Location
}
```

Scripts conferidos nos manifests; esta lista não significa execução. `integrationTest` sem casos
não comprova integração, e testes com banco podem estar em `src/test`. Relatar falhas preexistentes
separadamente. Não formatar ou refatorar arquivos alheios à demanda.

## 10. Checklist para a revisão posterior

- [ ] Contratos tipados, validação estrutural e erros padronizados?
- [ ] Regras válidas fora do HTTP e compartilhadas entre rotina e ação manual?
- [ ] Uso das bibliotecas existentes em vez de infraestrutura manual sem justificativa?
- [ ] Concorrência com invariante, recurso, alcance, atomicidade e recuperação definidos?
- [ ] Idempotência com requisito e contrato de repetição/payload conflitante?
- [ ] Tarefas cobrem corrida, rejeição, falha, interrupção e reinício conforme a garantia?
- [ ] Transações curtas e garantias verificadas no SQLite real?
- [ ] Logs seguros preservam diagnóstico; UI distingue falha, vazio e cancelamento?
- [ ] Tipos TypeScript descrevem dados reais sem casts que ocultam incompatibilidades?
- [ ] Hooks, Effects e cache têm responsabilidade clara e cleanup quando necessário?
- [ ] Segurança, aprovação, proveniência e fatos versus inferências preservados?
- [ ] Relatório distingue leitura documental, teste executado e integração demonstrada?

Cada achado deve registrar arquivo/localização, evidência, impacto, severidade e princípio violado.
Ocorrências de `?`, `synchronized`, `catch` e `as` são pistas de busca, não defeitos comprovados.
A revisão e as correções do código pertencem à demanda posterior anunciada pelo usuário.
