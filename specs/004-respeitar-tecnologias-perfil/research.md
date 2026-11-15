# Research — Compatibilidade por perfil

Data: 2026-11-15. Pesquisa do código atual e documentação oficial; sem execução funcional ou alteração de dados pessoais.

## R1 — Fonte de verdade dos critérios

**Decision**: implementar GET/PUT /api/criteria na feature sources e mapear a coluna version já presente, com comparação otimista e incremento sob WriteExecutor. Preservar campos existentes do formulário, normalizar listas (trim, remover vazios e duplicados), validar limites e formatos conforme contrato.
**Rationale**: Profile.tsx consome essas rotas, mas não há controller correspondente; SearchCriteriaEntity mapeia apenas parte da tabela V3. A aparência da tela não comprova persistência.
**Alternatives considered**: usar apenas seed (não respeita edição); implementar todo perfil (fora de escopo); criar outra tabela de critérios (duplica fonte de verdade).

## R2 — Separar compatibilidade e preferência

**Decision**: avaliar cargo e tecnologias independentemente de score; mismatch em qualquer eixo exclui ambas as listas. Unknown vai à secundária; empty technology é unrestricted.
**Rationale**: SelectionJpaRepository.secondary não filtra fit. RuleBasedRanker retorna weak tanto por cargo/stack quanto por outros filtros. JobFactExtractor exige Java fixo; SearchService.rank substitui cargo reconhecido pelo primeiro cargo configurado, fabricando igualdade.
**Alternatives considered**: filtrar secondary por strong (perde pendências e enfraquece outras regras); filtrar por Java (fixa perfil); apenas ocultar no React (consumidores divergentes).

## R3 — Política verificável de linguagem

**Decision**: normalizar Unicode/caixa/espaços e comparar tokens preservando sinais de C++, C# e .NET. Aliases são tabela pequena explícita: backend/back-end/back end; fullstack/full-stack/full stack; desenvolvedor/developer/software engineer em famílias de desenvolvimento, sem tornar backend e frontend equivalentes. Cargo compara família e especialização, retirando tokens de tecnologia e senioridade da comparação funcional. Tecnologia é avaliada no outro eixo; senioridade é preservada como fato separado para as regras existentes do ranker. Liderança só caracteriza incompatibilidade funcional quando as responsabilidades demonstram função diferente, não apenas pela palavra Lead. Nível omitido é lacuna, sem fabricar senioridade. Para cargos fora das famílias conhecidas, exigir título normalizado inequívoco ou responsabilidades correspondentes; caso ambíguo é unknown.

Preservar expressamente RB-004: vaga júnior de função compatível e tecnologia correspondente pode concorrer à seleção principal quando acceptJuniorIfJustified está ativo e há justificativa de empresa aprovada nos critérios (como SAP), sujeita aos demais filtros. O avaliador funcional não a exclui por diferença de nível. Com flag desligada ou justificativa ausente, manter o comportamento vigente de preferência/seleção do ranker, sem transformar senioridade em mismatch tecnológico/funcional.

Tecnologias usam nomes completos e família/versão (Java 17/21 → Java; versões preservadas como lacuna). Não inferir Java de Spring nem vice-versa. Distinguir required, optional, negated, incidental e uncertain por frase/seção: título técnico e seções de requisitos/responsabilidades/diferenciais com contexto da função podem fornecer evidência positiva; negação na oração e contexto de outra equipe não. Não usar janela cega que nega o anúncio inteiro. Trecho sem contexto confiável fica uncertain. Tecnologia opcional explicitamente aplicável conta, rotulada como diferencial.

Precedência: evidência positiva inequívoca da função conta; menções negativas/incidentes não contam; contradições sobre o mesmo uso sem resolução são unknown. Ausência sem descrição complete é unknown. Com complete, tecnologias preenchidas e nenhuma evidência positiva/ambígua, mismatched.
**Rationale**: atende casos fechados sem contratar modelo externo e preserva incerteza em texto arbitrário.
**Alternatives considered**: substring/regex de palavra isolada (C/C++, negados e contexto falham); interpretação por LLM (novo provedor/custo e menor reprodutibilidade); ontologia universal de cargos (complexidade desnecessária).
**Limitação**: interpretação conservadora pode reter pendências. Matriz de vinte ou mais anúncios com resultado esperado fixado antes da implementação é o gate; não prometer entendimento universal.

## R4 — Evidência completa versus resumo

**Decision**: JobSource.Item carrega descriptionCompleteness=complete|partial|unknown e texto literal sanitizado por fonte, até 100.000 caracteres; corte local sempre muda para partial. Manter raw_excerpt para compatibilidade, acrescentar descrição/contexto próprio para avaliação. Legado assume unknown, sem reconstruir anúncio dos textos “Menciona Java”. Entrada manual só título/URL é unknown; texto colado é unknown por padrão, sem supor completude.

Adapters declaram complete apenas se extraem todos os campos descritivos daquela fonte: no Lever combinar descriptionPlain e listas estruturadas, na Greenhouse preservar content integral; cartão Gupy, CWI e Oracle ShortDescription são partial. Em formato inesperado usar unknown e limitação, nunca complete por fornecedor.
**Rationale**: JobSource não registra completude; SearchService.excerpt corta em 4.000; AtsBoardSource Lever omite listas; outras fontes fornecem resumos. Texto perdido exige nova coleta autorizada futura para recuperar, não pode ser inventado.
**Alternatives considered**: guardar só fatos da classificação anterior (impede trocar perfil); refetch em GET (viola constituição); tratar qualquer ATS como completo (falsas incompatibilidades).

## R5 — Persistência e reavaliação

**Decision**: avaliação corrente por vaga com criteriaVersion, evaluatorVersion e evidenceRevision; status global pending/running/ready/failed; POST explícito síncrono, sem rede. Serializar reserva/finalização e commits curtos via WriteExecutor. Avaliar páginas fora da transação de escrita e aplicar somente se versões ainda coincidem. Atualizar evidência na ingestão, incluindo deduplicados e entrada manual; releitura no commit impede corrida com critérios/descartes.
**Rationale**: SQLite tem uma escrita simultânea; o projeto já usa TransactionTemplate em WriteExecutor. Manter transação durante rede/processamento textual prejudicaria outras operações.
**Alternatives considered**: atualização lazy em GET (comando oculto); processar tudo na transação (escrita longa); nova fila/outbox (sem necessidade para operação local síncrona).
**Falhas/concorrência**: reserva por perfil singleton e runId. Mudança de perfil invalida execução antiga; ela não sobrescreve estado novo. Falha persiste failed em transação separada se runId ainda é o dono; falha de persistência deixa running recuperável. Startup muda running para failed/interrupted sem disparar rede. Retry percorre pendentes da versão vigente; resultado já pronto pode ser devolvido sem refazer. Ingestão nova usa mesmo avaliador e valida versão antes de publicar.

## R6 — Histórico e UI

**Decision**: selected(date) permanece histórico bruto para consumo do teto; leitura atual aplica compatibilidade e atualidade, sem deletes. Seleções antigas consultadas por data continuam registros históricos, mas a lista de recomendações aplica avaliação atual e sinaliza essa semântica. Início acrescenta consumedCount, separado de count visível. Reavaliação não seleciona novas vagas nem altera ranking histórico; próxima busca pode usar somente posições ainda livres.
**Rationale**: DailySelectionService usa selected(date).size; filtrar esse método libera posições indevidamente. HomeController hoje expõe apenas quantidade visível.
**Alternatives considered**: apagar seleções inválidas (corrompe teto); preencher automaticamente o buraco (ultrapassa consumo); manter vagas antigas como atuais (contraria perfil).
**UI**: invalidar Query após save/reavaliar e apresentar dados stale como pendência, sem classificar no frontend.

## Fontes oficiais consultadas

- [Spring Data JPA — Query Methods](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html): métodos derivados e JPQL @Query suportam as consultas propostas; regra do projeto continua sem SQL nativo nos adapters.
- [Spring — Programmatic Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html): TransactionTemplate para fluxos imperativos, já empregado no escritor.
- [SQLite — Transactions](https://www.sqlite.org/lang_transaction.html): somente uma transação de escrita simultânea; não assumir lock por linha.
- [TanStack Query v5 — Invalidations from Mutations](https://tanstack.com/query/latest/docs/framework/react/guides/invalidations-from-mutations): invalidar consultas relacionadas após mutação.

Nenhuma dependência adicionada, portanto nenhuma escolha de versão nova. Documentação sustenta mecanismos; as políticas de linguagem, lotes e recuperação são decisões deste projeto. Questões de desenho resolvidas; desempenho e cobertura linguística permanecem metas a verificar na implementação.
