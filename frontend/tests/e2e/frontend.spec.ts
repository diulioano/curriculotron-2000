import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const job = {
  id: 'job-1',
  title: 'Desenvolvedor Java',
  companyName: 'SAP',
  location: 'São Leopoldo',
  workMode: 'hybrid',
  contractType: 'clt',
  salary: null,
  fitReasons: ['Java e Spring no seu perfil'],
  userDecision: 'none',
  availability: 'unconfirmed',
  facts: ['Java obrigatório'],
  inferred: ['Angular pode ser desejável'],
  missing: ['Salário'],
  sources: [
    {
      source: 'SAP Careers',
      url: 'https://careers.sap.com/job/1',
      fetchedAt: '2026-11-14T10:00:00Z',
    },
  ],
};
const application = {
  id: 'app-1',
  opportunityId: job.id,
  title: job.title,
  companyName: job.companyName,
  status: 'interview',
  nextAction: 'Confirmar entrevista',
  nextActionDueAt: '2026-11-15T15:00:00-03:00',
  duePrecision: 'exact',
  history: [
    {
      id: 'event-1',
      toStatus: 'interview',
      author: 'user',
      reason: 'Convite recebido',
      at: '2026-11-14T12:00:00Z',
      evidence: null,
    },
  ],
};
const home = {
  dataAsOf: '2026-11-14T10:00:00Z',
  selectionToday: { localDate: '2026-11-14', count: 1, limit: 10, items: [job] },
  integrations: [
    {
      id: 'openbot',
      state: 'paused',
      lastSuccessAt: null,
      lastAttemptAt: '2026-11-14T10:00:00Z',
      detail: 'Prova de conexão pendente',
    },
  ],
  applicationsWithNextActions: [application],
  mailNeedingAttention: [],
  reviewQueueCount: 1,
  missedRoutines: [],
};
const list = (items: unknown[]) => ({ items, dataAsOf: null });

test.beforeEach(async ({ page }) => {
  await page.route(/^http:\/\/127\.0\.0\.1:4173\/api\//, async (route) => {
    const path = new URL(route.request().url()).pathname;
    let data: unknown = list([]);
    if (path === '/api/session')
      data = { authenticated: true, setupRequired: false, csrfToken: 'test-csrf' };
    else if (route.request().method() !== 'GET') data = {};
    else if (path === '/api/home') data = home;
    else if (path === '/api/opportunities') data = list([job]);
    else if (path === '/api/opportunities/job-1') data = job;
    else if (path === '/api/applications') data = list([application]);
    else if (path === '/api/applications/app-1') data = application;
    else if (path === '/api/dedupe-candidates')
      data = list([
        {
          id: 'dup-1',
          opportunityA: job,
          opportunityB: { ...job, id: 'job-2' },
          signal: 'Mesmo título e empresa',
        },
      ]);
    else if (path === '/api/review-items')
      data = list([
        {
          id: 'review-1',
          kind: 'salary_ambiguous',
          summary: 'Confirmar unidade salarial',
          targetRef: job.id,
          createdAt: '2026-11-14T10:00:00Z',
        },
      ]);
    else if (path === '/api/profile')
      data = {
        id: 'profile-1',
        status: 'imported',
        facts: [
          {
            id: 'fact-1',
            kind: 'skill',
            statement: 'Java',
            origin: 'documented',
            originRef: 'Currículo, linha 10',
          },
        ],
      };
    else if (path === '/api/criteria')
      data = {
        version: 1,
        targetRoles: ['Java Pleno'],
        stackKeywords: ['Spring'],
        hybridRegions: ['Porto Alegre'],
        remotePreferred: true,
        cltMinMonthlyBrl: 6000,
        pjMinMonthlyBrl: 9000,
        acceptJuniorIfJustified: true,
        dailySearchTimeLocal: '08:00',
        deadlineHighlightDays: 7,
        urgentHours: 48,
        emailDigestEnabled: false,
      };
    else if (path === '/api/data-inventory')
      data = { items: [{ category: 'Perfil', count: 1, retention: 'Até exclusão pelo usuário' }] };
    await route.fulfill({ json: data });
  });
});

test('J1: lista, detalhe, fontes, descarte e registro explícito', async ({ page }) => {
  const posts: string[] = [];
  page.on('request', (request) => {
    if (request.method() === 'POST') posts.push(new URL(request.url()).pathname);
  });
  await page.goto('/vagas');
  await page.getByRole('link', { name: job.title }).click();
  await expect(page.getByRole('heading', { name: 'Fatos da vaga' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Inferências' })).toBeVisible();
  await expect(page.getByText('Salário a confirmar', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: /SAP Careers/ })).toHaveAttribute(
    'href',
    job.sources[0].url,
  );
  expect(posts).toEqual([]);
  await page.getByRole('button', { name: 'Me candidatei' }).click();
  await expect(page.getByRole('status')).toContainText('Candidatura registrada');
  expect(posts).toEqual(['/api/opportunities/job-1/applied']);
  await page.getByRole('button', { name: 'Descartar vaga' }).click();
  await expect(page.getByRole('status')).toContainText('Vaga descartada');
});

test('J3: correção de etapa mantém histórico e envia motivo', async ({ page }) => {
  await page.goto('/candidaturas/app-1');
  await page.getByLabel('Etapa', { exact: true }).selectOption('contact_received');
  await page.getByLabel('Motivo ou correção').fill('Corrigindo a etapa informada');
  const request = page.waitForRequest('**/api/applications/app-1/status');
  await page.getByRole('button', { name: 'Salvar etapa' }).click();
  expect((await request).postDataJSON()).toEqual({
    toStatus: 'contact_received',
    reason: 'Corrigindo a etapa informada',
  });
  await expect(page.getByText('Convite recebido')).toBeVisible();
  await page.getByLabel('O que precisa fazer?').fill('Enviar disponibilidade');
  await page.getByLabel('Prazo (opcional, horário de São Paulo)').fill('2026-11-20');
  const nextAction = page.waitForRequest('**/api/applications/app-1/next-action');
  await page.getByRole('button', { name: 'Salvar próximo passo' }).click();
  expect((await nextAction).postDataJSON()).toEqual({
    text: 'Enviar disponibilidade',
    dueAt: '2026-11-20T23:59:59-03:00',
    duePrecision: 'date_only',
  });
});

test('J5: sessão ausente não consulta dados privados', async ({ page }) => {
  const privateRequests: string[] = [];
  await page.route('**/api/session', (route) =>
    route.fulfill({ json: { authenticated: false, setupRequired: false, csrfToken: null } }),
  );
  page.on('request', (req) => {
    if (new URL(req.url()).pathname.startsWith('/api/') && !req.url().endsWith('/api/session'))
      privateRequests.push(req.url());
  });
  await page.goto('/candidaturas/app-1');
  await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible();
  expect(privateRequests).toEqual([]);
  await expect(page.getByText('Convite recebido')).toHaveCount(0);
});

for (const path of [
  '/',
  '/vagas',
  '/vagas/job-1',
  '/candidaturas',
  '/candidaturas/app-1',
  '/revisao',
  '/revisao/duplicatas',
  '/integracoes',
  '/atividades',
  '/perfil',
  '/criterios',
  '/empresas',
  '/dados',
]) {
  test(`acessibilidade e largura: ${path}`, async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.goto(path);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByText('Carregando…', { exact: true })).toHaveCount(0);
    await page.evaluate(() => document.fonts.ready);
    await page.keyboard.press('Tab');
    await expect(page.getByRole('link', { name: 'Pular para o conteúdo' })).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.locator('main')).toBeFocused();
    expect(errors).toEqual([]);
    const overflowing = await page.locator('main *').evaluateAll((elements) =>
      elements
        .filter((el) => {
          const r = el.getBoundingClientRect();
          return r.width > 0 && r.right > innerWidth + 1;
        })
        .map((el) => el.tagName + '.' + el.className),
    );
    expect(overflowing).toEqual([]);
    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations).toEqual([]);
  });
}
