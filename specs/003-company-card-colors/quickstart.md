# Quickstart — validação dos cards por empresa

## Pré-requisitos

Node/npm compatíveis com o frontend, dependências do lockfile instaladas e Chromium do Playwright disponível. Não usar banco ou coleta externa pessoal. Este guia descreve verificações futuras; não comprova execução.

Na raiz do repositório:

```powershell
Push-Location frontend
try {
  npm ci
  npx playwright install chromium
  npm test
  npm run lint
  npm run build
  npm run format:check
  npm run test:frontend
} finally {
  Pop-Location
}
```

`test:frontend` inicia Vite automaticamente com API simulada, usando `playwright.frontend.config.ts`. Não exige backend. `test:e2e` usa a configuração integrada e só deve ser executado com ambiente descartável preparado conforme os testes existentes. Não declarar integração backend validada pela suíte simulada.

## Cenários de aceitação

Ampliar fixtures de `tests/e2e/frontend.spec.ts` para PicPay, BV, CWI, DB, CI&T, SAP, Itaú, Sicredi, duas vagas da mesma empresa, outra empresa identificada, nome vazio e nome com prefixo semelhante a marca. Disponibilizar as mesmas identidades em Hoje e nas três listas. Usar estados de API simulada que mudem após descarte/restauração e correção do nome.

1. Comparar cores computadas dos destaques entre Hoje/listas; seis prioritárias devem ter mapeamento definido e marcas existentes conservar seus tons. Cor aplicada apenas a indicador externo não passa.
2. Reordenar/subdividir os resultados simulados, navegar e recarregar: a mesma chave mantém a cor. Não adicionar controles novos para realizar esse teste.
3. Descartar/restaurar e consultar a lista correspondente: mesma cor e comportamento das ações. Corrigir a empresa: destaque acompanha a correção.
4. Variações apenas de caixa/espaços externos mantêm cor; nomes apenas semelhantes não herdam marca conhecida. Empresa desconhecida tem alternativa e ausência tem grafite/rótulo.
5. Rodar axe nas duas larguras; verificar textos normais com 4,5:1, grandes com 3:1. Inspecionar também pseudo-elementos, foco e hachuras, pois axe não certifica sozinho todos esses desenhos.
6. Em 360 e 1280 px, usar teclado em todas as ações, conferir foco visível, leitura de estados sem cor e `scrollWidth <= clientWidth` da página. Em Hoje móvel, usar o link já existente para acessar vagas além do limite de cards.

Resultados esperados seguem [contrato](contracts/company-card-colors.md) e SC-001–SC-004. Registrar comandos/resultados reais, eventuais falhas anteriores e revisão por agente antes de concluir tasks. Não afirmar cobertura mínima atingida sem relatório de cobertura; configuração de cobertura ausente deve ser apontada na implementação.

## Evidência da execução local

Ver [validation.md](validation.md) para resultados por tarefa e [coverage-changed-lines.json](coverage-changed-lines.json) para o recorte de linhas novas. Foram executados 58 testes unitários e 38 de navegador com sucesso. A formatação global tem oito falhas preexistentes; os seis arquivos frontend tocados passam no Prettier. Isso não certifica cobertura global nem revisão por agente no Git.

Para repetir o gate atual em `frontend`: executar `npm ci --ignore-scripts`, `npm run test:coverage` e `node scripts/check-changed-coverage.mjs <SHA-da-base-do-PR>`. O script cruza os inícios de statements/branches do `coverage/coverage-final.json` com as linhas adicionadas em `frontend/src` e exige 80% das linhas executáveis alteradas. A cobertura global de legado continua indicada no relatório, mas não é o gate deste PR. Para a jornada visual, instalar Chromium com `npx playwright install chromium` e executar `npm run test:frontend`.
