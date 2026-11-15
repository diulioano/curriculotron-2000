# Quickstart — Validação da feature 004

Guia para a implementação futura; os cenários desta feature ainda não foram executados.

## Pré-requisitos e isolamento

Java 25 e Node/npm compatíveis com os manifests/lock existentes. Executar a partir da raiz do projeto. Dependências devem estar instaladas com npm ci no frontend. Não usar banco pessoal, nem CURRICULOTRON_URL apontando ao app real. Playwright existente cria instalação/SQLite descartáveis quando CURRICULOTRON_URL não está definido.

Dados de domínio: ao menos vinte anúncios estáticos com resultado esperado por eixo, texto/contexto e completude explícitos. Fontes em WireMock/fixtures; não consultar sites reais. Criar coverage de C/C++/C#, Java/JavaScript, versões, negativo, incidental, diferencial, descrição parcial/legada, conflito, cargos das capturas e troca Java→Python.

## Checks após implementar

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

Rodar integrationTest apenas se houver casos adicionados nessa source set; sem casos não é prova de integração. Hoje testes SQLite podem estar em src/test. Usar build para typecheck, pois não existe script npm typecheck no baseline. Não instalar pnpm nem atualizar stack nesta demanda.

E2E novo planejado: frontend/tests/e2e/profile-compatibility.spec.ts. Só executar o comando abaixo depois de o arquivo existir:
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

Harness usa home temporário, servidor próprio e projetos mobile-360/desktop-1280; confirmar porta livre. Não apontar dois processos ao mesmo SQLite. A implementação deve fornecer seed controlado no harness, sem novo endpoint de produção para testes.

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

Revisão por agente deve registrar arquivo, evidência, impacto e severidade por tarefa; corrigir impeditivos e rever correções antes do commit. Commits delimitados e push da US pertencem à implementação. Verificar execução real dos checks/revisão no Git antes de afirmar CI ativa. Esta fase de planejamento não constitui testes, deploy, migração nem aprovação funcional.
