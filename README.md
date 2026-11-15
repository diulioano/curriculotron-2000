# Curriculotron 2000

Aplicação pessoal de revisão de vagas. Backend Java 25/Spring Boot e frontend React/Vite.

## Uso local sem login

Na raiz, PowerShell:

```powershell
./scripts/run-local.ps1
```

Abrir http://127.0.0.1:8443. O perfil `local` usa HTTP em loopback, entra sem senha e mantém CSRF nas alterações. Configuração que tente expor esse perfil em 0.0.0.0 falha ao iniciar. Sem o perfil local, a autenticação e o TLS existentes continuam ativos. VPS não foi configurada.

Java 25 precisa estar instalado (o script detecta Eclipse Adoptium no diretório padrão). Para atualizar os estáticos antes de iniciar, usar Node no PATH:

```powershell
cd frontend
npm ci
npm run build
cd ..
```

Gradle copia frontend/dist para os estáticos do backend. Dados persistem por padrão em `%LOCALAPPDATA%/Curriculotron/data/curriculotron.db`. `run-local.ps1 -Port 18445 -DataHome <diretório>` permite instância separada; não apontar dois processos simultâneos ao mesmo banco.

## Referências salariais

A página `/referencias-salariais`, também no rodapé Sistema, reúne consultas por empresa + cargo, com senioridade, fonte, data e unidade. Faixa mensal/anual compatível aparece nos cartões e nas inferências do detalhe, sem preencher salário declarado nem alterar ranking. Anual é dividido por 12; hora/dia/ambíguo não é convertido. Cache de 90 dias, dados e vínculos históricos ficam no SQLite. Consultas GET nunca coletam dados externos.

A coleta web ainda é assistida nesta sessão do agente. O backend não inclui acesso automático à ferramenta web do assistente; uma atualização sem executor configurado fica como tarefa que precisa da pessoa e mantém a referência anterior. O app não tenta contornar o 403 recebido pelo coletor HTTP direto.

Para importar o lote real de três referências de Desenvolvedor Java Pleno (CWI Software, Act Digital e Stefanini), com o app em perfil local:

```powershell
./scripts/import-salary-references.ps1
# Se outra porta estiver em uso:
./scripts/import-salary-references.ps1 -BaseUrl http://127.0.0.1:18445
```

O arquivo padrão é `specs/002-estimativa-salarial/pleno-web-observations.json`. Para novas consultas, salvar outro lote no mesmo formato e passar `-File`. O script preserva a data de consulta original; a importação não finge uma nova pesquisa. As páginas consultadas não informam CLT/PJ: essas observações são guardadas com regime desconhecido, visíveis no catálogo, sem serem aplicadas automaticamente a vagas CLT/PJ. Dados de teste compatíveis ficam exclusivamente no banco descartável do e2e.

API adicional: GET `/api/salary-estimates`, POST `/api/salary-estimates/import` (lote de até 100, atômico), POST `/api/opportunities/{id}/salary-estimate/refresh`, com CSRF/Idempotency-Key. Histórico é append-only. Um lote precisa conter faixa, critérios, fonte, URL HTTPS Glassdoor, evidência e data; não importar números inferidos como se fossem observados.

## Verificações

```powershell
./backend/gradlew.bat -p backend test spotlessCheck
cd frontend
npm test
npm run lint
npm run build
# Usa banco e servidor descartáveis; escolher porta livre:
$env:CURRICULOTRON_E2E_PORT='18446'
npm run test:e2e -- salary-estimates.spec.ts
```

JUnit inclui testes com SQLite real temporário em src/test; a source set integrationTest atualmente não possui testes. Guia de cenários e resultados detalhados: `specs/002-estimativa-salarial/quickstart.md` e `research.md`. Piloto de cobertura de uma semana e execução web autônoma continuam pendentes.
