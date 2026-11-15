import type { Page } from '@playwright/test';

export function colorJob(companyName: string, id = 'color-0') {
  return {
    id,
    title: `Vaga ${id}`,
    companyName,
    location: 'Porto Alegre',
    workMode: 'remote' as const,
    contractType: 'clt' as const,
    salary: null,
    fitReasons: ['Java e Spring'],
    userDecision: 'none' as 'none' | 'discarded' | 'applied',
    availability: 'unconfirmed' as const,
    facts: ['Java'],
    inferred: [],
    missing: ['Salário'],
    sources: [],
  };
}

export function colorJobs() {
  return [
    'SAP',
    'PicPay',
    'BV',
    'CWI',
    'DB',
    'CI&T',
    'Itaú',
    'Sicredi',
    'SAP',
    'Empresa Nova',
    '',
    '   ',
    'SAP Outra Empresa',
  ].map((name, i) => colorJob(name, `color-${i}`));
}

/** Estado da API simulada sobrevive a reload e é compartilhável entre contextos de teste. */
export function colorState() {
  return { items: colorJobs(), reversed: false, subset: false };
}

export async function mockCompanyColors(page: Page, state = colorState()) {
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const active = state.items.filter((item) => item.userDecision !== 'discarded');
    let data: unknown;
    if (path === '/api/session') {
      data = { authenticated: true, setupRequired: false, csrfToken: 'test-csrf' };
    } else if (path === '/api/home') {
      data = {
        dataAsOf: null,
        selectionToday: { localDate: '2026-11-15', count: active.length, limit: 20, items: active },
        integrations: [],
        applicationsWithNextActions: [],
        mailNeedingAttention: [],
        reviewQueueCount: 0,
        missedRoutines: [],
      };
    } else if (path === '/api/settings/search-routine') {
      data = { enabled: false };
    } else if (path === '/api/opportunities') {
      const discarded = url.searchParams.get('list') === 'discarded';
      let items = state.items.filter((item) => (item.userDecision === 'discarded') === discarded);
      if (state.reversed) items = items.toReversed();
      if (state.subset) items = items.filter((_, i) => i % 2 === 0);
      data = { items, dataAsOf: null };
    } else {
      const match = path.match(/^\/api\/opportunities\/([^/]+)(?:\/(discard|restore|applied))?$/);
      const item = state.items.find((item) => item.id === match?.[1]);
      if (!item) return route.fallback();
      if (request.method() === 'POST') {
        if (match?.[2] === 'discard') item.userDecision = 'discarded';
        if (match?.[2] === 'restore') item.userDecision = 'none';
        if (match?.[2] === 'applied') item.userDecision = 'applied';
        data = {};
      } else data = item;
    }
    await route.fulfill({ json: data });
  });
  return state;
}
