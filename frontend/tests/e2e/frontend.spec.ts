import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mockCompanyColors } from './support/company-colors.js';

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

test('cores: marcas e alternativas nos destaques dos cards', async ({ page }) => {
  const state = await mockCompanyColors(page);
  const expected = [
    '#0070F2',
    '#21C25E',
    '#005CA9',
    '#FF8A00',
    '#F70773',
    '#FA5A50',
    '#EC7000',
    '#2F7D12',
    '#0070F2',
    '#0369A1',
    '#6B6B73',
    '#6B6B73',
    '#0F766E',
  ];
  const requests: string[] = [];
  page.on('request', (request) => requests.push(request.url()));
  for (const path of [
    '/',
    '/vagas?list=selection',
    '/vagas?list=secondary',
    '/vagas?list=discarded',
  ]) {
    state.items.forEach((item) => {
      item.userDecision = path.includes('discarded') ? 'discarded' : 'none';
    });
    await page.goto(path);
    await expect(page.locator('.job-card')).toHaveCount(expected.length);
    const colors = await page.locator('.job-card').evaluateAll((cards) =>
      cards.map((card) => {
        const style = getComputedStyle(card);
        const mark = card.querySelector('.company-mark span');
        const hatch = card.querySelector('.company-mark');
        return {
          color: style.getPropertyValue('--c').trim(),
          left: getComputedStyle(card, '::before').borderLeftColor,
          top: getComputedStyle(card, '::after').borderTopColor,
          mark: mark && getComputedStyle(mark).borderTopColor,
          hatch: hatch && getComputedStyle(hatch).backgroundImage,
        };
      }),
    );
    expect(colors.map((entry) => entry.color)).toEqual(expected);
    for (const entry of colors) {
      const rgb = `rgb(${entry.color
        .slice(1)
        .match(/../g)
        ?.map((hex) => parseInt(hex, 16))
        .join(', ')})`;
      expect(entry.left).toBe(rgb);
      expect(entry.top).toBe(rgb);
      expect(entry.mark).toBe(rgb);
      expect(entry.hatch).toContain(rgb);
    }
  }
  await page.goto('/vagas/color-2');
  await expect(page.locator('.job-detail')).toHaveCSS('--c', '#005CA9');
  expect(requests.every((url) => new URL(url).origin === new URL(page.url()).origin)).toBe(true);
  expect(
    requests
      .map((url) => new URL(url).pathname)
      .filter(
        (path) =>
          path.startsWith('/api/') &&
          ![
            '/api/session',
            '/api/home',
            '/api/settings/search-routine',
            '/api/opportunities',
            '/api/opportunities/color-2',
          ].includes(path),
      ),
  ).toEqual([]);
});

test('cores: leitura, contraste, foco e ações em todas as empresas', async ({ page }) => {
  const state = await mockCompanyColors(page);
  for (const path of ['/', '/vagas', '/vagas?list=discarded']) {
    state.items.forEach((item) => {
      item.userDecision = path.includes('discarded') ? 'discarded' : 'none';
    });
    await page.goto(path);
    await expect(page.locator('.job-card')).toHaveCount(13);
    await page.evaluate(() => document.fonts.ready);
    const visibleCards = page.locator('.job-card:visible');
    const controls = visibleCards.locator('.job-title a, .card-actions button');
    // Chegar a cada controle por Tab comprova sua presença na ordem de teclado.
    {
      for (let i = 0; i < (await controls.count()); i++) {
        const control = controls.nth(i);
        for (
          let step = 0;
          step < 100 && !(await control.evaluate((el) => el === document.activeElement));
          step++
        ) {
          await page.keyboard.press('Tab');
        }
        await expect(control).toBeFocused();
        await expect(control).toHaveCSS('outline-style', 'dashed');
        await expect(control).toHaveCSS('outline-width', '3px');
      }
    }
    if (path !== '/') {
      await expect(page.getByText('Empresa não informada', { exact: true })).toHaveCount(2);
      await expect(page.getByText('salário a confirmar', { exact: true })).toHaveCount(13);
    }
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
      ),
    ).toBe(true);
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
    const textColors = await visibleCards.evaluateAll((cards) =>
      cards.map((card) => ({
        text: getComputedStyle(card.querySelector('.company-name') ?? card).color,
        background: getComputedStyle(card).backgroundColor,
        opacity: getComputedStyle(card).opacity,
      })),
    );
    for (const style of textColors) {
      expect(style.text).toBe('rgb(27, 27, 31)');
      expect(style.background).toBe('rgb(255, 253, 246)');
      expect(style.opacity).toBe('1');
    }
    if (path === '/' && page.viewportSize()?.width === 360) {
      await expect(page.getByRole('link', { name: /Ver as outras/ })).toBeVisible();
      await page.getByRole('link', { name: /Ver as outras/ }).click();
      await expect(page.locator('.job-card:visible')).toHaveCount(13);
    }
  }
  state.items.forEach((item) => {
    item.userDecision = 'none';
  });
  await page.goto('/vagas');
  const first = page.locator('.job-card').first();
  await first.getByRole('button', { name: 'Me candidatei' }).focus();
  await page.keyboard.press('Enter');
  await expect(first.getByRole('status')).toContainText('Candidatura registrada');
  await first.getByRole('button', { name: 'Descartar' }).focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('.job-card')).toHaveCount(12);
  await page.getByRole('link', { name: 'Descartadas', exact: true }).click();
  await page.getByRole('button', { name: 'Restaurar' }).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('button', { name: 'Restaurar' })).toHaveCount(0);
});

test('cores: identidade estável ao navegar e alterar decisões', async ({ page, browser }) => {
  const state = await mockCompanyColors(page);
  const target = page
    .locator('.job-card')
    .filter({ has: page.getByRole('link', { name: 'Vaga color-0', exact: true }) });
  for (const path of ['/', '/vagas?list=selection', '/vagas?list=secondary']) {
    await page.goto(path);
    await expect(target).toHaveCSS('--c', '#0070F2');
  }
  state.reversed = true;
  state.subset = true;
  await page.reload();
  await expect(page.locator('.job-card')).toHaveCount(7);
  await expect(target).toHaveCSS('--c', '#0070F2');
  await page.reload();
  await expect(target).toHaveCSS('--c', '#0070F2');
  state.reversed = false;
  state.subset = false;
  await page.goto('/vagas');
  await target.getByRole('button', { name: 'Descartar', exact: true }).click();
  await expect(target).toHaveCount(0);
  await page.getByRole('link', { name: 'Descartadas', exact: true }).click();
  await expect(target).toHaveCSS('--c', '#0070F2');
  await target.getByRole('button', { name: 'Restaurar' }).click();
  await expect(target).toHaveCount(0);
  await page.getByRole('link', { name: 'Seleção do dia', exact: true }).click();
  await expect(target).toHaveCSS('--c', '#0070F2');
  state.items[0].companyName = 'BV';
  await page.reload();
  await expect(target).toHaveCSS('--c', '#005CA9');
  const context = await browser.newContext({ viewport: page.viewportSize() ?? undefined });
  try {
    const reopened = await context.newPage();
    await mockCompanyColors(reopened, state);
    await reopened.goto(new URL('/vagas', page.url()).href);
    const reopenedCards = reopened.locator('.job-card');
    await expect(reopenedCards.first()).toHaveCSS('--c', '#005CA9');
    await expect(reopenedCards.nth(9)).toHaveCSS('--c', '#0369A1');
  } finally {
    await context.close();
  }
});

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
