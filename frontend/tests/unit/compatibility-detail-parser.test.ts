import { describe, expect, it } from 'vitest';
import {
  MENTION_KINDS,
  EVIDENCE_FIELDS,
  parseCompatibilityCriteria,
  parseCompatibilityEvidenceItem,
  parseOpportunityDetail,
} from '../../src/api/compatibility-detail';

function validSummaryPayload(overrides: Record<string, unknown> = {}) {
  return {
    id: 'job-1',
    title: 'Desenvolvedor Backend Pleno Java',
    companyName: 'CI&T',
    location: 'Remoto',
    workMode: 'remote',
    contractType: 'clt',
    salary: null,
    fitReasons: ['Java e Spring citados na vaga e no perfil.'],
    userDecision: 'none',
    availability: 'open',
    compatibility: {
      criteriaVersion: 1,
      evaluatorVersion: 1,
      assessedAt: '2026-11-15T12:00:00Z',
      current: true,
      roleStatus: 'matched',
      technologyStatus: 'matched',
      reasonCodes: ['role_matched', 'technology_matched'],
    },
    ...overrides,
  };
}

function validEvidenceItem(overrides: Record<string, unknown> = {}) {
  return {
    source: 'lever:ciandt',
    sourceUrl: 'https://jobs.lever.co/ciandt/1',
    fetchedAt: '2026-11-15T12:00:00Z',
    field: 'description',
    section: 'Requisitos obrigatórios',
    quote: 'Experiência com Java 17 e Spring Boot.',
    matchedCriterion: 'Java 17',
    observedTerm: 'Java 17',
    mentionKind: 'required',
    observedVersion: '17',
    configuredVersion: '17',
    ...overrides,
  };
}

function validDetailPayload(overrides: Record<string, unknown> = {}) {
  return {
    ...validSummaryPayload(),
    compatibilityEvidence: [validEvidenceItem()],
    compatibilityCriteria: {
      targetRoles: ['Desenvolvedor Backend Pleno Java'],
      stackKeywords: ['Java 17', 'Spring Boot'],
    },
    facts: ['Java 17 obrigatório'],
    inferred: [],
    missing: ['Salário a confirmar'],
    sources: [
      {
        source: 'lever:ciandt',
        url: 'https://jobs.lever.co/ciandt/1',
        fetchedAt: '2026-11-15T12:00:00Z',
      },
    ],
    ...overrides,
  };
}

describe('parseCompatibilityEvidenceItem', () => {
  it('valida todos os campos obrigatórios e nuláveis do contrato', () => {
    expect(EVIDENCE_FIELDS).toEqual(['title', 'description']);
    expect(MENTION_KINDS).toEqual([
      'required',
      'optional',
      'usage',
      'negated',
      'incidental',
      'uncertain',
    ]);

    const full = parseCompatibilityEvidenceItem(validEvidenceItem());
    expect(full.field).toBe('description');
    expect(full.mentionKind).toBe('required');
    expect(full.observedVersion).toBe('17');

    const titleOnly = parseCompatibilityEvidenceItem(
      validEvidenceItem({
        source: 'manual',
        sourceUrl: null,
        fetchedAt: null,
        field: 'title',
        section: null,
        matchedCriterion: null,
        observedTerm: null,
        mentionKind: 'usage',
        observedVersion: null,
        configuredVersion: null,
      }),
    );
    expect(titleOnly.sourceUrl).toBeNull();
    expect(titleOnly.field).toBe('title');
    expect(titleOnly.mentionKind).toBe('usage');

    for (const kind of ['optional', 'negated', 'incidental', 'uncertain'] as const) {
      expect(
        parseCompatibilityEvidenceItem(validEvidenceItem({ mentionKind: kind })).mentionKind,
      ).toBe(kind);
    }
  });

  it('rejeita enum inválido, chave ausente ou tipo incompatível sem mascarar erro', () => {
    expect(() => parseCompatibilityEvidenceItem(null)).toThrow();
    expect(() => parseCompatibilityEvidenceItem(validEvidenceItem({ field: 'summary' }))).toThrow();
    expect(() =>
      parseCompatibilityEvidenceItem(validEvidenceItem({ mentionKind: 'preferred' })),
    ).toThrow();
    expect(() => parseCompatibilityEvidenceItem(validEvidenceItem({ quote: 123 }))).toThrow();
    expect(() => parseCompatibilityEvidenceItem(validEvidenceItem({ sourceUrl: 123 }))).toThrow();
    expect(() => parseCompatibilityEvidenceItem(validEvidenceItem({ source: '' }))).toThrow();

    const missingNullable = validEvidenceItem() as Record<string, unknown>;
    delete missingNullable.section;
    expect(() => parseCompatibilityEvidenceItem(missingNullable)).toThrow();
  });
});

describe('parseCompatibilityCriteria', () => {
  it('aceita null para vaga não avaliada e rejeita estruturas inválidas', () => {
    expect(parseCompatibilityCriteria(null)).toBeNull();
    expect(
      parseCompatibilityCriteria({
        targetRoles: ['Backend Java'],
        stackKeywords: ['Java', 'Spring Boot'],
      }),
    ).toEqual({
      targetRoles: ['Backend Java'],
      stackKeywords: ['Java', 'Spring Boot'],
    });

    expect(() => parseCompatibilityCriteria(undefined)).toThrow();
    expect(() => parseCompatibilityCriteria('invalid')).toThrow();
    expect(() => parseCompatibilityCriteria({ targetRoles: [42], stackKeywords: [] })).toThrow();
    expect(() => parseCompatibilityCriteria({ targetRoles: [] })).toThrow();
  });
});

describe('parseOpportunityDetail', () => {
  it('valida detalhe completo, detalhe stale e legado sem avaliação', () => {
    const full = parseOpportunityDetail(validDetailPayload());
    expect(full.compatibility.current).toBe(true);
    expect(full.compatibilityEvidence).toHaveLength(1);
    expect(full.compatibilityCriteria?.stackKeywords).toContain('Java 17');
    expect(full.facts).toEqual(['Java 17 obrigatório']);

    const stale = parseOpportunityDetail(
      validDetailPayload({
        fitReasons: [],
        compatibility: {
          criteriaVersion: 1,
          evaluatorVersion: 1,
          assessedAt: '2026-11-14T10:00:00Z',
          current: false,
          roleStatus: 'matched',
          technologyStatus: 'matched',
          reasonCodes: ['role_matched', 'technology_matched'],
        },
      }),
    );
    expect(stale.compatibility.current).toBe(false);
    expect(stale.compatibility.criteriaVersion).toBe(1);
    expect(stale.compatibilityEvidence).toHaveLength(1);
    expect(stale.compatibilityCriteria?.targetRoles).toEqual(['Desenvolvedor Backend Pleno Java']);

    const unassessed = parseOpportunityDetail(
      validDetailPayload({
        fitReasons: [],
        compatibility: {
          criteriaVersion: null,
          evaluatorVersion: null,
          assessedAt: null,
          current: false,
          roleStatus: 'unknown',
          technologyStatus: 'unknown',
          reasonCodes: ['not_assessed'],
        },
        compatibilityEvidence: [],
        compatibilityCriteria: null,
      }),
    );
    expect(unassessed.compatibility.current).toBe(false);
    expect(unassessed.compatibilityEvidence).toEqual([]);
    expect(unassessed.compatibilityCriteria).toBeNull();
  });

  it('não mascara compatibilityEvidence inválido ou ausente como lista vazia', () => {
    const missingEvidence = validDetailPayload() as Record<string, unknown>;
    delete missingEvidence.compatibilityEvidence;
    expect(() => parseOpportunityDetail(missingEvidence)).toThrow();

    const missingCriteria = validDetailPayload() as Record<string, unknown>;
    delete missingCriteria.compatibilityCriteria;
    expect(() => parseOpportunityDetail(missingCriteria)).toThrow();

    expect(() =>
      parseOpportunityDetail(validDetailPayload({ compatibilityEvidence: null })),
    ).toThrow();
    expect(() =>
      parseOpportunityDetail(
        validDetailPayload({
          compatibilityEvidence: [validEvidenceItem({ mentionKind: 'inferred' })],
        }),
      ),
    ).toThrow();
    expect(() => parseOpportunityDetail(validDetailPayload({ facts: 'Java' }))).toThrow();
    expect(() => parseOpportunityDetail(validDetailPayload({ inferred: null }))).toThrow();
    expect(() => parseOpportunityDetail(validDetailPayload({ missing: [1] }))).toThrow();
    expect(() => parseOpportunityDetail(validDetailPayload({ sources: null }))).toThrow();
    expect(() =>
      parseOpportunityDetail(validDetailPayload({ sources: [{ source: 'a', url: 'b' }] })),
    ).toThrow();
  });
});
