import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import type {
  Application,
  ApplicationDetail as ApplicationDetailDto,
  DedupeCandidate,
  Opportunity,
  ReviewItem,
  TaskRun,
} from '../../src/api/panel';
import { Activities } from '../../src/pages/Activities';
import { ApplicationDetail } from '../../src/pages/ApplicationDetail';
import { Applications } from '../../src/pages/Applications';
import { DedupeReview } from '../../src/pages/DedupeReview';
import { Integrations } from '../../src/pages/Integrations';
import { NotFound, Placeholder } from '../../src/pages/Placeholder';
import { ReviewQueue } from '../../src/pages/ReviewQueue';

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
}

function renderWithProviders(ui: ReactNode, initialEntries: string[] = ['/']) {
  const queryClient = createTestQueryClient();
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    </MemoryRouter>,
  );
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function emptyHomePayload(integrations: Array<Record<string, unknown>> = []) {
  return {
    dataAsOf: null,
    assessment: {
      criteriaVersion: 1,
      state: 'ready',
      stale: false,
      assessedCount: 0,
      remainingCount: 0,
      startedAt: null,
      finishedAt: null,
      errorCode: null,
    },
    integrations,
    selectionToday: {
      localDate: '2026-11-20',
      count: 0,
      consumedCount: 0,
      limit: 5,
      items: [],
    },
    applicationsWithNextActions: [],
    mailNeedingAttention: [],
    reviewQueueCount: 0,
    missedRoutines: [],
    pendingReviewItems: [],
  };
}

function makeOpportunity(overrides: Partial<Opportunity> = {}): Opportunity {
  return {
    id: 'opp-1',
    title: 'Pessoa Desenvolvedora Java',
    companyName: 'SAP',
    location: 'São Leopoldo, RS',
    workMode: 'hybrid',
    contractType: 'clt',
    salary: null,
    fitReasons: [],
    userDecision: 'none',
    availability: 'open',
    deadlineAt: null,
    publishedAt: null,
    source: null,
    companyTier: null,
    ...overrides,
  };
}

function getPostedJson(
  fetchMock: ReturnType<typeof vi.fn>,
  expectedPath: string,
): Record<string, unknown> {
  const call = fetchMock.mock.calls.find(
    ([path, init]) =>
      path === expectedPath &&
      typeof init === 'object' &&
      init !== null &&
      (init as RequestInit).method === 'POST',
  );
  expect(call).toBeDefined();
  const init = call?.[1] as RequestInit | undefined;
  return JSON.parse(String(init?.body ?? '{}')) as Record<string, unknown>;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('Integrations (/integracoes)', () => {
  it('exibe estado VAZIO quando não há integrações nem tarefas pausadas', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (path: string) => {
        if (path === '/api/home') return jsonResponse(emptyHomePayload([]));
        if (path === '/api/task-runs') return jsonResponse({ dataAsOf: null, items: [] });
        return jsonResponse({}, 404);
      }),
    );

    renderWithProviders(<Integrations />, ['/integracoes']);

    expect(await screen.findByText('Nenhuma integração informada.')).toBeInTheDocument();
    expect(screen.getByText('Nenhuma tarefa pausada.')).toBeInTheDocument();
    expect(screen.getAllByText('VAZIO')).toHaveLength(2);
  });

  it('exibe integração em FALHA, PARCIAL, ausência hachurada e retoma tarefa pausada com fontes', async () => {
    const user = userEvent.setup();
    const taskRuns: TaskRun[] = [
      {
        id: 'run-paused-1',
        state: 'paused',
        pauseReason: 'source_unavailable',
        sourcesConsulted: ['oracle:sap', 'lever:cwi'],
        sourcesFailed: ['gupy:banco'],
        errorSummary: null,
      },
    ];
    const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
      if (path === '/api/home') {
        return jsonResponse(
          emptyHomePayload([
            {
              id: 'gmail',
              state: 'disconnected',
              lastSuccessAt: null,
              lastAttemptAt: '2026-11-20T12:00:00Z',
              detail: 'Token OAuth expirado.',
            },
            {
              id: 'buscador-vagas',
              state: 'degraded',
              lastSuccessAt: '2026-11-20T10:00:00Z',
              lastAttemptAt: '2026-11-20T12:00:00Z',
              detail: 'Gupy retornou timeout.',
            },
            {
              id: 'linkedin',
              state: 'ok',
              lastSuccessAt: null,
              lastAttemptAt: null,
            },
          ]),
        );
      }
      if (path === '/api/task-runs') {
        return jsonResponse({ dataAsOf: null, items: taskRuns });
      }
      if (path === '/api/task-runs/run-paused-1/resume' && init?.method === 'POST') {
        return new Response(null, { status: 204 });
      }
      return jsonResponse({}, 404);
    });
    vi.stubGlobal('fetch', fetchMock);

    const { container } = renderWithProviders(<Integrations />, ['/integracoes']);

    expect(await screen.findByText('Integração desconectada')).toBeInTheDocument();
    expect(screen.getByText('Token OAuth expirado.')).toBeInTheDocument();
    expect(screen.getByText('FALHA')).toBeInTheDocument();

    expect(screen.getByText('Integração com falhas parciais')).toBeInTheDocument();
    expect(screen.getByText('Gupy retornou timeout.')).toBeInTheDocument();
    expect(screen.getAllByText('PARCIAL')).toHaveLength(2);

    expect(screen.getByText('Fonte indisponível')).toBeInTheDocument();
    expect(
      screen.getByText('2 de 3 fontes consultadas · com falha: gupy:banco.'),
    ).toBeInTheDocument();

    const absentDetails = screen.getAllByText('Nenhum detalhe adicional.');
    expect(absentDetails).toHaveLength(2);
    for (const node of absentDetails) {
      expect(node.closest('.ep-absent.hatch')).not.toBeNull();
    }
    expect(container.querySelectorAll('.ep-absent.hatch').length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText('sem atualização bem-sucedida ainda').length).toBeGreaterThanOrEqual(
      2,
    );

    await user.click(screen.getByRole('button', { name: 'Retomar tarefa' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/task-runs/run-paused-1/resume',
        expect.objectContaining({ method: 'POST' }),
      );
    });
    expect(
      await screen.findByText(/Retomada solicitada a partir do último ponto salvo\./),
    ).toBeInTheDocument();
  });

  it('exibe estado FALHA com botão de recuperação quando /api/home falha', async () => {
    const user = userEvent.setup();
    let attempt = 0;
    const fetchMock = vi.fn(async (path: string) => {
      if (path === '/api/home') {
        attempt += 1;
        if (attempt === 1) {
          return jsonResponse(
            { error: { code: 'service_down', message: 'Serviço local indisponível.' } },
            500,
          );
        }
        return jsonResponse(emptyHomePayload([]));
      }
      if (path === '/api/task-runs') {
        return jsonResponse({ dataAsOf: null, items: [] });
      }
      return jsonResponse({}, 404);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithProviders(<Integrations />, ['/integracoes']);

    expect(await screen.findByText('Não foi possível carregar')).toBeInTheDocument();
    expect(screen.getByText('FALHA')).toBeInTheDocument();
    expect(screen.getByText('Serviço local indisponível.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Tentar de novo' }));

    expect(await screen.findByText('Nenhuma integração informada.')).toBeInTheDocument();
  });
});

describe('Activities (/atividades)', () => {
  it('exibe CARREGANDO com contagem de fontes, PARCIAL e FALHA', async () => {
    const items: TaskRun[] = [
      {
        id: 'run-running-1',
        state: 'running',
        pauseReason: null,
        sourcesConsulted: ['oracle:sap', 'lever:cwi'],
        sourcesFailed: ['gupy:banco'],
        errorSummary: null,
        startedAt: '2026-11-20T12:00:00Z',
      },
      {
        id: 'run-partial-1',
        state: 'completed',
        pauseReason: null,
        sourcesConsulted: ['oracle:sap', 'lever:cwi'],
        sourcesFailed: ['gupy:banco'],
        errorSummary: 'Tempo esgotado em 1 fonte.',
      },
      {
        id: 'run-failed-1',
        state: 'failed',
        pauseReason: null,
        sourcesConsulted: [],
        sourcesFailed: ['oracle:sap'],
        errorSummary: 'Falha de rede ao consultar fontes.',
      },
    ];

    vi.stubGlobal(
      'fetch',
      vi.fn(async (path: string) => {
        if (path === '/api/task-runs') return jsonResponse({ dataAsOf: null, items });
        if (path === '/api/activity') return jsonResponse({ dataAsOf: null, items: [] });
        return jsonResponse({}, 404);
      }),
    );

    const { container } = renderWithProviders(<Activities />, ['/atividades']);

    expect(await screen.findByText('CARREGANDO')).toBeInTheDocument();
    expect(screen.getByText('Buscando vagas… 2 de 3 fontes')).toBeInTheDocument();
    const progressFill = container.querySelector('.surface-progress-fill.hatch');
    expect(progressFill).toHaveStyle({ width: '67%' });

    expect(screen.getByText('PARCIAL')).toBeInTheDocument();
    expect(screen.getByText('2 de 3 fontes consultadas')).toBeInTheDocument();
    expect(screen.getByText(/Fontes com falha: gupy:banco\./)).toBeInTheDocument();

    expect(screen.getByText('FALHA')).toBeInTheDocument();
    expect(screen.getByText('Execução falhou')).toBeInTheDocument();
    expect(screen.getByText('Falha de rede ao consultar fontes.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Verificar integrações →' })).toHaveAttribute(
      'href',
      '/integracoes',
    );
  });

  it('exibe VAZIO quando não há execuções nem alterações e FALHA ao errar carregamento', async () => {
    const user = userEvent.setup();
    let shouldFail = true;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (path: string) => {
        if (path === '/api/task-runs') {
          if (shouldFail) {
            return jsonResponse(
              { error: { code: 'unavailable', message: 'Falha ao buscar execuções.' } },
              500,
            );
          }
          return jsonResponse({ dataAsOf: null, items: [] });
        }
        if (path === '/api/activity') return jsonResponse({ dataAsOf: null, items: [] });
        return jsonResponse({}, 404);
      }),
    );

    renderWithProviders(<Activities />, ['/atividades']);

    expect(await screen.findByText('Não foi possível carregar')).toBeInTheDocument();
    expect(screen.getByText('FALHA')).toBeInTheDocument();
    expect(screen.getByText('Falha ao buscar execuções.')).toBeInTheDocument();

    shouldFail = false;
    await user.click(screen.getByRole('button', { name: 'Tentar de novo' }));

    expect(await screen.findByText('Nenhuma tarefa executada.')).toBeInTheDocument();
    expect(screen.getByText('Nenhuma alteração registrada.')).toBeInTheDocument();
    expect(screen.getAllByText('VAZIO')).toHaveLength(2);
  });
});

describe('ReviewQueue (/revisao) e DedupeReview (/revisao/duplicatas)', () => {
  it('ReviewQueue exibe VAZIO quando não há pendências', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (path: string) => {
        if (path === '/api/review-items') return jsonResponse({ dataAsOf: null, items: [] });
        return jsonResponse({}, 404);
      }),
    );

    renderWithProviders(<ReviewQueue />, ['/revisao']);

    expect(await screen.findByText('Tudo revisado. Nenhuma decisão pendente.')).toBeInTheDocument();
    expect(screen.getByText('VAZIO')).toBeInTheDocument();
  });

  it('ReviewQueue exibe PENDENTE DE REVISÃO, envia resolução e lida com falha/sucesso', async () => {
    const user = userEvent.setup();
    let items: ReviewItem[] = [
      {
        id: 'rev-1',
        kind: 'salary_ambiguous',
        summary: 'Salário informado como faixa anual em USD.',
        targetRef: 'opp-1',
        createdAt: '2026-11-20T10:00:00Z',
      },
      {
        id: 'rev-2',
        kind: 'dedupe_candidate',
        summary: 'Possível vaga duplicada no Itaú.',
        targetRef: 'dedupe-1',
        createdAt: '2026-11-20T10:05:00Z',
      },
    ];
    let resolveCalls = 0;
    const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
      if (path === '/api/review-items') return jsonResponse({ dataAsOf: null, items });
      if (path === '/api/review-items/rev-1/resolve' && init?.method === 'POST') {
        resolveCalls += 1;
        if (resolveCalls === 1) {
          return jsonResponse(
            {
              error: { code: 'resolve_failed', message: 'Não foi possível registrar a resolução.' },
            },
            500,
          );
        }
        items = items.filter((entry) => entry.id !== 'rev-1');
        return new Response(null, { status: 204 });
      }
      return jsonResponse({}, 404);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithProviders(<ReviewQueue />, ['/revisao']);

    expect(
      await screen.findByText('PENDENTE DE REVISÃO · Salário a confirmar'),
    ).toBeInTheDocument();
    expect(screen.getByText('PENDENTE DE REVISÃO · Possível duplicata')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Comparar anúncios →' })).toHaveAttribute(
      'href',
      '/revisao/duplicatas',
    );

    const submitButton = screen.getByRole('button', { name: 'Registrar resolução' });
    expect(submitButton).toBeDisabled();

    await user.type(
      screen.getByLabelText('Sua decisão'),
      'Confirmado R$ 10.000/mês CLT na entrevista',
    );
    expect(submitButton).toBeEnabled();
    await user.click(submitButton);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível registrar a resolução.',
    );

    await user.click(submitButton);

    await waitFor(() => {
      expect(getPostedJson(fetchMock, '/api/review-items/rev-1/resolve')).toEqual({
        resolution: 'Confirmado R$ 10.000/mês CLT na entrevista',
      });
    });

    await waitFor(() => {
      expect(
        screen.queryByText('Salário informado como faixa anual em USD.'),
      ).not.toBeInTheDocument();
    });
  });

  it('DedupeReview exibe VAZIO quando não há candidatos', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (path: string) => {
        if (path === '/api/dedupe-candidates') return jsonResponse({ dataAsOf: null, items: [] });
        return jsonResponse({}, 404);
      }),
    );

    renderWithProviders(<DedupeReview />, ['/revisao/duplicatas']);

    expect(await screen.findByText('Nenhuma possível duplicata para revisar.')).toBeInTheDocument();
    expect(screen.getByText('VAZIO')).toBeInTheDocument();
  });

  it('DedupeReview exibe PENDENTE DE REVISÃO e envia merge e reject com feedback', async () => {
    const user = userEvent.setup();
    let candidates: DedupeCandidate[] = [
      {
        id: 'ded-1',
        signal: 'Mesmo título e empresa em Gupy e LinkedIn',
        opportunityA: makeOpportunity({
          id: 'opp-a',
          title: 'Java Sênior · Gupy',
          companyName: 'Itaú',
        }),
        opportunityB: makeOpportunity({
          id: 'opp-b',
          title: 'Java Sênior · LinkedIn',
          companyName: 'Itaú',
        }),
      },
      {
        id: 'ded-2',
        signal: 'Cargos parecidos na mesma unidade',
        opportunityA: makeOpportunity({ id: 'opp-c', title: 'Tech Lead Java', companyName: 'SAP' }),
        opportunityB: makeOpportunity({ id: 'opp-d', title: 'Arquiteto Java', companyName: 'SAP' }),
      },
    ];
    const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
      if (path === '/api/dedupe-candidates')
        return jsonResponse({ dataAsOf: null, items: candidates });
      if (path === '/api/dedupe-candidates/ded-1/merge' && init?.method === 'POST') {
        candidates = candidates.filter((c) => c.id !== 'ded-1');
        return new Response(null, { status: 204 });
      }
      if (path === '/api/dedupe-candidates/ded-2/reject' && init?.method === 'POST') {
        candidates = candidates.filter((c) => c.id !== 'ded-2');
        return new Response(null, { status: 204 });
      }
      return jsonResponse({}, 404);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithProviders(<DedupeReview />, ['/revisao/duplicatas']);

    expect(await screen.findAllByText('PENDENTE DE REVISÃO · DUPLICATA')).toHaveLength(2);
    expect(
      screen.getByText('Sinal encontrado: Mesmo título e empresa em Gupy e LinkedIn'),
    ).toBeInTheDocument();

    const mergeButtons = screen.getAllByRole('button', { name: 'São a mesma vaga: unir' });
    await user.click(mergeButtons[0]);

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/dedupe-candidates/ded-1/merge',
        expect.objectContaining({ method: 'POST' }),
      );
    });
    expect(await screen.findByRole('status')).toHaveTextContent('✓ Decisão registrada.');
    await waitFor(() => {
      expect(screen.queryByText('Java Sênior · Gupy')).not.toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: 'São vagas diferentes' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/dedupe-candidates/ded-2/reject',
        expect.objectContaining({ method: 'POST' }),
      );
    });
    expect(await screen.findByText('Nenhuma possível duplicata para revisar.')).toBeInTheDocument();
  });
});

describe('Applications (/candidaturas) e ApplicationDetail (/candidaturas/:id)', () => {
  it('Applications exibe fatos, ausências hachuradas e estado VAZIO', async () => {
    const user = userEvent.setup();
    const applications: Application[] = [
      {
        id: 'app-1',
        opportunityId: 'opp-1',
        title: 'Desenvolvedora Backend Java',
        companyName: 'SAP',
        status: 'interview',
        nextAction: 'Enviar desafio técnico',
        nextActionDueAt: '2099-11-28',
        duePrecision: 'date_only',
      },
      {
        id: 'app-2',
        opportunityId: 'opp-2',
        title: 'Engenheira de Software',
        companyName: '   ',
        status: 'submitted_awaiting',
        nextAction: null,
        nextActionDueAt: null,
        duePrecision: 'ambiguous',
      },
    ];

    vi.stubGlobal(
      'fetch',
      vi.fn(async (path: string) => {
        if (path === '/api/applications') {
          return jsonResponse({ dataAsOf: '2026-11-20T12:00:00Z', items: applications });
        }
        return jsonResponse({}, 404);
      }),
    );

    const { container } = renderWithProviders(<Applications />, ['/candidaturas']);

    expect(await screen.findByText('Desenvolvedora Backend Java')).toBeInTheDocument();
    expect(screen.getByText('SAP')).toBeInTheDocument();
    expect(screen.getAllByText('✓')).toHaveLength(1);
    expect(screen.getByText('Enviar desafio técnico')).toBeInTheDocument();
    expect(screen.getByText(/28\/11\/2099/)).toBeInTheDocument();

    const absentCompany = screen.getByText('Empresa não informada');
    expect(absentCompany.closest('.ep-absent.hatch')).not.toBeNull();
    const absentNextAction = screen.getByText('Nenhuma próxima ação definida.');
    expect(absentNextAction.closest('.ep-absent.hatch')).not.toBeNull();
    expect(container.querySelectorAll('.ep-absent.hatch')).toHaveLength(2);
    expect(screen.getByText('Prazo a confirmar')).toBeInTheDocument();

    await user.click(screen.getByLabelText('Filtrar por etapa'));
    await user.click(screen.getByRole('option', { name: 'Proposta' }));

    expect(await screen.findByText(/Nenhuma candidatura nesta etapa\./)).toBeInTheDocument();
    expect(screen.getByText('VAZIO')).toBeInTheDocument();
  });

  it('Applications exibe estado VAZIO quando lista retornada é vazia', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (path: string) => {
        if (path === '/api/applications') {
          return jsonResponse({ dataAsOf: null, items: [] });
        }
        return jsonResponse({}, 404);
      }),
    );

    renderWithProviders(<Applications />, ['/candidaturas']);

    expect(await screen.findByText(/Nenhuma candidatura nesta etapa\./)).toBeInTheDocument();
    expect(screen.getByText('VAZIO')).toBeInTheDocument();
  });

  it('ApplicationDetail renderiza formulários padronizados e envia atualização de etapa e próximo passo', async () => {
    const user = userEvent.setup();
    const detail: ApplicationDetailDto = {
      id: 'app-1',
      opportunityId: 'opp-1',
      title: 'Desenvolvedora Backend Java',
      companyName: 'SAP',
      status: 'submitted_awaiting',
      nextAction: 'Aguardar retorno do RH',
      nextActionDueAt: '2026-11-25',
      duePrecision: 'date_only',
      history: [
        {
          id: 'hist-1',
          toStatus: 'submitted_awaiting',
          author: 'user',
          reason: 'Candidatura confirmada pelo painel.',
          at: '2026-11-18T14:00:00Z',
          evidence: null,
        },
      ],
    };
    const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
      if (path === '/api/applications/app-1') return jsonResponse(detail);
      if (path === '/api/applications/app-1/status' && init?.method === 'POST') {
        return new Response(null, { status: 204 });
      }
      if (path === '/api/applications/app-1/next-action' && init?.method === 'POST') {
        return new Response(null, { status: 204 });
      }
      return jsonResponse({}, 404);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithProviders(
      <Routes>
        <Route path="/candidaturas/:id" element={<ApplicationDetail />} />
      </Routes>,
      ['/candidaturas/app-1'],
    );

    expect(
      await screen.findByRole('heading', { name: 'Desenvolvedora Backend Java' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Histórico preservado')).toBeInTheDocument();
    expect(screen.getByText('Candidatura confirmada pelo painel.')).toBeInTheDocument();

    const saveStatusButton = screen.getByRole('button', { name: 'Salvar etapa' });
    expect(saveStatusButton).toBeDisabled();

    await user.click(screen.getByLabelText('Etapa'));
    await user.click(screen.getByRole('option', { name: 'Entrevista' }));
    await user.type(screen.getByLabelText('Motivo ou correção'), 'Convite recebido por e-mail');
    expect(saveStatusButton).toBeEnabled();
    await user.click(saveStatusButton);

    await waitFor(() => {
      expect(getPostedJson(fetchMock, '/api/applications/app-1/status')).toEqual({
        toStatus: 'interview',
        reason: 'Convite recebido por e-mail',
      });
    });
    expect(await screen.findByRole('status')).toHaveTextContent(
      '✓ Alteração registrada no histórico.',
    );

    const nextActionField = screen.getByLabelText('O que precisa fazer?');
    await user.clear(nextActionField);
    await user.type(nextActionField, 'Preparar apresentação técnica');
    fireEvent.change(screen.getByLabelText('Prazo (opcional, horário de São Paulo)'), {
      target: { value: '2026-11-30' },
    });
    await user.click(screen.getByRole('button', { name: 'Salvar próximo passo' }));

    await waitFor(() => {
      expect(getPostedJson(fetchMock, '/api/applications/app-1/next-action')).toEqual({
        text: 'Preparar apresentação técnica',
        dueAt: '2026-11-30T23:59:59-03:00',
        duePrecision: 'date_only',
      });
    });
  });
});

describe('Placeholder (/bots) e NotFound', () => {
  it('Placeholder renderiza PageHeader e SurfaceStateBlock no estado VAZIO', () => {
    const { container } = renderWithProviders(<Placeholder title="Bots" />, ['/bots']);

    expect(screen.getByRole('heading', { name: 'Bots' })).toBeInTheDocument();
    expect(screen.getByText('Coordenador e automações')).toBeInTheDocument();
    expect(screen.getByText('VAZIO')).toBeInTheDocument();
    expect(screen.getByText('Esta tela ainda está em construção.')).toBeInTheDocument();
    expect(container.querySelector('[data-surface-state="empty"]')).not.toBeNull();
    expect(screen.getByRole('link', { name: 'Ver atividades →' })).toHaveAttribute(
      'href',
      '/atividades',
    );
  });

  it('NotFound renderiza PageHeader e SurfaceStateBlock no estado VAZIO', () => {
    const { container } = renderWithProviders(<NotFound />, ['/rota-inexistente']);

    expect(screen.getByRole('heading', { name: 'Página não encontrada' })).toBeInTheDocument();
    expect(screen.getByText('Rota desconhecida')).toBeInTheDocument();
    expect(screen.getByText('VAZIO')).toBeInTheDocument();
    expect(screen.getByText('Este endereço não existe no painel.')).toBeInTheDocument();
    expect(container.querySelector('[data-surface-state="empty"]')).not.toBeNull();
    expect(screen.getByRole('link', { name: 'Voltar para Hoje →' })).toHaveAttribute('href', '/');
  });
});
