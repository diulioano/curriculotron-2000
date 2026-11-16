# Quickstart — Validação da feature 004

Guia de validação da feature `004-respeitar-tecnologias-perfil` (US1–US3 implementadas; T031–T034 concluem benchmark, verificação consolidada e entrega remota).

## Pré-requisitos e isolamento

Java 25 e Node/npm compatíveis com os manifests/lock existentes. Executar a partir da raiz do projeto. Dependências devem estar instaladas com `npm ci` no frontend. Não usar banco pessoal, nem `CURRICULOTRON_URL` apontando ao app real. O harness Playwright existente cria instalação/SQLite descartáveis quando `CURRICULOTRON_URL` não está definido.

Dados de domínio: ao menos vinte anúncios estáticos com resultado esperado por eixo, texto/contexto e completude explícitos. Fontes em WireMock/fixtures em testes; não consultar sites reais. Coverage inclui C/C++/C#, Java/JavaScript, versões, negativo, incidental, diferencial, descrição parcial/legada, conflito, cargos das capturas e troca Java→Python.

## Checks de implementação e verificação

```powershell
./backend/gradlew.bat -p backend test spotlessCheck
Push-Location frontend
try {
  npm ci
  npm test
  npm run lint
  npm run build
  npm run format:check
} finally {
  Pop-Location
}
```

Rodar `integrationTest` apenas se houver casos adicionados nessa source set (hoje os testes com SQLite real temporário residem em `src/test`; `integrationTest` permanece vazia). Usar `npm run build` para typecheck, pois não existe script `npm typecheck` no baseline. No Windows com Git `core.autocrlf = true`, arquivos não editados com CRLF no working tree podem fazer `npm run format:check` acusar quebra de linha local; no CI Linux (`LF`) o check roda sobre os arquivos versionados.

E2E dedicado: `frontend/tests/e2e/profile-compatibility.spec.ts`:
```powershell
Remove-Item Env:CURRICULOTRON_URL -ErrorAction SilentlyContinue
$env:CURRICULOTRON_E2E_PORT = '18447'
Push-Location frontend
try {
  npm run test:e2e -- profile-compatibility.spec.ts
} finally {
  Pop-Location
}
```

O harness usa home temporário, servidor próprio e projetos `mobile-360` e `desktop-1280`; confirmar porta livre. Não apontar dois processos ao mesmo SQLite. O seed controlado fica restrito ao harness E2E (`frontend/tests/e2e/support/seed.ts`), sem endpoint de produção para testes.

## Jornadas observáveis

1. GET criteria retorna os valores persistidos; PUT com version correta normaliza listas, incrementa version e marca pending. GET posterior persiste após restart. PUT com version antiga devolve 409 sem sobrescrever; payload inválido devolve 400. Mínimos salariais CLT/PJ zero ou negativos retornam 400 sem alterar versão ou estado, preservando a exigência de valores positivos de ObjectiveFilter.
2. POST reassess com a versão atual conclui ready. Backend Java/Spring compatível pode concorrer à principal; design, produto e relacionamento comercial incompatíveis não aparecem em nenhuma lista.
3. Descrição completa Salesforce sem tecnologia configurada fica fora; backend de descrição parcial sem tecnologia comprovada permanece só na secundária como não confirmado. JavaScript não prova Java; C++/C# não provam C; negação e outra equipe não contam.
4. Abrir detalhe mostra critério/trecho/fonte e diferencia requisito de diferencial. Nenhuma afirmação adiciona experiência ao perfil.
5. Trocar critérios para backend Python. Antes da reavaliação, aviso de desatualização e resultados antigos ocultos. Após o comando, listas usam Python sem exigir Java. Histórico de candidaturas/descartes continua igual.
6. Com dez seleções históricas, retirar cinco da exibição por incompatibilidade: count visível pode ser cinco, consumedCount continua dez. Próxima busca não cria a décima primeira seleção. Reavaliação não preenche posições.
7. Repetir fonte/URL existente atualiza evidência e revisão, sem criar outra oportunidade; avaliação passa a refletir descrição nova. Manual sem URL também possui evidência reutilizável.
8. Simular falha do comando: status failed, mensagem segura, sem false ready e sem apagar resultados/histórico. Retry reaplica apenas pendentes.
9. Concorrência controlada por barreiras em testes: PUT durante busca/reavaliação; mudança de evidência durante cálculo; dois POST simultâneos; descarte/merge durante publicação. Nenhuma versão antiga é publicada como atual, nenhum limite é liberado; 409 no concorrente quando aplicável.
10. Reiniciar com refresh running sem concluir: estado failed/interrupted, sem execução ou rede automáticas; POST explícito recupera. GET repetidos não escrevem, não coletam nem mudam timestamps de sucesso.
11. Regressão RB-004: cargo de desenvolvimento pleno e vaga júnior SAP de função/tecnologia compatíveis, com acceptJuniorIfJustified=true e justificativa aprovada, mantêm possibilidade de seleção conforme filtros vigentes. Com flag false ou sem justificativa, não promover à principal; a diferença de nível não deve virar incompatibilidade de função/tecnologia nem eliminar a regra antes do ranker.

## Critérios e evidências de saída

[Contratos](contracts/compatibility-api.md) definem formatos; [modelo](data-model.md) define invariantes. Cobrir todos os FR-001–010 e SC-001–005: vinte anúncios com resultados esperados fixos, zero falsos matches dos casos negativos, cem por cento de decisões/histórico preservados. Benchmark local com 1.000 anúncios mede meta de leitura <=2s e reavaliação <=10s; registrar hardware, tamanho/texto e tempo, sem afirmar antes de medir.

Revisão por agente deve registrar arquivo, evidência, impacto e severidade por tarefa; corrigir impeditivos e rever correções antes do commit. Commits delimitados e push da US acompanham a entrega. Verificar execução real dos checks/revisão no Git antes de afirmar CI ativa; testes e benchmarks não executam deploy, migração automática do banco pessoal nem aprovação funcional implícita.

## Resultados registrados — T029 (E2E e paridade)

- **Backend paridade manual/rotina e ausência de coleta em GET/refresh**: `./backend/gradlew.bat -p backend spotlessApply test --tests dev.curriculotron.sources.SearchIngestionTest` e `spotlessCheck` executados com sucesso (`3/3` testes em `SearchIngestionTest`, incluindo lote misto via `POST /api/searches` vs `SearchService.run` com `TaskRunTrigger.routine`, além de `verifyNoInteractions(JobSourceFactory)` em `GET /api/criteria`, `GET /api/opportunities/compatibility-status`, `GET /api/home`, `GET /api/opportunities`, `GET /api/opportunities/{id}`, `PUT /api/criteria` e `POST /api/opportunities/reassess`).
- **E2E isolado (`profile-compatibility.spec.ts`)**: `npm run test:e2e -- profile-compatibility.spec.ts` com `CURRICULOTRON_E2E_PORT=18447` e `CURRICULOTRON_URL` ausente passou **`12 passed (47.0s)`** nos dois viewports (`mobile-360` e `desktop-1280`), cobrindo US1, US2, troca Java→Python, legado/parcial, falha HTTP 500 + retry real, `10` consumidas com `5` visíveis (`5/10`), candidaturas/descartes intactos, ausência de requisições externas ao host local e persistência dos critérios, histórico e recuperação `running → failed/interrupted` após reinício real de instância isolada em SQLite descartável.
- **Checks de frontend da T029**: `eslint .` e `prettier --check tests/e2e/profile-compatibility.spec.ts tests/e2e/support/seed.ts` passaram após a última edição.

## Resultados registrados — T031 (Benchmark controlado de 1.000 anúncios)

- **Cenário e código**: [`CompatibilityPerformanceTest.java`](../../backend/src/test/java/dev/curriculotron/opportunities/CompatibilityPerformanceTest.java) (`readsStayUnder2sAndFullReassessmentUnder10sAcrossOneThousandPostings`), executado sobre SQLite real temporário (`SQLite 3.54.1` com Flyway v1–v13 e `WriteExecutor`).
- **Volume e tamanho de texto**: `1.000` oportunidades não fundidas (`20` marcadas como `discarded`, `10` selecionadas em `daily_selection` e fontes simuladas distribuídas entre `lever:bench`, `gupy:bench` e `greenhouse:bench`), somando `924.600` caracteres (`~924` caracteres por vaga) distribuídos em 5 perfis distintos (Java completo, Python com negação de Java, Product Designer com menção incidental, Fullstack React/Angular e legado parcial).
- **Ambiente/hardware**: `Windows 11` (`amd64`), `AMD Ryzen 9 7900 12-Core Processor` (`12` núcleos / `24` processadores lógicos), `15,2 GB` RAM, `Java 25.0.5`.
- **Medições obtidas**:
  - Reavaliação inicial (`criteriaVersion = 1`, 1.000 vagas sem avaliação prévia, lotes de 100): **`3.309 ms`** (`<= 10.000 ms`).
  - Reavaliação após troca de critérios para Python (`criteriaVersion = 2`, 1.000 vagas reclassificadas): **`1.717 ms`** (`<= 10.000 ms`).
  - Leituras antes da troca (`GET /api/home`: **`61 ms`**, `selection`: **`21 ms`**, `secondary`: **`419 ms`**, `discarded`: **`22 ms`**, `detail`: **`7 ms`**) — todas `<= 2.000 ms`.
  - Leituras após troca para Python (`GET /api/home`: **`8 ms`**, `selection`: **`8 ms`**, `secondary`: **`186 ms`**, `detail`: **`3 ms`**) — todas `<= 2.000 ms`.
- **Conclusão do benchmark**: todas as metas do plano (`<= 2 s` em leitura e `<= 10 s` em reavaliação completa) foram cumpridas sem necessidade de índices extras ou alterações adicionais em consultas/lotes.

