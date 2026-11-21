import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { createQueryClient } from '../../src/app/queryClient';
import {
  advanceCompatibilityGeneration,
  COMPATIBILITY_QUERY_KEYS,
  getMinCriteriaVersion,
  runCoordinatedRead,
} from '../../src/api/useCompatibilityRefresh';
import { Home } from '../../src/pages/Home';
import { Opportunities } from '../../src/pages/Opportunities';
import { OpportunityDetail } from '../../src/pages/OpportunityDetail';
import { Criteria } from '../../src/pages/Profile';

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function makeStatus(overrides: Record<string, unknown> = {}) {
  return {
    criteriaVersion: 1,
    state: 'ready',
    stale: false,
    assessedCount: 2,
    remainingCount: 0,
    startedAt: null,
    finishedAt: '2026-11-16T10:00:00Z',
    errorCode: null,
    ...overrides,
  };
}

function makeCriteria(overrides: Record<string, unknown> = {}) {
  return {
    version: 1,
    targetRoles: ['Desenvolvedor Backend Java'],
    stackKeywords: ['Java', 'Spring Boot'],
    remotePreferred: true,
    hybridRegions: ['Porto Alegre'],
    cltMinMonthlyBrl: 6000,
    pjMinMonthlyBrl: 9000,
    acceptJuniorIfJustified: true,
    dailySearchTimeLocal: '07:30',
    deadlineHighlightDays: 3,
    urgentHours: 48,
    emailDigestEnabled: false,
    assessment: makeStatus(),
    ...overrides,
  };
}

function makeOpportunity(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    title: `Vaga ${id}`,
    companyName: 'Nubank',
    location: 'Remoto',
    workMode: 'remote',
    contractType: 'clt',
    salary: null,
    fitReasons: ['Compatível com o perfil.'],
    userDecision: 'none',
    availability: 'open',
    compatibility: {
      criteriaVersion: 1,
      evaluatorVersion: 1,
      assessedAt: '2026-11-16T10:00:00Z',
      current: true,
      roleStatus: 'matched',
      technologyStatus: 'matched',
      reasonCodes: ['role_matched', 'technology_matched'],
    },
    ...overrides,
  };
}

function makeHome(overrides: Record<string, unknown> = {}) {
  return {
    dataAsOf: '2026-11-16T10:00:00Z',
    assessment: makeStatus(),
    integrations: [],
    selectionToday: {
      localDate: '2026-11-16',
      count: 1,
      consumedCount: 1,
      limit: 10,
      items: [makeOpportunity('java-1', { title: 'Dev Java Legado' })],
    },
    applicationsWithNextActions: [],
    mailNeedingAttention: [],
    reviewQueueCount: 0,
    missedRoutines: [],
    pendingReviewItems: [],
    ...overrides,
  };
}

function makeDetail(id: string, overrides: Record<string, unknown> = {}) {
  return {
    ...makeOpportunity(id),
    compatibilityEvidence: [],
    compatibilityCriteria: {
      targetRoles: ['Desenvolvedor Backend Java'],
      stackKeywords: ['Java'],
    },
    facts: ['Java 21'],
    inferred: [],
    missing: [],
    seniority: { label: null, status: 'absent', reason: null },
    corrections: [],
    timeline: [],
    sources: [
      { source: 'greenhouse', url: 'https://example.com/vaga', fetchedAt: '2026-11-16T09:00:00Z' },
    ],
    ...overrides,
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('Coordenação de cache e corrida de respostas de compatibilidade', () => {
  it('cancela consultas antigas e invalida criteria, compatibility-status, opportunities, opportunity e home após save e reassess', async () => {
    const client = createQueryClient();
    const origCancel = client.cancelQueries.bind(client);
    const origInvalidate = client.invalidateQueries.bind(client);
    const cancelSpy = vi.spyOn(client, 'cancelQueries');
    const invalidateSpy = vi.spyOn(client, 'invalidateQueries');
    const callOrder: string[] = [];

    cancelSpy.mockImplementation(async (filters, options) => {
      callOrder.push(`cancel:${JSON.stringify(filters?.queryKey)}`);
      return origCancel(filters, options);
    });
    invalidateSpy.mockImplementation(async (filters, options) => {
      callOrder.push(`invalidate:${JSON.stringify(filters?.queryKey)}`);
      return origInvalidate(filters, options);
    });

    let criteria = makeCriteria();
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        const method = init?.method ?? 'GET';
        if (path === '/api/criteria' && method === 'GET') return json(200, criteria);
        if (path === '/api/criteria' && method === 'PUT') {
          criteria = makeCriteria({
            version: 2,
            stackKeywords: ['Python'],
            assessment: makeStatus({
              criteriaVersion: 2,
              state: 'pending',
              stale: true,
              assessedCount: 0,
              remainingCount: 2,
              finishedAt: null,
            }),
          });
          return json(200, criteria);
        }
        if (path === '/api/opportunities/reassess' && method === 'POST') {
          criteria = makeCriteria({
            version: 2,
            stackKeywords: ['Python'],
            assessment: makeStatus({
              criteriaVersion: 2,
              state: 'ready',
              stale: false,
              assessedCount: 2,
              remainingCount: 0,
            }),
          });
          return json(200, {
            criteriaVersion: 2,
            state: 'ready',
            assessedCount: 2,
            remainingCount: 0,
            finishedAt: '2026-11-16T10:15:00Z',
          });
        }
        if (path === '/api/opportunities/compatibility-status' && method === 'GET') {
          return json(200, criteria.assessment);
        }
        return json(404, {});
      }),
    );

    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/criterios']}>
          <Routes>
            <Route path="/criterios" element={<Criteria />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    const stackInput = await screen.findByLabelText(/Tecnologias/i);
    await userEvent.clear(stackInput);
    await userEvent.type(stackInput, 'Python');

    callOrder.length = 0;
    await userEvent.click(screen.getByRole('button', { name: 'Salvar critérios' }));
    expect(await screen.findByText(/Critérios salvos/i)).toBeVisible();

    for (const key of COMPATIBILITY_QUERY_KEYS) {
      const cancelIndex = callOrder.indexOf(`cancel:${JSON.stringify(key)}`);
      const invalidateIndex = callOrder.indexOf(`invalidate:${JSON.stringify(key)}`);
      expect(cancelIndex).toBeGreaterThanOrEqual(0);
      expect(invalidateIndex).toBeGreaterThan(cancelIndex);
    }

    callOrder.length = 0;
    await userEvent.click(screen.getByRole('button', { name: 'Atualizar avaliação' }));
    expect(await screen.findByText(/Compatibilidade atualizada para o critério v2/i)).toBeVisible();

    for (const key of COMPATIBILITY_QUERY_KEYS) {
      const cancelIndex = callOrder.indexOf(`cancel:${JSON.stringify(key)}`);
      const invalidateIndex = callOrder.indexOf(`invalidate:${JSON.stringify(key)}`);
      expect(cancelIndex).toBeGreaterThanOrEqual(0);
      expect(invalidateIndex).toBeGreaterThan(cancelIndex);
    }
    expect(getMinCriteriaVersion(client)).toBe(2);
  });

  it('impede que resposta GET /api/home iniciada antes do save sobrescreva estado v2 mesmo se fetch ignorar AbortSignal', async () => {
    const client = createQueryClient();
    let resolveSlowHomeV1!: (value: Response) => void;
    let homeRequests = 0;
    let saved = false;

    const slowHomeV1Promise = new Promise<Response>((resolve) => {
      resolveSlowHomeV1 = resolve;
    });

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        const method = init?.method ?? 'GET';
        if (path === '/api/home' && method === 'GET') {
          homeRequests++;
          if (homeRequests === 1) {
            return slowHomeV1Promise;
          }
          return json(
            200,
            makeHome({
              assessment: makeStatus({
                criteriaVersion: 2,
                state: 'pending',
                stale: true,
                assessedCount: 1,
                remainingCount: 3,
                finishedAt: null,
              }),
              selectionToday: {
                localDate: '2026-11-16',
                count: 1,
                consumedCount: 1,
                limit: 10,
                items: [
                  makeOpportunity('py-1', {
                    title: 'Dev Python Atual',
                    compatibility: {
                      criteriaVersion: 2,
                      evaluatorVersion: 1,
                      assessedAt: '2026-11-16T10:12:00Z',
                      current: true,
                      roleStatus: 'matched',
                      technologyStatus: 'matched',
                      reasonCodes: ['role_matched'],
                    },
                  }),
                ],
              },
            }),
          );
        }
        if (path === '/api/criteria' && method === 'GET') {
          return json(
            200,
            makeCriteria(
              saved
                ? {
                    version: 2,
                    stackKeywords: ['Python'],
                    assessment: makeStatus({
                      criteriaVersion: 2,
                      state: 'pending',
                      stale: true,
                      assessedCount: 1,
                      remainingCount: 3,
                      finishedAt: null,
                    }),
                  }
                : {},
            ),
          );
        }
        if (path === '/api/criteria' && method === 'PUT') {
          saved = true;
          return json(
            200,
            makeCriteria({
              version: 2,
              stackKeywords: ['Python'],
              assessment: makeStatus({
                criteriaVersion: 2,
                state: 'pending',
                stale: true,
                assessedCount: 1,
                remainingCount: 3,
                finishedAt: null,
              }),
            }),
          );
        }
        return json(404, {});
      }),
    );

    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/']}>
          <Home />
          <Criteria />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    const stackInput = await screen.findByLabelText(/Tecnologias/i);
    await waitFor(() => expect(homeRequests).toBe(1));

    await userEvent.clear(stackInput);
    await userEvent.type(stackInput, 'Python');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar critérios' }));

    expect(await screen.findByText(/Critérios salvos/i)).toBeVisible();
    expect(await screen.findByText('Dev Python Atual')).toBeVisible();
    expect(screen.getAllByText(/3 vaga\(s\) pendente\(s\)/i).length).toBeGreaterThanOrEqual(2);

    // Agora a resposta atrasada v1 finalmente chega na rede — não pode voltar para a tela!
    await act(async () => {
      resolveSlowHomeV1(json(200, makeHome()));
      await Promise.resolve();
    });

    await waitFor(() => {
      expect(screen.queryByText('Dev Java Legado')).not.toBeInTheDocument();
    });
    expect(screen.getByText('Dev Python Atual')).toBeVisible();
  });

  it('runCoordinatedRead rejeita resposta atrasada de geração/versão antiga e refaz leitura atual', async () => {
    const client = new QueryClient();
    let reads = 0;

    const result = await runCoordinatedRead(client, {
      fetcher: async () => {
        reads++;
        if (reads === 1) {
          // Simula um save ocorrendo enquanto a primeira leitura estava em voo
          advanceCompatibilityGeneration(client, 2);
          return makeHome({
            assessment: makeStatus({ criteriaVersion: 1, stale: true, remainingCount: 5 }),
          });
        }
        return makeHome({
          assessment: makeStatus({ criteriaVersion: 2, stale: false, remainingCount: 0 }),
        });
      },
      getVersion: (data) => data.assessment.criteriaVersion,
    });

    expect(reads).toBe(2);
    expect(result.assessment.criteriaVersion).toBe(2);
    expect(result.assessment.stale).toBe(false);
  });

  it('Home e Opportunities exibem resultados atuais parciais junto com aviso stale e ocultam itens não atuais nas listas ativas', async () => {
    const client = createQueryClient();

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const path = String(input);
        if (path === '/api/home') {
          return json(
            200,
            makeHome({
              assessment: makeStatus({
                criteriaVersion: 2,
                state: 'pending',
                stale: true,
                assessedCount: 1,
                remainingCount: 2,
                finishedAt: null,
              }),
              selectionToday: {
                localDate: '2026-11-16',
                count: 1,
                consumedCount: 4,
                limit: 10,
                items: [
                  makeOpportunity('cur-1', {
                    title: 'Python Atualizada',
                    compatibility: {
                      criteriaVersion: 2,
                      evaluatorVersion: 1,
                      assessedAt: '2026-11-16T10:15:00Z',
                      current: true,
                      roleStatus: 'matched',
                      technologyStatus: 'matched',
                      reasonCodes: ['role_matched'],
                    },
                  }),
                  makeOpportunity('old-1', {
                    title: 'Java Obsoleta Não Deve Aparecer',
                    compatibility: {
                      criteriaVersion: 1,
                      evaluatorVersion: 1,
                      assessedAt: '2026-11-16T09:00:00Z',
                      current: false,
                      roleStatus: 'matched',
                      technologyStatus: 'matched',
                      reasonCodes: ['role_matched'],
                    },
                  }),
                ],
              },
            }),
          );
        }
        if (path === '/api/settings/search-routine') return json(200, { enabled: false });
        if (path.startsWith('/api/opportunities?list=selection')) {
          return json(200, {
            dataAsOf: '2026-11-16T10:15:00Z',
            assessment: makeStatus({
              criteriaVersion: 2,
              state: 'running',
              stale: true,
              assessedCount: 1,
              remainingCount: 2,
              finishedAt: null,
            }),
            items: [
              makeOpportunity('cur-1', {
                title: 'Python Atualizada Lista',
                compatibility: {
                  criteriaVersion: 2,
                  evaluatorVersion: 1,
                  assessedAt: '2026-11-16T10:15:00Z',
                  current: true,
                  roleStatus: 'matched',
                  technologyStatus: 'matched',
                  reasonCodes: ['role_matched'],
                },
              }),
              makeOpportunity('stale-list', {
                title: 'Java Antiga Lista Oculta',
                compatibility: {
                  criteriaVersion: 1,
                  evaluatorVersion: 1,
                  assessedAt: '2026-11-16T09:00:00Z',
                  current: false,
                  roleStatus: 'matched',
                  technologyStatus: 'matched',
                  reasonCodes: ['role_matched'],
                },
              }),
            ],
          });
        }
        if (path.startsWith('/api/opportunities?list=discarded')) {
          return json(200, {
            dataAsOf: '2026-11-16T10:15:00Z',
            assessment: makeStatus({
              criteriaVersion: 2,
              state: 'pending',
              stale: true,
              assessedCount: 1,
              remainingCount: 2,
              finishedAt: null,
            }),
            items: [
              makeOpportunity('disc-1', {
                title: 'Descartada Histórica Visível',
                userDecision: 'discarded',
                compatibility: {
                  criteriaVersion: 1,
                  evaluatorVersion: 1,
                  assessedAt: '2026-11-16T09:00:00Z',
                  current: false,
                  roleStatus: 'mismatched',
                  technologyStatus: 'matched',
                  reasonCodes: ['role_mismatched'],
                },
              }),
            ],
          });
        }
        return json(404, {});
      }),
    );

    const { unmount } = render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/']}>
          <Home />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(await screen.findByText('Python Atualizada')).toBeVisible();
    expect(screen.getByText(/2 vaga\(s\) pendente\(s\)/i)).toBeVisible();
    expect(screen.queryByText('Java Obsoleta Não Deve Aparecer')).not.toBeInTheDocument();

    unmount();

    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/vagas?list=selection']}>
          <Opportunities />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(await screen.findByText('Python Atualizada Lista')).toBeVisible();
    expect(screen.getByText(/Atualizando compatibilidade.*2 vaga\(s\)/i)).toBeVisible();
    expect(screen.queryByText('Java Antiga Lista Oculta')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('link', { name: 'Descartadas' }));
    expect(await screen.findByText('Descartada Histórica Visível')).toBeVisible();
  });

  it('preserva histórico rotulado em OpportunityDetail e invalida compatibility-status ao restaurar vaga', async () => {
    const client = createQueryClient();
    const invalidateSpy = vi.spyOn(client, 'invalidateQueries');
    let restored = false;

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        const method = init?.method ?? 'GET';
        if (path === '/api/opportunities/job-1' && method === 'GET') {
          return json(
            200,
            makeDetail('job-1', {
              userDecision: restored ? 'none' : 'discarded',
              compatibility: {
                criteriaVersion: 1,
                evaluatorVersion: 1,
                assessedAt: '2026-11-16T09:00:00Z',
                current: false,
                roleStatus: 'matched',
                technologyStatus: 'matched',
                reasonCodes: ['role_matched'],
              },
            }),
          );
        }
        if (path === '/api/opportunities/job-1/restore' && method === 'POST') {
          restored = true;
          return json(200, {});
        }
        return json(404, {});
      }),
    );

    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/vagas/job-1']}>
          <Routes>
            <Route path="/vagas/:id" element={<OpportunityDetail />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(
      await screen.findByText(
        /Exibindo o resultado e o retrato dos critérios salvos nessa avaliação/i,
      ),
    ).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: 'Restaurar' }));

    expect(await screen.findByText(/Vaga restaurada\./i)).toBeVisible();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['compatibility-status'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['criteria'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['opportunities'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['home'] });
  });

  it('em falha de PUT /api/criteria não avança versão mínima e permite consultar corretamente depois', async () => {
    const client = createQueryClient();
    let failPut = true;
    let homeFails = true;

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        const method = init?.method ?? 'GET';
        if (path === '/api/criteria' && method === 'GET') return json(200, makeCriteria());
        if (path === '/api/criteria' && method === 'PUT') {
          if (failPut) {
            return json(500, {
              error: { code: 'internal_error', message: 'Falha temporária ao salvar critérios.' },
            });
          }
          return json(
            200,
            makeCriteria({
              version: 2,
              stackKeywords: ['Python'],
              assessment: makeStatus({
                criteriaVersion: 2,
                state: 'pending',
                stale: true,
                remainingCount: 1,
                finishedAt: null,
              }),
            }),
          );
        }
        if (path === '/api/home' && method === 'GET') {
          if (homeFails) {
            return json(500, {
              error: { code: 'internal_error', message: 'Falha ao carregar painel.' },
            });
          }
          return json(200, makeHome());
        }
        return json(404, {});
      }),
    );

    const { unmount } = render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/criterios']}>
          <Criteria />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    const stackInput = await screen.findByLabelText(/Tecnologias/i);
    await userEvent.clear(stackInput);
    await userEvent.type(stackInput, 'Python');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar critérios' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/Falha temporária ao salvar/i);
    expect(getMinCriteriaVersion(client)).toBe(1);

    failPut = false;
    unmount();

    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/']}>
          <Home />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(
      /Falha ao carregar painel/i,
    );
    homeFails = false;
    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }));
    expect(await screen.findByText('Dev Java Legado')).toBeVisible();
  });

  it('resposta atrasada de GET /api/criteria ou GET compatibility-status ignorando AbortSignal não sobrescreve status atual após save/reassess', async () => {
    const client = createQueryClient();
    let resolveSlowCriteria!: (value: Response) => void;
    let resolveSlowStatus!: (value: Response) => void;
    let criteriaRequests = 0;
    let statusRequests = 0;
    let saved = false;
    let reassessed = false;

    const slowCriteriaPromise = new Promise<Response>((resolve) => {
      resolveSlowCriteria = resolve;
    });
    const slowStatusPromise = new Promise<Response>((resolve) => {
      resolveSlowStatus = resolve;
    });

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        const method = init?.method ?? 'GET';
        if (path === '/api/criteria' && method === 'GET') {
          criteriaRequests++;
          if (criteriaRequests === 2) {
            return slowCriteriaPromise;
          }
          if (!saved) {
            return json(
              200,
              makeCriteria({
                version: 1,
                assessment: makeStatus({
                  criteriaVersion: 1,
                  state: 'running',
                  stale: true,
                  remainingCount: 2,
                  finishedAt: null,
                }),
              }),
            );
          }
          return json(
            200,
            makeCriteria({
              version: 2,
              stackKeywords: ['Python'],
              assessment: reassessed
                ? makeStatus({
                    criteriaVersion: 2,
                    state: 'ready',
                    stale: false,
                    assessedCount: 2,
                    remainingCount: 0,
                  })
                : makeStatus({
                    criteriaVersion: 2,
                    state: 'pending',
                    stale: true,
                    assessedCount: 0,
                    remainingCount: 2,
                    finishedAt: null,
                  }),
            }),
          );
        }
        if (path === '/api/criteria' && method === 'PUT') {
          saved = true;
          return json(
            200,
            makeCriteria({
              version: 2,
              stackKeywords: ['Python'],
              assessment: makeStatus({
                criteriaVersion: 2,
                state: 'pending',
                stale: true,
                assessedCount: 0,
                remainingCount: 2,
                finishedAt: null,
              }),
            }),
          );
        }
        if (path === '/api/opportunities/compatibility-status' && method === 'GET') {
          statusRequests++;
          if (statusRequests === 1) {
            return slowStatusPromise;
          }
          return json(
            200,
            makeStatus({
              criteriaVersion: 2,
              state: reassessed ? 'ready' : 'pending',
              stale: !reassessed,
              assessedCount: reassessed ? 2 : 0,
              remainingCount: reassessed ? 0 : 2,
              finishedAt: reassessed ? '2026-11-16T10:20:00Z' : null,
            }),
          );
        }
        if (path === '/api/opportunities/reassess' && method === 'POST') {
          reassessed = true;
          return json(200, {
            criteriaVersion: 2,
            state: 'ready',
            assessedCount: 2,
            remainingCount: 0,
            finishedAt: '2026-11-16T10:20:00Z',
          });
        }
        return json(404, {});
      }),
    );

    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/criterios']}>
          <Criteria />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    const stackInput = await screen.findByLabelText(/Tecnologias/i);
    await waitFor(() => expect(statusRequests).toBe(1));
    // Inicia refetch lento de GET /api/criteria antes do save
    void client.refetchQueries({ queryKey: ['criteria'] });
    await waitFor(() => expect(criteriaRequests).toBe(2));

    await userEvent.clear(stackInput);
    await userEvent.type(stackInput, 'Python');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar critérios' }));
    expect(await screen.findByText(/Critérios salvos/i)).toBeVisible();

    await userEvent.click(screen.getByRole('button', { name: 'Atualizar avaliação' }));
    expect(await screen.findByText(/Compatibilidade atualizada para o critério v2/i)).toBeVisible();

    await act(async () => {
      resolveSlowCriteria(
        json(
          200,
          makeCriteria({
            version: 1,
            assessment: makeStatus({
              criteriaVersion: 1,
              state: 'running',
              stale: true,
              remainingCount: 2,
              finishedAt: null,
            }),
          }),
        ),
      );
      resolveSlowStatus(
        json(
          200,
          makeStatus({
            criteriaVersion: 1,
            state: 'running',
            stale: true,
            assessedCount: 0,
            remainingCount: 2,
            finishedAt: null,
          }),
        ),
      );
      await Promise.resolve();
    });

    const cachedStatus = client.getQueryData<{
      criteriaVersion: number;
      state: string;
      stale: boolean;
    }>(['compatibility-status']);
    const cachedCriteria = client.getQueryData<{
      version: number;
      assessment: { state: string; stale: boolean };
    }>(['criteria']);
    expect(cachedStatus?.criteriaVersion).toBe(2);
    expect(cachedStatus?.state).toBe('ready');
    expect(cachedStatus?.stale).toBe(false);
    expect(cachedCriteria?.version).toBe(2);
    expect(cachedCriteria?.assessment.state).toBe('ready');
    expect(screen.getByText(/Compatibilidade atualizada para o critério v2/i)).toBeVisible();
  });
});
