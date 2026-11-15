# Data Model — Avaliação de compatibilidade

Desenho proposto; nenhuma migração executada. Tempos por AppClock, UTC ISO-8601; dia de seleção em America/Sao_Paulo.

## Critérios de busca existentes

Tabela search_criteria, id=1. Mapear todos os campos já presentes em V3, inclusive version (inteiro positivo), created_at e updated_at. PUT compara version esperada e incrementa uma vez em mudança efetiva; save normalizado idêntico é no-op. Não criar outro contador paralelo. Snapshot da avaliação guarda cargos/tecnologias e preferências usadas, para explicar o resultado sem consultar critérios já alterados.

Listas: trim, remoção de vazios, deduplicação por caixa/Unicode, até 100 itens de até 200 caracteres. Lista vazia permitida; cargo vazio não permite principal, tecnologia vazia é unrestricted. CLT/PJ: decimais BRL estritamente positivos, no máximo duas casas, em acordo com ObjectiveFilter; zero e negativos são inválidos. Hora HH:mm válida, deadlineHighlightDays 1–365, urgentHours 1–8760. Preservar campos não editados e rejeitar payload malformado, sem ignorar silenciosamente.

## Evidência por fonte

Criar opportunity_evidence, relacionada à opportunity_source quando há URL, sem substituir seus campos existentes. Campos de conteúdo:
- description_text: texto legível literal preservando seções, até 100.000 caracteres;
- description_completeness: complete|partial|unknown;
- completeness_reason: código seguro, por exemplo listing_summary, locally_truncated, legacy_excerpt;
- content_revision: inteiro positivo por evidência, incrementado apenas ao mudar conteúdo/metadados relevantes;
- title/location originais se fornecidos e metadados de origem existentes.

raw_excerpt continua para consumidores legados. Backfill copia só evidência existente, com unknown e legacy_excerpt; não derivar texto completo de requirements_facts. Texto literal manual previamente armazenado em facts pode ser recuperado como manual/unknown, sem tratar fatos derivados como anúncio. Entrada manual sem URL usa source_id nulo e source_kind=manual, sem inventar URL nem violar a constraint de opportunity_source.canonical_url. Todos os campos de descrição acima ficam na nova tabela; opportunity_source mantém suas colunas atuais e a associação é única por fonte.

opportunity_evidence: id, opportunity_id FK, source_id FK nullable, source_kind, title, location, description_text, description_completeness, completeness_reason, content_revision, fetched_at. UNIQUE source_id quando presente; para manual sem URL, identidade estável da evidência do registro, não um novo registro a cada reavaliação.

A oportunidade ganha evidence_revision global, incrementada junto a qualquer alteração relevante em suas evidências, título, remuneração ou modalidade usada no ranking. Todos os caminhos de ingestão/merge/edição devem invalidar avaliação ou reavaliar na mesma operação. Mais de uma fonte: avaliar separadamente e agregar sem misturar contextos; prova positiva confiável pode confirmar um eixo, conflito explícito entre fontes sobre o mesmo fato gera unknown, ausência só prova mismatch se há descrição completa e nenhuma evidência conflitante. Cargo incompatível inequívoco pode excluir mesmo com texto parcial.

## Avaliação corrente por oportunidade

Nova tabela opportunity_compatibility:
- opportunity_id PK/FK; criteria_version; evaluator_version (constante versionada no código);
- evidence_revision global usada; assessed_at;
- role_status: matched|mismatched|unknown;
- technology_status: matched|mismatched|unknown|unrestricted;
- reason_codes e criteria_snapshot como JSON validado e tipado na aplicação;
- evidence_items: lista tipada com source/evidence id, field/section, quote, matchedCriterion, observedTerm, mentionKind, observedVersion e configuredVersion quando presentes;
- fit, fit_score e fit_reasons derivados com o ranker existente.

Tipos de menção: required|optional|usage|negated|incidental|uncertain. Cópias defensivas de coleções no domínio; JPA entities não expostas via HTTP. JSON usa conversores existentes/adaptados com formato conhecido, sem mapas genéricos.

Atual: criteria_version e evaluator_version vigentes e evidence_revision igual à oportunidade. Ausência de avaliação é stale. Excluída: qualquer mismatched, independente de score. Principal: role matched e technology matched/unrestricted, fit strong e demais regras existentes. Secundária: atual, sem mismatched e não selecionada historicamente, permitindo unknown rotulado; regras existentes para decisão, encerramento e merge permanecem.

Persistir fit/score/reasons existentes na opportunity junto à avaliação para manter consumidores atuais; fonte de verdade do filtro são estados e versões, não frases. Nenhuma avaliação altera decisão, candidatura, salário declarado ou fatos de experiência da pessoa.

## Estado da reavaliação

Nova tabela singleton compatibility_refresh:
- id=1, target_criteria_version, evaluator_version, run_id nullable;
- state: pending|running|ready|failed;
- started_at/finished_at, error_code nullable;
- assessed_count, remaining_count (contagens da tentativa, não número de recomendações).

Transições:
1. Migração/alteração de critérios: pending. Critérios mudam e estado pending na mesma transação.
2. POST reserva running para versão esperada e novo run_id no WriteExecutor. Mesmo perfil com running válido: 409; ready sem avaliações pendentes: retorno 200 sem trabalho.
3. Ler páginas estáveis por id de oportunidades não fundidas, calcular fora da escrita; commit até 100 resultados por transação, conferindo run_id, criteria_version, evaluator_version e evidence_revision. Conteúdo alterado é relido e reavaliado, nunca sobrescrito com resultado antigo.
4. Só marcar ready após consulta final sem avaliações desatualizadas de vagas não fundidas. Se houver modificações contínuas, limitar a três passagens de reconciliação; finalizar failed/evidence_changed e deixar retry explícito. Mudança de critérios gera 409 criteria_version_conflict e deixa pending da versão nova intacto.
5. Erro: failed em transação separada apenas se o run_id continua dono; resultados já gravados continuam válidos e demais vagas permanecem ocultas com aviso de desatualização.
6. Reinício: running → failed/interrupted em inicialização, sem coleta ou reexecução escondida. Novo POST retoma deterministicamente as pendentes.
7. Falha ao persistir erro é registrada com run_id/código seguro; running remanescente é recuperável no próximo reinício. Interrupção preserva sinal.

Sem lease distribuída: uma JVM por instalação. Nenhum controller mantém estado/locks locais. PUT novo invalida o dono anterior; finalização antiga nunca muda estado da versão nova. Restore reavalia/invalida a vaga; merge atualiza evidências/revisão do sobrevivente e respeita decisão/histórico existentes. Publicação da busca também verifica a versão, reavaliando snapshot atualizado antes de seleção.

## Histórico diário e consultas

daily_selection não é apagada nem renumerada. selected(date) ou count histórico determina consumo, incluindo posições hoje invisíveis; consultas de exibição filtram avaliação atual. Acrescentar índices por oportunidade/versão/revisão e por state conforme consulta real. Filtros estruturais via JPQL/JPA, sem predicados JSON específicos do SQLite. Migração Flyway aditiva, testada em SQLite temporário inclusive com banco legado; sem nativeQuery. Não persistir cópia do currículo nem dados alheios à vaga.
