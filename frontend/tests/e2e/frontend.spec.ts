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
  compatibility: {
    criteriaVersion: 1,
    evaluatorVersion: 1,
    assessedAt: '2026-11-14T10:00:00Z',
    current: true,
    roleStatus: 'matched',
    technologyStatus: 'matched',
    reasonCodes: ['role_matched', 'technology_matched'],
  },
  compatibilityEvidence: [],
  compatibilityCriteria: {
    targetRoles: ['Desenvolvedor Java'],
    stackKeywords: ['Java', 'Spring Boot'],
  },
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
  seniority: { label: null, status: 'absent', reason: null },
  corrections: [],
  timeline: [],
};
const assessment = {
  criteriaVersion: 1,
  state: 'ready',
  stale: false,
  assessedCount: 1,
  remainingCount: 0,
  startedAt: null,
  finishedAt: '2026-11-14T10:00:00Z',
  errorCode: null,
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
  assessment,
  selectionToday: { localDate: '2026-11-14', count: 1, consumedCount: 1, limit: 10, items: [job] },
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
  pendingReviewItems: [],
};
const list = (items: unknown[]) => ({ items, dataAsOf: null });
const opportunitiesList = (items: unknown[]) => ({ items, dataAsOf: null, assessment });

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
    '#6B6B73',
    '#6B6B73',
    '#6B6B73',
    '#6B6B73',
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
    await expect(reopenedCards.nth(9)).toHaveCSS('--c', '#6B6B73');
  } finally {
    await context.close();
  }
});

test.beforeEach(async ({ page }) => {
  const currentJob = structuredClone(job) as Omit<typeof job, 'corrections' | 'timeline'> & {
    facts: string[];
    corrections: Array<{
      id: string;
      fieldName: string;
      previousValue: string | null;
      correctedValue: string | null;
      reason: string | null;
      authorLabel: string;
      correctedAt: string;
    }>;
    timeline: Array<{
      id: string;
      kind: string;
      actorLabel: string;
      description: string;
      note: string | null;
      at: string;
    }>;
  };
  currentJob.facts = ['Java obrigatório', 'Docker desejável'];
  currentJob.corrections = [];
  currentJob.timeline = [
    {
      id: 'tl-1',
      kind: 'discovered',
      actorLabel: 'bot Busca de vagas',
      description: 'Encontrada em SAP Careers.',
      note: null,
      at: '2026-11-14T10:00:00Z',
    },
  ];
  const currentHome = {
    ...home,
    pendingReviewItems: [
      {
        id: 'pending-1',
        kind: 'review_item',
        kindLabel: 'FILA DE REVISÃO',
        title: 'Confirmar unidade salarial',
        summary: 'Valor citado sem indicar mensal ou anual.',
        targetRoute: '/revisao',
      },
    ],
  };

  await page.route(/^http:\/\/127\.0\.0\.1:4173\/api\//, async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    let data: unknown = list([]);
    if (path === '/api/session')
      data = { authenticated: true, setupRequired: false, csrfToken: 'test-csrf' };
    else if (path === '/api/opportunities/job-1/corrections' && request.method() === 'POST') {
      const payload = request.postDataJSON() as { location?: string; reason?: string };
      if (payload.location) {
        currentJob.location = payload.location;
      }
      currentJob.corrections = [
        {
          id: 'corr-1',
          fieldName: 'location',
          previousValue: 'São Leopoldo',
          correctedValue: currentJob.location,
          reason: payload.reason ?? null,
          authorLabel: 'corrigido por você',
          correctedAt: '2026-11-15T11:00:00Z',
        },
      ];
      data = currentJob;
    } else if (path === '/api/opportunities/job-1/external-visit' && request.method() === 'POST') {
      currentJob.timeline.push({
        id: 'tl-open-1',
        kind: 'external_opened',
        actorLabel: 'você',
        description: 'Abriu o link da vaga.',
        note: 'isso não registra candidatura',
        at: '2026-11-15T11:05:00Z',
      });
      data = {};
    } else if (request.method() !== 'GET') data = {};
    else if (path === '/api/home') data = currentHome;
    else if (path === '/api/opportunities') data = opportunitiesList([currentJob]);
    else if (path === '/api/opportunities/job-1') data = currentJob;
    else if (path === '/api/applications') data = list([application]);
    else if (path === '/api/applications/app-1') data = application;
    else if (path === '/api/salary-estimates')
      data = {
        items: [
          {
            id: 'sal-1',
            criteria: {
              company: 'SAP',
              role: 'Desenvolvedor Java',
              seniority: 'pleno',
              location: 'São Leopoldo',
              workMode: 'hybrid',
              contractType: 'unknown',
            },
            monthlyMin: 7000,
            monthlyMax: 10000,
            originalMin: 7000,
            originalMax: 10000,
            originalUnit: 'monthly',
            currency: 'BRL',
            convertedFromAnnual: false,
            sampleSize: 8,
            source: 'Glassdoor',
            sourceUrl: 'https://www.glassdoor.com.br/',
            consultedAt: '2026-10-01T10:00:00Z',
            expiresAt: '2026-11-01T10:00:00Z',
            stale: true,
            scope: 'company',
          },
        ],
      };
    else if (path === '/api/task-runs')
      data = list([
        {
          id: 'run-1',
          kind: 'search',
          state: 'completed',
          pauseReason: null,
          sourcesConsulted: ['oracle:sap', 'lever:cwi'],
          sourcesFailed: ['gupy:banco'],
          errorSummary: 'Tempo esgotado em 1 fonte.',
          startedAt: '2026-11-14T10:00:00Z',
          finishedAt: '2026-11-14T10:02:00Z',
        },
      ]);
    else if (path === '/api/dedupe-candidates')
      data = list([
        {
          id: 'dup-1',
          opportunityA: currentJob,
          opportunityB: { ...currentJob, id: 'job-2' },
          signal: 'Mesmo título e empresa',
        },
      ]);
    else if (path === '/api/review-items')
      data = list([
        {
          id: 'review-1',
          kind: 'salary_ambiguous',
          summary: 'Confirmar unidade salarial',
          targetRef: currentJob.id,
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
        assessment: {
          criteriaVersion: 1,
          state: 'ready',
          stale: false,
          assessedCount: 1,
          remainingCount: 0,
          startedAt: null,
          finishedAt: '2026-11-14T10:00:00Z',
          errorCode: null,
        },
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

test('padrões epistêmicos, correção pela evidência, linha do tempo e 6 estados', async ({
  page,
}) => {
  await page.goto('/vagas/job-1');
  await expect(page.getByText('você tem', { exact: true })).toBeVisible();
  await expect(page.getByText('não está no perfil', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Linha do tempo' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Preparar' })).toBeVisible();

  await page.getByRole('button', { name: 'Corrigir algum dado →' }).click();
  await page.getByLabel('Local').fill('Porto Alegre (híbrido)');
  await page.getByLabel('Motivo ou origem da correção').fill('Confirmado na descrição');
  await page.getByRole('button', { name: 'Salvar correção' }).click();
  await expect(
    page.locator('.ep-corrected-field').filter({ hasText: 'Porto Alegre' }),
  ).toContainText('corrigido por você');
  await expect(page.getByText('Porto Alegre (híbrido)').first()).toBeVisible();

  await page.evaluate(() => {
    window.open = () => null;
  });
  const visitReq = page.waitForRequest('**/api/opportunities/job-1/external-visit');
  await page.getByRole('link', { name: 'Abrir vaga no site ↗' }).evaluate((el) => {
    el.addEventListener('click', (e) => e.preventDefault(), { once: true });
    (el as HTMLAnchorElement).click();
  });
  await visitReq;
  await expect(page.getByText(/isso não registra candidatura/i)).toBeVisible();

  await page.goto('/atividades');
  await expect(page.getByText('PARCIAL', { exact: true })).toBeVisible();
  await expect(page.getByText('2 de 3 fontes consultadas')).toBeVisible();

  await page.goto('/referencias-salariais');
  await expect(page.getByText('DESATUALIZADO', { exact: true })).toBeVisible();
  await expect(
    page.getByRole('link', { name: 'Atualizar a partir de uma vaga →' }),
  ).toHaveAttribute('href', '/vagas');

  await page.goto('/bots');
  await expect(page.getByText('VAZIO', { exact: true })).toBeVisible();
});

test('J3: correção de etapa mantém histórico e envia motivo', async ({ page }) => {
  await page.goto('/candidaturas/app-1');
  await page.getByLabel('Etapa', { exact: true }).click();
  await page.getByRole('option', { name: 'Contato recebido' }).click();
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
  '/referencias-salariais',
  '/vagas/job-1',
  '/vagas/job-1#corrigir',
  '/candidaturas',
  '/candidaturas/app-1',
  '/revisao',
  '/revisao/duplicatas',
  '/bots',
  '/integracoes',
  '/atividades',
  '/perfil',
  '/criterios',
  '/empresas',
  '/dados',
]) {
  test(`acessibilidade e largura: ${path}`, async ({ page }, testInfo) => {
    await page.setViewportSize(
      testInfo.project.name === 'mobile-360'
        ? { width: 390, height: 844 }
        : { width: 1280, height: 800 },
    );
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
    if (path.endsWith('#corrigir')) {
      await page.getByRole('button', { name: 'Corrigir algum dado →' }).click();
      await expect(page.getByRole('form', { name: 'Corrigir dados da vaga' })).toBeVisible();
      await page.locator('main').focus();
    }
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
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );

    // FR-011: alvos visíveis com altura >= 44 px (checkbox medido pelo seu label associado).
    const smallTargets = await page
      .locator('a[href], button, input, select, textarea, summary')
      .evaluateAll((elements) =>
        elements
          .filter((el) => {
            if (el.closest('[aria-hidden="true"]')) return false;
            const style = getComputedStyle(el);
            if (style.display === 'none' || style.visibility === 'hidden') return false;
            const rect = el.getBoundingClientRect();
            return rect.width > 0 && rect.height > 0;
          })
          .map((el) => {
            const target =
              el instanceof HTMLInputElement && (el.type === 'checkbox' || el.type === 'radio')
                ? (el.closest('label') ??
                  (el.id ? document.querySelector(`label[for="${CSS.escape(el.id)}"]`) : null) ??
                  el)
                : el;
            const rect = target.getBoundingClientRect();
            return {
              tag: target.tagName,
              text: (target.textContent ?? '').trim().slice(0, 40),
              height: Math.round(rect.height * 100) / 100,
            };
          })
          .filter((entry) => entry.height < 44),
      );
    expect(smallTargets).toEqual([]);

    // FR-011: foco de teclado visível nos controles interativos (incluindo checkboxes e nativos).
    const mainControls = page
      .locator('main')
      .locator(
        'a[href]:not([tabindex="-1"]):not([aria-hidden="true"]), ' +
          'button:not(:disabled):not([tabindex="-1"]):not([aria-hidden="true"]), ' +
          'input:not(:disabled):not([tabindex="-1"]):not([aria-hidden="true"]), ' +
          'select:not(:disabled):not([tabindex="-1"]):not([aria-hidden="true"]), ' +
          'textarea:not(:disabled):not([tabindex="-1"]):not([aria-hidden="true"])',
      );
    for (let i = 0; i < (await mainControls.count()); i++) {
      const control = mainControls.nth(i);
      const tabbable = await control.evaluate((el) => {
        if (el.closest('[aria-hidden="true"]')) return false;
        if ('disabled' in el && (el as HTMLButtonElement | HTMLInputElement).disabled) return false;
        if ((el as HTMLElement).tabIndex < 0) return false;
        if (el instanceof HTMLInputElement && el.type === 'hidden') return false;
        const style = getComputedStyle(el);
        if (style.display === 'none' || style.visibility === 'hidden') return false;
        const rect = el.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0;
      });
      if (!tabbable) continue;
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

    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations).toEqual([]);
  });
}
