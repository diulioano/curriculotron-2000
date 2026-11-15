# Quickstart de validação

Guia futuro, nenhum cenário abaixo foi executado no planejamento.

## Pré-requisitos e comandos

Java 25, Node e dependências do repo; SQLite temporário nos testes. E2E usa configuração Playwright existente, serviços e novos seeds salariais. Na raiz, PowerShell:

```powershell
Push-Location backend
.\gradlew.bat test integrationTest spotlessCheck
Pop-Location
Push-Location frontend
npm test
npm run lint
npm run build
npm run test:e2e -- salary-estimates.spec.ts
Pop-Location
```

Após implementar perfil local: `backend/gradlew.bat -p backend bootRun --args="--spring.profiles.active=local"`; endereço esperado http://127.0.0.1:8443, sem TLS apenas nesse perfil.

## US1

Semear mensal/anual, sem referência, amostra 4, horário, regime divergente, empresa/mercado. Conferir início/lista/detalhe a 360/1280px: estimativa, fonte/mês, anual/12, poucos dados, unidade não mensal sem conversão, ausência sem número inventado. Detalhe em inferências com critérios. Salário declarado prevalece inclusive quando acrescentado depois. Comparar IDs/ordem/score/motivos antes/depois: iguais. Rejeitar resposta inválida e instruções externas. Prova SC-102 e SC-105.

## US2

Com estimativa Glassdoor, alternativa, ausente e salário declarado, abrir link: destino relacionado ao cargo/local, nova aba e noopener, nenhum dado alterado. Validar rota real/termos e registrar resultado, sem contornar login/bloqueio (SC-104).

## US3

Relógio controlado: duas vagas similares no TaskRun e outra antes de 90 dias geram uma consulta; reinício preserva cache/deduplicação inclusive falhas. Aos 90 dias exatos renovar. Falha mantém data/faixa e aviso antiga; lastSuccessAt não muda. Renovação manual, mesma chave repetida e concorrência geram uma tarefa, histórico preservado. Limite/falha não impedem busca nem seleção.

## Local sem login

Sem senha cadastrada, abrir painel e executar mutação com CSRF. Sem token =>403. Bind apenas loopback; configuração local exposta falha. Perfil padrão mantém 401 e login. Nenhuma tela setup/sair no modo local.

## Piloto externo

Somente após gate de research: registrar fonte e autorização, amostras, consultas e falhas; medir faixa em >=70% das vagas elegíveis (SC-101) e uma semana de reutilização (SC-103). Sem fonte aprovada, declarar pendência e não apresentar fixtures como validação real.

## Resultado da execução de 2026-11-15

104 testes backend e 28 frontend passaram; lint/typecheck/build/Spotless passaram. Playwright: 12 cenários completos passaram nos dois tamanhos, incluindo 3 vagas controladas de pleno e lote de referências reais. A importação no banco local padrão foi confirmada por API e novamente após reinício. As 3 observações são unknown para regime e não foram aplicadas como CLT/PJ; a fixture compatível permanece só no banco e2e. Processo de validação encerrado.

Para usar os dados importados, executar scripts/run-local.ps1 e abrir /referencias-salariais. O roteiro de piloto externo de uma semana continua futuro; resultados pontuais não comprovam SC-101/103.
