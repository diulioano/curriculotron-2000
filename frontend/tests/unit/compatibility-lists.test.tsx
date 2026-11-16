import { QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { createQueryClient } from '../../src/app/queryClient';
import { App } from '../../src/app/App';
import {
  describeCompatibility,
  describeRefreshStatus,
  parseCompatibilitySummary,
  parseHomeData,
  parseOpportunityListResponse,
  parseRefreshStatus,
} from '../../src/api/compatibility';
import { OpportunityCard } from '../../src/components/OpportunityCard';
import type { CompatibilitySummary, Opportunity } from '../../src/api/panel';

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function validRefresh(overrides: Record<string, unknown> = {}) {
  return {
    criteriaVersion: 2,
    state: 'ready' as const,
    stale: false,
    assessedCount: 5,
    remainingCount: 0,
    startedAt: null,
    finishedAt: '2026-11-15T10:00:00Z',
    errorCode: null,
    ...overrides,
  };
}

function validCompatibility(overrides: Partial<CompatibilitySummary> = {}): CompatibilitySummary {
  return {
    criteriaVersion: 2,
    evaluatorVersion: 1,
    assessedAt: '2026-11-15T10:00:00Z',
    current: true,
    roleStatus: 'matched',
    technologyStatus: 'matched',
    reasonCodes: ['role_matched', 'technology_matched'],
    ...overrides,
  };
}

function validOpportunity(id: string, overrides: Partial<Opportunity> = {}): Opportunity {
  return {
    id,
    title: `Vaga ${id}`,
    companyName: 'CI&T',
    location: 'Remoto',
    workMode: 'remote',
    contractType: 'clt',
    salary: null,
    fitReasons: ['Java e Spring Boot citados como requisitos.'],
    userDecision: 'none',
    availability: 'open',
    compatibility: validCompatibility(),
    ...overrides,
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', '/');
});

describe('parsing estrito de compatibilidade a partir de unknown', () => {
  it('valida RefreshStatus e rejeita estados ou contagens inválidas', () => {
    const parsed = parseRefreshStatus(
      validRefresh({ state: 'pending', stale: true, remainingCount: 3 }),
    );
    expect(parsed.state).toBe('pending');
    expect(parsed.remainingCount).toBe(3);

    expect(() => parseRefreshStatus(null)).toThrow();
    expect(() => parseRefreshStatus(validRefresh({ state: 'invalid_state' }))).toThrow();
    expect(() => parseRefreshStatus(validRefresh({ criteriaVersion: 0 }))).toThrow();
    expect(() => parseRefreshStatus(validRefresh({ remainingCount: -1 }))).toThrow();
    expect(() => parseRefreshStatus(validRefresh({ stale: 'yes' }))).toThrow();
  });

  it('valida CompatibilitySummary e rejeita enums desconhecidos', () => {
    const parsed = parseCompatibilitySummary(
      validCompatibility({
        criteriaVersion: null,
        evaluatorVersion: null,
        assessedAt: null,
        current: false,
        roleStatus: 'unknown',
        technologyStatus: 'unknown',
        reasonCodes: ['not_assessed'],
      }),
    );
    expect(parsed.current).toBe(false);
    expect(parsed.reasonCodes).toEqual(['not_assessed']);

    expect(() =>
      parseCompatibilitySummary({ ...validCompatibility(), roleStatus: 'maybe' }),
    ).toThrow();
    expect(() =>
      parseCompatibilitySummary({ ...validCompatibility(), technologyStatus: 'partial' }),
    ).toThrow();
    expect(() =>
      parseCompatibilitySummary({ ...validCompatibility(), reasonCodes: [123] }),
    ).toThrow();
    expect(() => parseCompatibilitySummary({ ...validCompatibility(), current: 'true' })).toThrow();
  });

  it('rejeita envelopes /api/opportunities e /api/home sem campos obrigatórios', () => {
    expect(() =>
      parseOpportunityListResponse({ items: [validOpportunity('1')], dataAsOf: null }),
    ).toThrow();
    expect(() =>
      parseHomeData({
        dataAsOf: null,
        assessment: validRefresh(),
        integrations: [],
        selectionToday: { localDate: '2026-11-15', count: 0, limit: 10, items: [] },
        applicationsWithNextActions: [],
        mailNeedingAttention: [],
        reviewQueueCount: 0,
        missedRoutines: [],
      }),
    ).toThrow();
  });

  it('aceita salaryReference com reason null e estimate null vindos do backend', () => {
    const parsed = parseOpportunityListResponse({
      dataAsOf: null,
      assessment: validRefresh(),
      items: [
        validOpportunity('sal-null', {
          salaryReference: {
            status: 'available',
            reason: null as unknown as string,
            estimate: undefined,
          },
        }),
        validOpportunity('sal-unavail', {
          salaryReference: {
            status: 'unavailable',
            reason: 'Sem referência suficiente.',
            estimate: null as unknown as undefined,
          },
        }),
      ],
    });
    expect(parsed.items[0].salaryReference?.reason).toBeUndefined();
    expect(parsed.items[1].salaryReference?.estimate).toBeUndefined();
  });

  it('rejeita salaryReference.estimate malformado e chave location ausente', () => {
    expect(() =>
      parseOpportunityListResponse({
        dataAsOf: null,
        assessment: validRefresh(),
        items: [
          validOpportunity('bad-estimate', {
            salaryReference: {
              status: 'available',
              estimate: {
                id: 'obs-1',
                criteria: { role: 'Dev' },
                scope: 'invalid_scope',
              } as unknown as Opportunity['salaryReference'] extends { estimate?: infer E }
                ? E
                : never,
            },
          }),
        ],
      }),
    ).toThrow();

    const missingLocation = { ...validOpportunity('no-loc') } as Record<string, unknown>;
    delete missingLocation.location;
    expect(() =>
      parseOpportunityListResponse({
        dataAsOf: null,
        assessment: validRefresh(),
        items: [missingLocation],
      }),
    ).toThrow();
  });

  it('traduz estados de describeCompatibility e describeRefreshStatus', () => {
    expect(describeCompatibility(validCompatibility({ roleStatus: 'mismatched' })).label).toMatch(
      /Incompatível com o perfil/i,
    );
    expect(
      describeCompatibility(
        validCompatibility({ roleStatus: 'unknown', technologyStatus: 'unknown' }),
      ).label,
    ).toMatch(/função e tecnologia não confirmadas/i);
    expect(
      describeCompatibility(validCompatibility({ technologyStatus: 'unrestricted' })).label,
    ).toMatch(/sem restrição de tecnologia/i);

    expect(
      describeRefreshStatus(validRefresh({ stale: true, state: 'running', remainingCount: 2 })),
    ).toMatch(/Atualizando compatibilidade.*2 vaga\(s\)/i);
    expect(
      describeRefreshStatus(validRefresh({ stale: true, state: 'failed', remainingCount: 3 })),
    ).toMatch(/atualização da compatibilidade falhou.*3 vaga\(s\)/i);
    expect(
      describeRefreshStatus(validRefresh({ stale: true, state: 'failed', remainingCount: 0 })),
    ).toMatch(/atualização da compatibilidade falhou\./i);
  });
});

describe('OpportunityCard compatibilidade explícita em texto', () => {
  it('exibe pendência explícita para unknown, legado não avaliado e avaliação desatualizada e oculta fitReasons sem avaliação atual', () => {
    render(
      <MemoryRouter>
        <QueryClientProvider client={createQueryClient()}>
          <OpportunityCard
            item={validOpportunity('sec-unknown', {
              fitReasons: ['Compatibilidade tecnológica não confirmada.'],
              compatibility: validCompatibility({
                roleStatus: 'matched',
                technologyStatus: 'unknown',
                reasonCodes: ['technology_unknown_partial'],
              }),
            })}
          />
          <OpportunityCard
            item={validOpportunity('legacy-unassessed', {
              fitReasons: ['Razão legada oculta'],
              compatibility: validCompatibility({
                criteriaVersion: null,
                evaluatorVersion: null,
                assessedAt: null,
                current: false,
                roleStatus: 'unknown',
                technologyStatus: 'unknown',
                reasonCodes: ['not_assessed'],
              }),
            })}
          />
          <OpportunityCard
            item={validOpportunity('stale-item', {
              fitReasons: ['Razão antiga oculta'],
              compatibility: validCompatibility({
                criteriaVersion: 1,
                current: false,
                roleStatus: 'matched',
                technologyStatus: 'matched',
                reasonCodes: ['role_matched'],
              }),
            })}
          />
          <OpportunityCard
            item={validOpportunity('omitted-compat', {
              fitReasons: ['Razão sem compatibilidade oculta'],
              compatibility: undefined,
            })}
          />
        </QueryClientProvider>
      </MemoryRouter>,
    );

    const cards = screen.getAllByRole('article');
    expect(within(cards[0]).getByText(/Tecnologia não confirmada/i)).toBeVisible();
    expect(within(cards[1]).getByText(/Ainda não avaliada/i)).toBeVisible();
    expect(within(cards[1]).queryByText(/Razão legada oculta/)).not.toBeInTheDocument();
    expect(within(cards[2]).getByText(/Avaliação desatualizada/i)).toBeVisible();
    expect(within(cards[2]).queryByText(/Razão antiga oculta/)).not.toBeInTheDocument();
    expect(
      within(cards[3]).queryByText(/Razão sem compatibilidade oculta/),
    ).not.toBeInTheDocument();
  });
});

describe('páginas Hoje e Vagas com stale, consumo histórico e falha de parser', () => {
  it('Home exibe consumidas X de 10 a partir de consumedCount e aviso de avaliação desatualizada', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (path: string) => {
        if (path === '/api/session') {
          return json(200, {
            authenticated: true,
            setupRequired: false,
            csrfToken: 'csrf',
            authMode: 'local',
          });
        }
        return json(200, {
          dataAsOf: '2026-11-15T09:00:00Z',
          assessment: validRefresh({ state: 'pending', stale: true, remainingCount: 4 }),
          integrations: [],
          selectionToday: {
            localDate: '2026-11-15',
            count: 2,
            consumedCount: 10,
            limit: 10,
            items: [validOpportunity('h-1'), validOpportunity('h-2')],
          },
          applicationsWithNextActions: [],
          mailNeedingAttention: [],
          reviewQueueCount: 0,
          missedRoutines: [],
        });
      }),
    );

    render(<App queryClient={createQueryClient()} />);

    expect(await screen.findByText(/consumidas 10 de 10/i)).toBeVisible();
    expect(screen.getByText(/4 vaga\(s\) pendente\(s\)/i)).toBeVisible();
    expect(screen.queryByText(/não completamos até 10/i)).not.toBeInTheDocument();
  });

  it('Home exibe alerta de erro quando payload falha na validação do parser', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (path: string) => {
        if (path === '/api/session') {
          return json(200, {
            authenticated: true,
            setupRequired: false,
            csrfToken: 'csrf',
            authMode: 'local',
          });
        }
        return json(200, {
          dataAsOf: null,
          assessment: validRefresh({ state: 'desconhecido' }),
          integrations: [],
          selectionToday: {
            localDate: '2026-11-15',
            count: 0,
            consumedCount: 0,
            limit: 10,
            items: [],
          },
          applicationsWithNextActions: [],
          mailNeedingAttention: [],
          reviewQueueCount: 0,
          missedRoutines: [],
        });
      }),
    );

    render(<App queryClient={createQueryClient()} />);

    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toBeVisible();
    expect(screen.queryByText(/consumidas/i)).not.toBeInTheDocument();
  });

  it('Opportunities exibe resultados parciais atuais com aviso de stale e estado vazio explícito sem mascarar erro', async () => {
    window.history.replaceState({}, '', '/vagas?list=secondary');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (path: string) => {
        if (path === '/api/session') {
          return json(200, {
            authenticated: true,
            setupRequired: false,
            csrfToken: 'csrf',
            authMode: 'local',
          });
        }
        if (path === '/api/settings/search-routine') return json(200, { enabled: false });
        if (path.includes('list=secondary')) {
          return json(200, {
            dataAsOf: '2026-11-15T09:00:00Z',
            assessment: validRefresh({ state: 'pending', stale: true, remainingCount: 3 }),
            items: [
              validOpportunity('sec-1', {
                compatibility: validCompatibility({
                  roleStatus: 'unknown',
                  technologyStatus: 'matched',
                  reasonCodes: ['role_unknown'],
                }),
              }),
            ],
          });
        }
        return json(200, {
          dataAsOf: '2026-11-15T09:00:00Z',
          assessment: validRefresh({ state: 'pending', stale: true, remainingCount: 3 }),
          items: [],
        });
      }),
    );

    render(<App queryClient={createQueryClient()} />);
    expect(await screen.findByText(/Função não confirmada/i)).toBeVisible();
    expect(screen.getByText(/3 vaga\(s\) pendente\(s\)/i)).toBeVisible();

    cleanup();
    window.history.replaceState({}, '', '/vagas?list=selection');
    render(<App queryClient={createQueryClient()} />);
    expect(await screen.findByText(/Nenhuma vaga nesta lista\./i)).toBeVisible();
  });

  it('resposta com enum inválido de compatibilidade gera alerta de erro em vez de lista vazia', async () => {
    window.history.replaceState({}, '', '/vagas?list=selection');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (path: string) => {
        if (path === '/api/session') {
          return json(200, {
            authenticated: true,
            setupRequired: false,
            csrfToken: 'csrf',
            authMode: 'local',
          });
        }
        if (path === '/api/settings/search-routine') return json(200, { enabled: false });
        if (path === '/api/home') {
          return json(200, {
            dataAsOf: null,
            assessment: validRefresh(),
            integrations: [],
            selectionToday: {
              localDate: '2026-11-15',
              count: 0,
              consumedCount: 0,
              limit: 10,
              items: [],
            },
            applicationsWithNextActions: [],
            mailNeedingAttention: [],
            reviewQueueCount: 0,
            missedRoutines: [],
          });
        }
        return json(200, {
          dataAsOf: null,
          assessment: validRefresh(),
          items: [
            {
              ...validOpportunity('bad-enum'),
              compatibility: { ...validCompatibility(), roleStatus: 'invalido' },
            },
          ],
        });
      }),
    );

    render(<App queryClient={createQueryClient()} />);
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toBeVisible();
    expect(screen.queryByText(/Nenhuma vaga nesta lista\./i)).not.toBeInTheDocument();
  });
});
