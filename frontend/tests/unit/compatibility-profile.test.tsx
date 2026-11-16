import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { createQueryClient } from '../../src/app/queryClient';
import {
  parseCriteriaResponse,
  parseReassessResponse,
  type CriteriaResponse,
  type RefreshStatus,
} from '../../src/api/compatibility';
import { Criteria } from '../../src/pages/Profile';

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function makeStatus(overrides: Partial<RefreshStatus> = {}): RefreshStatus {
  return {
    criteriaVersion: 1,
    state: 'ready',
    stale: false,
    assessedCount: 5,
    remainingCount: 0,
    startedAt: null,
    finishedAt: '2026-11-16T10:00:00Z',
    errorCode: null,
    ...overrides,
  };
}

function makeCriteria(overrides: Partial<CriteriaResponse> = {}): CriteriaResponse {
  return {
    version: 1,
    targetRoles: ['Desenvolvedor Backend Pleno Java'],
    stackKeywords: ['Java', 'Spring Boot'],
    remotePreferred: true,
    hybridRegions: ['Porto Alegre'],
    cltMinMonthlyBrl: 6000,
    pjMinMonthlyBrl: 9000,
    acceptJuniorIfJustified: true,
    dailySearchTimeLocal: '07:30',
    deadlineHighlightDays: 7,
    urgentHours: 48,
    emailDigestEnabled: false,
    assessment: makeStatus({ criteriaVersion: overrides.version ?? 1 }),
    ...overrides,
  };
}

function renderCriteria() {
  const client = createQueryClient();
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/criterios']}>
        <Routes>
          <Route path="/criterios" element={<Criteria />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('parsers estritos de critérios e reavaliação', () => {
  it('valida CriteriaResponse e rejeita payloads inválidos ou incompletos', () => {
    const valid = parseCriteriaResponse(makeCriteria());
    expect(valid.version).toBe(1);
    expect(valid.assessment.state).toBe('ready');

    expect(() => parseCriteriaResponse(null)).toThrow();
    expect(() => parseCriteriaResponse({ ...makeCriteria(), version: 0 })).toThrow();
    expect(() => parseCriteriaResponse({ ...makeCriteria(), cltMinMonthlyBrl: 0 })).toThrow();
    expect(() => parseCriteriaResponse({ ...makeCriteria(), pjMinMonthlyBrl: -10 })).toThrow();
    expect(() =>
      parseCriteriaResponse({ ...makeCriteria(), dailySearchTimeLocal: '25:99' }),
    ).toThrow();
    expect(() =>
      parseCriteriaResponse({ ...makeCriteria(), assessment: { state: 'unknown' } }),
    ).toThrow();
  });

  it('valida ReassessResponse e rejeita estados ou contagens inválidas', () => {
    const parsed = parseReassessResponse({
      criteriaVersion: 2,
      state: 'ready',
      assessedCount: 12,
      remainingCount: 0,
      finishedAt: '2026-11-16T11:00:00Z',
    });
    expect(parsed.criteriaVersion).toBe(2);
    expect(parsed.assessedCount).toBe(12);

    expect(() =>
      parseReassessResponse({
        criteriaVersion: 0,
        state: 'ready',
        assessedCount: 0,
        remainingCount: 0,
        finishedAt: null,
      }),
    ).toThrow();
    expect(() =>
      parseReassessResponse({
        criteriaVersion: 1,
        state: 'invalid',
        assessedCount: 0,
        remainingCount: 0,
        finishedAt: null,
      }),
    ).toThrow();
  });
});

describe('Criteria UI integrada com versionamento, status e reavaliação', () => {
  it('salva critérios com versionamento, mostra pending e conclui Atualizar avaliação', async () => {
    let serverCriteria = makeCriteria();
    const putBodies: Record<string, unknown>[] = [];
    let postHeaders: HeadersInit | undefined;
    let postBody: unknown = null;

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        const method = init?.method ?? 'GET';
        if (path === '/api/criteria' && method === 'GET') {
          return json(200, serverCriteria);
        }
        if (path === '/api/criteria' && method === 'PUT') {
          putBodies.push(JSON.parse(String(init?.body)) as Record<string, unknown>);
          serverCriteria = makeCriteria({
            version: 2,
            stackKeywords: ['Python', 'Django'],
            assessment: makeStatus({
              criteriaVersion: 2,
              state: 'pending',
              stale: true,
              assessedCount: 0,
              remainingCount: 4,
              finishedAt: null,
            }),
          });
          return json(200, serverCriteria);
        }
        if (path === '/api/opportunities/compatibility-status' && method === 'GET') {
          return json(200, serverCriteria.assessment);
        }
        if (path === '/api/opportunities/reassess' && method === 'POST') {
          postHeaders = init?.headers;
          postBody = JSON.parse(String(init?.body));
          serverCriteria = {
            ...serverCriteria,
            assessment: makeStatus({
              criteriaVersion: 2,
              state: 'ready',
              stale: false,
              assessedCount: 4,
              remainingCount: 0,
              finishedAt: '2026-11-16T11:05:00Z',
            }),
          };
          return json(200, {
            criteriaVersion: 2,
            state: 'ready',
            assessedCount: 4,
            remainingCount: 0,
            finishedAt: '2026-11-16T11:05:00Z',
          });
        }
        return json(404, {});
      }),
    );

    renderCriteria();

    const stackInput = await screen.findByLabelText(/Tecnologias/i);
    expect(stackInput).toHaveValue('Java\nSpring Boot');
    expect(screen.getByText(/Compatibilidade atualizada para o critério v1/i)).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Atualizar avaliação' })).not.toBeInTheDocument();

    await userEvent.clear(stackInput);
    await userEvent.type(stackInput, 'Python\nDjango');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar critérios' }));

    expect(await screen.findByText(/Critérios salvos/i)).toBeVisible();
    expect(putBodies).toHaveLength(1);
    expect(putBodies[0].version).toBe(1);
    expect(putBodies[0]).not.toHaveProperty('assessment');

    expect(screen.getByText(/pendente.*v2.*4 vaga\(s\)/i)).toBeVisible();
    const reassessBtn = screen.getByRole('button', { name: 'Atualizar avaliação' });
    expect(reassessBtn).toBeEnabled();

    await userEvent.click(reassessBtn);

    expect(await screen.findByText(/Compatibilidade atualizada para o critério v2/i)).toBeVisible();
    expect(postBody).toEqual({ criteriaVersion: 2 });
    const headersRecord = new Headers(postHeaders);
    expect(headersRecord.get('Idempotency-Key')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Atualizar avaliação' })).not.toBeInTheDocument();
  });

  it('preserva formulário em 409 criteria_version_conflict e permite recarregar critérios atuais', async () => {
    const serverCriteria = makeCriteria({
      version: 2,
      stackKeywords: ['Go', 'Kubernetes'],
      assessment: makeStatus({
        criteriaVersion: 2,
        state: 'pending',
        stale: true,
        remainingCount: 2,
      }),
    });
    let getCount = 0;

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        const method = init?.method ?? 'GET';
        if (path === '/api/criteria' && method === 'GET') {
          getCount++;
          if (getCount === 1) {
            return json(200, makeCriteria({ version: 1, stackKeywords: ['Java'] }));
          }
          return json(200, serverCriteria);
        }
        if (path === '/api/opportunities/compatibility-status' && method === 'GET') {
          return json(200, makeStatus({ criteriaVersion: 1, state: 'ready', stale: false }));
        }
        if (path === '/api/criteria' && method === 'PUT') {
          return json(409, {
            error: {
              code: 'criteria_version_conflict',
              message: 'Os critérios foram alterados. Recarregue antes de salvar.',
            },
          });
        }
        return json(404, {});
      }),
    );

    renderCriteria();

    const stackInput = await screen.findByLabelText(/Tecnologias/i);
    await userEvent.clear(stackInput);
    await userEvent.type(stackInput, 'Python\nFastAPI');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar critérios' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/alterados|conflito/i);
    // Formulário preservado com o que o usuário digitou!
    expect(screen.getByLabelText(/Tecnologias/i)).toHaveValue('Python\nFastAPI');

    const reloadBtn = screen.getByRole('button', { name: /Recarregar critérios/i });
    await userEvent.click(reloadBtn);

    await waitFor(() => {
      expect(screen.getByLabelText(/Tecnologias/i)).toHaveValue('Go\nKubernetes');
    });
    expect(screen.queryByRole('button', { name: /Recarregar critérios/i })).not.toBeInTheDocument();
  });

  it('trata 409 reassessment_running consultando apenas GET compatibility-status via polling sem reenviar POST', async () => {
    const serverCriteria = makeCriteria({
      version: 2,
      assessment: makeStatus({
        criteriaVersion: 2,
        state: 'pending',
        stale: true,
        remainingCount: 3,
      }),
    });
    let statusPolls = 0;
    let postCalls = 0;

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        const method = init?.method ?? 'GET';
        if (path === '/api/criteria' && method === 'GET') return json(200, serverCriteria);
        if (path === '/api/opportunities/reassess' && method === 'POST') {
          postCalls++;
          return json(409, {
            error: {
              code: 'reassessment_running',
              message: 'Já existe uma reavaliação em andamento.',
            },
          });
        }
        if (path === '/api/opportunities/compatibility-status' && method === 'GET') {
          statusPolls++;
          if (statusPolls <= 2) {
            return json(
              200,
              makeStatus({
                criteriaVersion: 2,
                state: 'running',
                stale: true,
                assessedCount: 1,
                remainingCount: 2,
                startedAt: '2026-11-16T11:00:00Z',
                finishedAt: null,
              }),
            );
          }
          return json(
            200,
            makeStatus({
              criteriaVersion: 2,
              state: 'ready',
              stale: false,
              assessedCount: 3,
              remainingCount: 0,
              startedAt: '2026-11-16T11:00:00Z',
              finishedAt: '2026-11-16T11:00:02Z',
            }),
          );
        }
        return json(404, {});
      }),
    );

    renderCriteria();

    const btn = await screen.findByRole('button', { name: 'Atualizar avaliação' });
    await userEvent.click(btn);

    expect(await screen.findByText(/Atualizando avaliação de compatibilidade/i)).toBeVisible();
    expect(postCalls).toBe(1);

    await waitFor(
      () => {
        expect(screen.getByText(/Compatibilidade atualizada para o critério v2/i)).toBeVisible();
      },
      { timeout: 5000 },
    );
    expect(postCalls).toBe(1);
    expect(statusPolls).toBeGreaterThanOrEqual(3);
  });

  it('em timeout de POST /reassess consulta GET compatibility-status antes de permitir retry', async () => {
    const serverCriteria = makeCriteria({
      version: 2,
      assessment: makeStatus({
        criteriaVersion: 2,
        state: 'pending',
        stale: true,
        remainingCount: 2,
      }),
    });
    let currentStatus = makeStatus({
      criteriaVersion: 2,
      state: 'pending',
      stale: true,
      remainingCount: 2,
      finishedAt: null,
    });
    let postCount = 0;
    let statusChecksAfterPost = 0;

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        const method = init?.method ?? 'GET';
        if (path === '/api/criteria' && method === 'GET') {
          return json(200, { ...serverCriteria, assessment: currentStatus });
        }
        if (path === '/api/opportunities/reassess' && method === 'POST') {
          postCount++;
          if (postCount === 1) {
            throw new DOMException('The operation timed out.', 'TimeoutError');
          }
          currentStatus = makeStatus({
            criteriaVersion: 2,
            state: 'ready',
            stale: false,
            assessedCount: 2,
            remainingCount: 0,
          });
          return json(200, {
            criteriaVersion: 2,
            state: 'ready',
            assessedCount: 2,
            remainingCount: 0,
            finishedAt: '2026-11-16T11:10:00Z',
          });
        }
        if (path === '/api/opportunities/compatibility-status' && method === 'GET') {
          if (postCount > 0) statusChecksAfterPost++;
          return json(200, currentStatus);
        }
        return json(404, {});
      }),
    );

    renderCriteria();

    const btn = await screen.findByRole('button', { name: 'Atualizar avaliação' });
    await userEvent.click(btn);

    expect(await screen.findByRole('alert')).toHaveTextContent(/Status consultado/i);
    expect(statusChecksAfterPost).toBeGreaterThanOrEqual(1);

    const retryBtn = screen.getByRole('button', { name: 'Atualizar avaliação' });
    expect(retryBtn).toBeEnabled();
    await userEvent.click(retryBtn);

    expect(await screen.findByText(/Compatibilidade atualizada para o critério v2/i)).toBeVisible();
    expect(postCount).toBe(2);
  });

  it('identifica interrupção e falha recuperável permitindo retomar manualmente', async () => {
    let status: RefreshStatus = makeStatus({
      criteriaVersion: 2,
      state: 'failed',
      stale: true,
      assessedCount: 1,
      remainingCount: 2,
      errorCode: 'interrupted',
      finishedAt: '2026-11-16T10:05:00Z',
    });
    const criteria = makeCriteria({ version: 2, assessment: status });
    let postAttempt = 0;

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        const method = init?.method ?? 'GET';
        if (path === '/api/criteria' && method === 'GET') {
          return json(200, { ...criteria, assessment: status });
        }
        if (path === '/api/opportunities/compatibility-status' && method === 'GET') {
          return json(200, status);
        }
        if (path === '/api/opportunities/reassess' && method === 'POST') {
          postAttempt++;
          if (postAttempt === 1) {
            status = makeStatus({
              criteriaVersion: 2,
              state: 'failed',
              stale: true,
              assessedCount: 1,
              remainingCount: 2,
              errorCode: 'assessment_failed',
            });
            return json(500, {
              error: {
                code: 'reassessment_failed',
                message: 'Falha ao reavaliar vagas. Consulte o status e tente novamente.',
              },
            });
          }
          status = makeStatus({
            criteriaVersion: 2,
            state: 'ready',
            stale: false,
            assessedCount: 3,
            remainingCount: 0,
          });
          return json(200, {
            criteriaVersion: 2,
            state: 'ready',
            assessedCount: 3,
            remainingCount: 0,
            finishedAt: '2026-11-16T11:12:00Z',
          });
        }
        return json(404, {});
      }),
    );

    renderCriteria();

    expect(await screen.findByText(/interrompida.*2 vaga\(s\)/i)).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: 'Atualizar avaliação' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/Falha ao reavaliar vagas/i);
    expect(screen.getByText(/atualização da compatibilidade falhou.*2 vaga\(s\)/i)).toBeVisible();

    await userEvent.click(screen.getByRole('button', { name: 'Atualizar avaliação' }));
    expect(await screen.findByText(/Compatibilidade atualizada para o critério v2/i)).toBeVisible();
  });

  it('trata 409 evidence_changed com mensagem específica e permite nova tentativa', async () => {
    let status: RefreshStatus = makeStatus({
      criteriaVersion: 2,
      state: 'pending',
      stale: true,
      remainingCount: 2,
    });
    const criteria = makeCriteria({ version: 2, assessment: status });
    let attempts = 0;

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        const method = init?.method ?? 'GET';
        if (path === '/api/criteria' && method === 'GET') {
          return json(200, { ...criteria, assessment: status });
        }
        if (path === '/api/opportunities/compatibility-status' && method === 'GET') {
          return json(200, status);
        }
        if (path === '/api/opportunities/reassess' && method === 'POST') {
          attempts++;
          if (attempts === 1) {
            status = makeStatus({
              criteriaVersion: 2,
              state: 'failed',
              stale: true,
              remainingCount: 1,
              errorCode: 'evidence_changed',
            });
            return json(409, {
              error: {
                code: 'evidence_changed',
                message: 'As vagas mudaram durante a reavaliação. Tente novamente.',
              },
            });
          }
          status = makeStatus({
            criteriaVersion: 2,
            state: 'ready',
            stale: false,
            assessedCount: 2,
            remainingCount: 0,
          });
          return json(200, {
            criteriaVersion: 2,
            state: 'ready',
            assessedCount: 2,
            remainingCount: 0,
            finishedAt: '2026-11-16T11:20:00Z',
          });
        }
        return json(404, {});
      }),
    );

    renderCriteria();

    await userEvent.click(await screen.findByRole('button', { name: 'Atualizar avaliação' }));
    expect(
      await screen.findByText(/Vagas foram alteradas durante a última tentativa/i),
    ).toBeVisible();
    expect(screen.getByRole('alert')).toHaveTextContent(/vagas mudaram/i);

    await userEvent.click(screen.getByRole('button', { name: 'Atualizar avaliação' }));
    expect(await screen.findByText(/Compatibilidade atualizada para o critério v2/i)).toBeVisible();
  });

  it('oculta Atualizar avaliação quando timeout + GET status falham e permite recuperar com Consultar status', async () => {
    let status: RefreshStatus = makeStatus({
      criteriaVersion: 2,
      state: 'pending',
      stale: true,
      remainingCount: 2,
    });
    const criteria = makeCriteria({ version: 2, assessment: status });
    let statusCalls = 0;

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        const method = init?.method ?? 'GET';
        if (path === '/api/criteria' && method === 'GET') {
          return json(200, { ...criteria, assessment: status });
        }
        if (path === '/api/opportunities/reassess' && method === 'POST') {
          throw new DOMException('Timed out', 'TimeoutError');
        }
        if (path === '/api/opportunities/compatibility-status' && method === 'GET') {
          statusCalls++;
          if (statusCalls === 1) {
            return json(503, { error: { code: 'unavailable', message: 'Indisponível' } });
          }
          status = makeStatus({
            criteriaVersion: 2,
            state: 'ready',
            stale: false,
            assessedCount: 2,
            remainingCount: 0,
          });
          return json(200, status);
        }
        return json(404, {});
      }),
    );

    renderCriteria();

    await userEvent.click(await screen.findByRole('button', { name: 'Atualizar avaliação' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Não foi possível confirmar o status da atualização/i,
    );
    expect(screen.queryByRole('button', { name: 'Atualizar avaliação' })).not.toBeInTheDocument();

    const checkBtn = screen.getByRole('button', { name: 'Consultar status' });
    await userEvent.click(checkBtn);

    expect(await screen.findByText(/Compatibilidade atualizada para o critério v2/i)).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Consultar status' })).not.toBeInTheDocument();
  });

  it('trata 409 criteria_version_conflict durante POST /reassess atualizando para a versão vigente', async () => {
    const criteriaV1 = makeCriteria({
      version: 1,
      assessment: makeStatus({
        criteriaVersion: 1,
        state: 'pending',
        stale: true,
        remainingCount: 2,
      }),
    });
    let currentStatus = makeStatus({
      criteriaVersion: 2,
      state: 'pending',
      stale: true,
      remainingCount: 3,
    });
    let conflictObserved = false;
    const postedVersions: number[] = [];

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        const method = init?.method ?? 'GET';
        if (path === '/api/criteria' && method === 'GET') {
          return json(
            200,
            conflictObserved ? makeCriteria({ version: 2, assessment: currentStatus }) : criteriaV1,
          );
        }
        if (path === '/api/opportunities/compatibility-status' && method === 'GET') {
          return json(200, currentStatus);
        }
        if (path === '/api/opportunities/reassess' && method === 'POST') {
          const body = JSON.parse(String(init?.body)) as { criteriaVersion: number };
          postedVersions.push(body.criteriaVersion);
          if (body.criteriaVersion === 1) {
            conflictObserved = true;
            return json(409, {
              error: {
                code: 'criteria_version_conflict',
                message: 'A versão dos critérios mudou antes da reavaliação.',
              },
            });
          }
          currentStatus = makeStatus({
            criteriaVersion: 2,
            state: 'ready',
            stale: false,
            assessedCount: 3,
            remainingCount: 0,
            finishedAt: '2026-11-16T11:30:00Z',
          });
          return json(200, {
            criteriaVersion: 2,
            state: 'ready',
            assessedCount: 3,
            remainingCount: 0,
            finishedAt: '2026-11-16T11:30:00Z',
          });
        }
        return json(404, {});
      }),
    );

    renderCriteria();

    await userEvent.click(await screen.findByRole('button', { name: 'Atualizar avaliação' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/versão dos critérios mudou/i);
    expect(screen.getByRole('status')).toHaveTextContent(/critério v2/i);

    await userEvent.click(screen.getByRole('button', { name: 'Atualizar avaliação' }));
    expect(await screen.findByText(/Compatibilidade atualizada para o critério v2/i)).toBeVisible();
    expect(postedVersions).toEqual([1, 2]);
  });

  it('recupera de falha durante polling em estado running via Consultar status', async () => {
    const criteria = makeCriteria({
      version: 2,
      assessment: makeStatus({
        criteriaVersion: 2,
        state: 'running',
        stale: true,
        remainingCount: 2,
      }),
    });
    let pollCalls = 0;

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        const method = init?.method ?? 'GET';
        if (path === '/api/criteria' && method === 'GET') return json(200, criteria);
        if (path === '/api/opportunities/compatibility-status' && method === 'GET') {
          pollCalls++;
          if (pollCalls === 1) {
            return json(500, { error: { code: 'internal_error', message: 'Erro temporário' } });
          }
          return json(
            200,
            makeStatus({
              criteriaVersion: 2,
              state: 'ready',
              stale: false,
              assessedCount: 2,
              remainingCount: 0,
            }),
          );
        }
        return json(404, {});
      }),
    );

    renderCriteria();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Não foi possível confirmar o andamento da atualização/i,
    );
    const checkBtn = screen.getByRole('button', { name: 'Consultar status' });
    await userEvent.click(checkBtn);

    expect(await screen.findByText(/Compatibilidade atualizada para o critério v2/i)).toBeVisible();
    expect(pollCalls).toBe(2);
  });

  it('sincroniza compatibility-status ao remontar /criterios com dados frescos e trata erro ao recarregar conflito', async () => {
    let criteriaLoads = 0;
    let reloadFailures = 0;

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        const method = init?.method ?? 'GET';
        if (path === '/api/criteria' && method === 'GET') {
          criteriaLoads++;
          if (criteriaLoads === 1) {
            return json(
              200,
              makeCriteria({
                version: 1,
                assessment: makeStatus({ criteriaVersion: 1, state: 'ready', stale: false }),
              }),
            );
          }
          if (criteriaLoads === 2) {
            // Remontagem após nova vaga pendente
            return json(
              200,
              makeCriteria({
                version: 1,
                assessment: makeStatus({
                  criteriaVersion: 1,
                  state: 'pending',
                  stale: true,
                  remainingCount: 3,
                }),
              }),
            );
          }
          reloadFailures++;
          return json(500, {
            error: { code: 'internal_error', message: 'Falha ao ler critérios.' },
          });
        }
        if (path === '/api/criteria' && method === 'PUT') {
          return json(409, {
            error: { code: 'criteria_version_conflict', message: 'Conflito de versão.' },
          });
        }
        return json(404, {});
      }),
    );

    const client = createQueryClient();
    const { unmount } = render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/criterios']}>
          <Routes>
            <Route path="/criterios" element={<Criteria />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(await screen.findByText(/Compatibilidade atualizada para o critério v1/i)).toBeVisible();
    unmount();

    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/criterios']}>
          <Routes>
            <Route path="/criterios" element={<Criteria />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(
      await screen.findByText(/Avaliação pendente para o critério v1 \(3 vaga\(s\)/i),
    ).toBeVisible();
    expect(screen.getByRole('button', { name: 'Atualizar avaliação' })).toBeVisible();

    const stackInput = screen.getByLabelText(/Tecnologias/i);
    await userEvent.clear(stackInput);
    await userEvent.type(stackInput, 'Rust');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar critérios' }));

    expect(await screen.findByRole('button', { name: 'Recarregar critérios' })).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: 'Recarregar critérios' }));

    await waitFor(() => expect(reloadFailures).toBe(1));
    expect(screen.getByText(/Falha ao ler critérios\./i)).toBeVisible();
    expect(screen.getByLabelText(/Tecnologias/i)).toHaveValue('Rust');
  });
});
