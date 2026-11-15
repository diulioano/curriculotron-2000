# Frontend do Curriculotron 2000

React + TypeScript, com o visual de caderno definido em `design/`. Fontes locais; nenhuma dependência externa em tempo de execução.

```powershell
npm ci
npm run dev
```

O Vite encaminha `/api` para `https://localhost:8443`. Para outro endereço, defina `CURRICULOTRON_BACKEND` antes de executar. Login e configuração inicial exigem o backend real; não existe modo de demonstração que dispense autenticação.

```powershell
npm run build
npm run lint
npm test
npm run test:coverage
npx playwright install chromium
npm run test:frontend
```

`test:frontend` inicia e encerra o Vite automaticamente, intercepta apenas `/api/` com fixtures de teste e executa as telas em 360 e 1280 px. Inclui axe-core, verificação de largura, inscrição explícita, correção de candidatura e bloqueio de consultas privadas sem sessão. `npm run test:e2e` continua disponível para jornadas contra um backend em `CURRICULOTRON_URL` (padrão HTTPS na porta 8443).

No PR, a CI exige cobertura de pelo menos 80% das linhas executáveis alteradas em `frontend/src`, calculada após `npm run test:coverage`. A cobertura global, que inclui código legado, aparece no relatório sem ser usada como limite deste PR.

Telas implementadas: Hoje, Vagas, Detalhe da vaga, Duplicatas, Candidaturas e histórico, Revisão, Atividades, Integrações, Perfil, Critérios, Empresas e inventário de Dados. Os DTOs estão descritos em `specs/001-assistente-busca-emprego/contracts/panel-api.md`. Os endpoints de domínio ainda precisam ser implementados no backend; enquanto ausentes, as telas mostram indisponibilidade. A seleção e o limite diário são controlados pelo servidor.

Gestão de bots, Gmail e rascunhos, entrevista, edição de empresas existentes e exclusão de dados permanecem nas respectivas tarefas do plano. O gate do OpenBot continua pendente.
