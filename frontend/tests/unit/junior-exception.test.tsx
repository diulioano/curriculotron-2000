import { cleanup, render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { parseJuniorException, parseOpportunityDetail } from '../../src/api/compatibility-detail';
import type { CareerProfile } from '../../src/api/profile';
import { JuniorException } from '../../src/components/JuniorException';

const PROFILE: CareerProfile = {
  role: 'Desenvolvedor Backend Java',
  primaryTechnology: 'Java',
  secondaryTechnologies: [],
  flexibleTechnologies: [],
  currentSeniority: 'pleno',
  targetLevels: ['pleno'],
  priorities: [],
  juniorFloorCltMonthlyBrl: 7000,
  juniorFloorPjMonthlyBrl: null,
  version: 1,
  origin: 'manual',
  provenance: {},
  updatedAt: '2026-11-21T12:00:00Z',
};

const SAP_REASON = 'SAP oferece carga de trabalho menor (RB-004).';

function decision(overrides: Record<string, unknown> = {}, salary: Record<string, unknown> = {}) {
  return {
    status: 'excluded',
    workFit: true,
    salary: {
      status: 'below_floor',
      source: 'announced',
      contract: 'clt',
      unit: 'monthly',
      floor: 7000,
      announcedMin: 5000,
      announcedMax: 6000,
      ...salary,
    },
    justification: { status: 'accepted', text: SAP_REASON },
    ...overrides,
  };
}

function detail(juniorException: unknown, overrides: Record<string, unknown> = {}) {
  return parseOpportunityDetail({
    id: 'job-1',
    title: 'Desenvolvedor Java Júnior',
    companyName: 'SAP',
    location: 'Remoto',
    workMode: 'remote',
    contractType: 'clt',
    salary: { min: 5000, max: 6000, unit: 'monthly' },
    // Redação livre: o componente não depende dos motivos quando há decisão tipada.
    fitReasons: ['Qualquer texto do ranking.'],
    userDecision: 'none',
    availability: 'open',
    compatibility: {
      criteriaVersion: 2,
      evaluatorVersion: 1,
      assessedAt: '2026-11-21T12:00:00Z',
      current: true,
      roleStatus: 'matched',
      technologyStatus: 'matched',
      reasonCodes: [],
    },
    compatibilityCriteria: null,
    compatibilityEvidence: [],
    facts: [],
    inferred: [],
    missing: [],
    sources: [],
    seniority: { label: 'Júnior', status: 'fact', reason: null },
    corrections: [],
    timeline: [],
    preparation: { profileVersion: 1, required: [], optional: [], mentioned: [] },
    juniorException,
    ...overrides,
  });
}

function show(
  juniorException: unknown,
  overrides: Record<string, unknown> = {},
  profile = PROFILE,
) {
  render(<JuniorException item={detail(juniorException, overrides)} profile={profile} />);
  return screen.queryByRole('region', { name: 'Exceção Júnior' });
}

function check(section: HTMLElement, label: string) {
  return within(section)
    .getAllByRole('listitem')
    .find((item) => item.textContent?.includes(label));
}

describe('parser da decisão da exceção Júnior', () => {
  it('aceita respostas antigas sem o campo e decisões sem dados', () => {
    expect(parseJuniorException(undefined)).toBeNull();
    expect(parseJuniorException(null)).toBeNull();
    expect(parseJuniorException({ status: 'unknown' })).toEqual({ status: 'unknown' });
    expect(parseJuniorException({ status: 'not_applicable' })).toEqual({
      status: 'not_applicable',
    });
  });

  it('lê a decisão completa, inclusive valores monetários como texto', () => {
    const parsed = parseJuniorException(decision({}, { floor: '7000.00', announcedMax: null }));
    expect(parsed).toMatchObject({
      status: 'excluded',
      workFit: true,
      salary: { status: 'below_floor', floor: 7000, announcedMin: 5000, announcedMax: null },
      justification: { status: 'accepted', text: SAP_REASON },
    });
  });

  it.each([
    [{ status: 'talvez' }],
    [decision({ workFit: 'sim' })],
    [decision({}, { status: 'desconhecido' })],
    [decision({}, { source: 'glassdoor' })],
    [decision({}, { floor: 'muito' })],
    [decision({ justification: { status: 'ok', text: null } })],
    [decision({ salary: undefined })],
  ])('rejeita formato inválido %#', (raw) => {
    expect(() => parseJuniorException(raw)).toThrow();
  });
});

describe('exceção Júnior a partir da decisão tipada', () => {
  it('preserva a justificativa da empresa quando só o salário falha', () => {
    const section = show(decision())!;
    expect(section).toHaveTextContent('Não atende a todas as condições');
    expect(check(section, 'Justificativa da empresa')).toHaveTextContent('(atendida)');
    expect(check(section, 'Justificativa da empresa')).toHaveTextContent(SAP_REASON);
    expect(check(section, 'Remuneração confirmada acima do piso')).toHaveTextContent(
      /\(não atendida\).*abaixo do piso Júnior CLT de R\$\s?7\.000,00/,
    );
  });

  it('confirma as três condições', () => {
    const section = show(
      decision({ status: 'qualified' }, { status: 'confirmed', announcedMin: 8000 }),
    )!;
    expect(section).toHaveTextContent('Atende às três condições da exceção.');
    expect(section).toHaveTextContent(
      /Mínimo anunciado de R\$\s?8\.000,00 atinge o piso Júnior CLT de R\$\s?7\.000,00/,
    );
  });

  it.each([
    [{ status: 'range_crosses_floor' }, /cruza o piso Júnior CLT/],
    [
      { status: 'not_announced', source: 'none', announcedMin: null, announcedMax: null },
      /Salário mensal não anunciado; estimativas não confirmam/,
    ],
    [
      { status: 'not_announced', source: 'estimate', announcedMin: null, announcedMax: null },
      /Só há estimativa salarial.*estimativas não confirmam/,
    ],
    [{ status: 'floor_missing', contract: 'pj', floor: null }, /Você não definiu piso Júnior PJ\./],
    [
      { status: 'contract_unknown', contract: null, floor: null },
      /Regime da vaga .* não informado/,
    ],
  ])('explica salário não confirmado %#', (salary, text) => {
    const section = show(decision({}, salary))!;
    expect(check(section, 'Remuneração confirmada acima do piso')).toHaveTextContent(text);
  });

  it('usa o fit de trabalho do serviço, não o status exibido da compatibilidade', () => {
    const section = show(decision({ workFit: false }))!;
    expect(check(section, 'Trabalho aderente')).toHaveTextContent(
      '(não atendida): Cargo ou tecnologia não confirmados',
    );
  });

  it('diferencia empresa sem justificativa de critérios que não aceitam Júnior', () => {
    let section = show(decision({ justification: { status: 'missing', text: null } }))!;
    expect(section).toHaveTextContent('Ser prioritária, sozinha, não qualifica.');
    cleanup();
    section = show(
      decision({ justification: { status: 'not_accepted_by_criteria', text: SAP_REASON } }),
    )!;
    expect(check(section, 'Justificativa da empresa')).toHaveTextContent(
      /não aceitam vagas Júnior.*RB-004/,
    );
  });

  it('não confirma veredito com avaliação desatualizada', () => {
    const section = show(decision({ status: 'qualified' }, { status: 'confirmed' }), {
      compatibility: {
        criteriaVersion: 2,
        evaluatorVersion: 1,
        assessedAt: '2026-11-21T12:00:00Z',
        current: false,
        roleStatus: 'matched',
        technologyStatus: 'matched',
        reasonCodes: [],
      },
    })!;
    expect(section).toHaveTextContent('não confirmou uma decisão');
  });

  it('decisão desconhecida sem dados mostra o aviso sem condições', () => {
    const section = show({ status: 'unknown' })!;
    expect(section).toHaveTextContent('Sem dados suficientes para decidir a exceção Júnior.');
    expect(within(section).queryAllByRole('listitem')).toHaveLength(0);
  });

  it('segue o nível usado pelo ranking, não o rótulo exibido', () => {
    expect(show({ status: 'not_applicable' })).toBeNull();
  });

  it('mostra a decisão do ranking mesmo quando o rótulo exibido não é Júnior', () => {
    const section = show(decision(), {
      seniority: { label: null, status: 'absent', reason: null },
    });
    expect(section).not.toBeNull();
  });
});
