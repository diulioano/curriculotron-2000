import { describe, expect, it } from 'vitest';
import { parseHomeData, parseTaskRun, parseTaskRunList } from '../../src/api/compatibility';
import { parseOpportunityDetail } from '../../src/api/compatibility-detail';

const refresh = {
  criteriaVersion: 1,
  state: 'ready',
  stale: false,
  assessedCount: 0,
  remainingCount: 0,
  startedAt: null,
  finishedAt: null,
  errorCode: null,
};

function home(overrides: Record<string, unknown> = {}) {
  return {
    dataAsOf: null,
    assessment: refresh,
    integrations: [],
    selectionToday: { localDate: '2026-11-15', count: 0, consumedCount: 0, limit: 10, items: [] },
    applicationsWithNextActions: [],
    mailNeedingAttention: [],
    reviewQueueCount: 2,
    missedRoutines: [],
    pendingReviewItems: [],
    ...overrides,
  };
}

const pendingItem = (targetRoute: string) => ({
  id: 'r1',
  kind: 'review_item',
  kindLabel: 'Revisão',
  title: 'Salário ambíguo',
  summary: 'Confirme a faixa',
  targetRoute,
});

function detail(overrides: Record<string, unknown> = {}) {
  return {
    id: 'job-1',
    title: 'Dev',
    companyName: 'Acme',
    location: '',
    workMode: 'unknown',
    contractType: 'unknown',
    salary: null,
    fitReasons: [],
    userDecision: 'none',
    availability: 'unconfirmed',
    compatibility: {
      criteriaVersion: 1,
      evaluatorVersion: 1,
      assessedAt: '2026-11-15T12:00:00Z',
      current: true,
      roleStatus: 'unknown',
      technologyStatus: 'unknown',
      reasonCodes: [],
    },
    compatibilityEvidence: [],
    compatibilityCriteria: null,
    facts: [],
    inferred: [],
    missing: [],
    sources: [],
    seniority: { label: null, status: 'absent', reason: null },
    corrections: [],
    timeline: [],
    ...overrides,
  };
}

describe('parseHomeData.pendingReviewItems', () => {
  it('é obrigatório e validado item a item', () => {
    expect(parseHomeData(home()).pendingReviewItems).toEqual([]);
    expect(() => parseHomeData(home({ pendingReviewItems: undefined }))).toThrow(
      /pendingReviewItems/,
    );
    expect(() => parseHomeData(home({ pendingReviewItems: null }))).toThrow(/pendingReviewItems/);
    expect(
      parseHomeData(home({ pendingReviewItems: [pendingItem('/revisao')] })).pendingReviewItems,
    ).toHaveLength(1);
    expect(() =>
      parseHomeData(home({ pendingReviewItems: [{ ...pendingItem('/revisao'), kind: 'x' }] })),
    ).toThrow();
    expect(() => parseHomeData(home({ pendingReviewItems: 'x' }))).toThrow();
  });

  it('rejeita targetRoute externo ou protocol-relative', () => {
    for (const route of ['https://evil.test', '//evil.test', 'revisao', '/\\evil.test']) {
      expect(() => parseHomeData(home({ pendingReviewItems: [pendingItem(route)] }))).toThrow();
    }
  });
});

describe('parseOpportunityDetail campos da feature 005', () => {
  it('valida correctedFields em resumo, detalhe e dados iniciais e aceita contratos antigos', () => {
    const summary = {
      id: 'job-1',
      title: 'Dev',
      companyName: 'Acme',
      location: '',
      workMode: 'unknown',
      contractType: 'unknown',
      salary: null,
      fitReasons: [],
      userDecision: 'none',
      availability: 'unconfirmed',
      compatibility: {
        criteriaVersion: null,
        evaluatorVersion: null,
        assessedAt: null,
        current: false,
        roleStatus: 'unknown',
        technologyStatus: 'unknown',
        reasonCodes: [],
      },
    };
    expect(
      parseOpportunityDetail(detail({ ...summary, correctedFields: ['location'] })).correctedFields,
    ).toEqual(['location']);
    expect(
      parseHomeData(
        home({
          selectionToday: {
            localDate: '2026-11-15',
            count: 1,
            consumedCount: 0,
            limit: 10,
            items: [{ ...summary, correctedFields: ['salary_min'] }],
          },
        }),
      ).selectionToday.items[0].correctedFields,
    ).toEqual(['salary_min']);
    expect(parseOpportunityDetail(detail(summary)).correctedFields).toEqual([]);
    expect(() =>
      parseOpportunityDetail(detail({ ...summary, correctedFields: ['bogus'] })),
    ).toThrow(/correctedFields/);
    expect(() =>
      parseOpportunityDetail(detail({ ...summary, correctedFields: 'location' })),
    ).toThrow(/correctedFields/);
    expect(
      parseOpportunityDetail(
        detail({
          ...summary,
          seniority: { label: 'Pleno', status: 'corrected_title', reason: null },
        }),
      ).seniority.status,
    ).toBe('corrected_title');
  });

  it.each([
    ['seniority', /seniority/],
    ['corrections', /corrections/],
    ['timeline', /timeline/],
  ])('exige o campo %s do contrato', (field, message) => {
    expect(() => parseOpportunityDetail(detail({ [field]: undefined }))).toThrow(message);
    expect(() => parseOpportunityDetail(detail({ [field]: null }))).toThrow(message);
  });

  it('rejeita corrections e timeline que não sejam listas', () => {
    expect(() => parseOpportunityDetail(detail({ corrections: 'x' }))).toThrow(/corrections/);
    expect(() => parseOpportunityDetail(detail({ timeline: {} }))).toThrow(/timeline/);
  });

  it('valida seniority, corrections e timeline', () => {
    const parsed = parseOpportunityDetail(
      detail({
        seniority: { label: 'Pleno', status: 'fact', reason: null },
        corrections: [
          {
            id: 'c1',
            fieldName: 'title',
            previousValue: 'Dev',
            correctedValue: 'Dev Java',
            reason: null,
            authorLabel: 'você',
            correctedAt: '2026-11-15T12:00:00Z',
          },
        ],
        timeline: [
          {
            id: 't1',
            kind: 'external_opened',
            actorLabel: 'você',
            description: 'Abriu o link da vaga.',
            note: null,
            at: '2026-11-15T12:00:00Z',
          },
        ],
      }),
    );
    expect(parsed.seniority.status).toBe('fact');
    expect(parsed.corrections).toHaveLength(1);
    expect(parsed.timeline[0].kind).toBe('external_opened');
  });

  it('rejeita kind de timeline e status de nível desconhecidos', () => {
    expect(() =>
      parseOpportunityDetail(detail({ corrections: [{ id: 'c', fieldName: 'unknown' }] })),
    ).toThrow(/fieldName/);
    expect(() =>
      parseOpportunityDetail(
        detail({
          timeline: [
            { id: 't', kind: 'hacked', actorLabel: 'x', description: 'x', note: null, at: 'x' },
          ],
        }),
      ),
    ).toThrow();
    expect(() =>
      parseOpportunityDetail(detail({ seniority: { label: 'x', status: 'guess' } })),
    ).toThrow();
  });
});

describe('parseTaskRun', () => {
  it('aceita execução sem campos de progresso parcial', () => {
    const run = parseTaskRun({ id: 'run-1', state: 'running', pauseReason: null });
    expect(run).toMatchObject({ id: 'run-1', state: 'running', errorSummary: null });
    expect(run.sourcesFailed).toBeUndefined();
  });

  it('rejeita estado e listas de fontes inválidos', () => {
    expect(() => parseTaskRun({ id: 'r', state: 'done' })).toThrow();
    expect(() => parseTaskRun({ id: 'r', state: 'failed', sourcesFailed: 'lever' })).toThrow();
    expect(() => parseTaskRunList({ items: 'x' })).toThrow();
    expect(
      parseTaskRunList({
        items: [{ id: 'r', state: 'failed', sourcesConsulted: ['a'], sourcesFailed: ['b'] }],
        dataAsOf: null,
      }).items[0].sourcesFailed,
    ).toEqual(['b']);
  });
});
