import {
  CORRECTABLE_OPPORTUNITY_FIELDS,
  parseOpportunitySummary,
  type CorrectableOpportunityField,
  type OpportunitySummary,
} from './compatibility';
import type {
  OpportunityCorrectionEntry,
  OpportunitySource,
  OpportunityTimelineEvent,
  SeniorityAssessment,
  TimelineEventKind,
} from './panel';
import { parsePreparation, type Preparation } from './profile';

export const EVIDENCE_FIELDS = ['title', 'description'] as const;
export type EvidenceField = (typeof EVIDENCE_FIELDS)[number];

export const MENTION_KINDS = [
  'required',
  'optional',
  'usage',
  'negated',
  'incidental',
  'uncertain',
] as const;
export type MentionKind = (typeof MENTION_KINDS)[number];

const SENIORITY_STATUSES = ['fact', 'inferred', 'absent', 'corrected_title'] as const;
export { CORRECTABLE_OPPORTUNITY_FIELDS } from './compatibility';
export type { CorrectableOpportunityField } from './compatibility';
export type CorrectableField = CorrectableOpportunityField;
const TIMELINE_KINDS: readonly TimelineEventKind[] = [
  'discovered',
  'selected_today',
  'external_opened',
  'corrected',
  'discarded',
  'restored',
  'applied',
] as const;

export interface CompatibilityEvidenceItem {
  source: string;
  sourceUrl: string | null;
  fetchedAt: string | null;
  field: EvidenceField;
  section: string | null;
  quote: string;
  matchedCriterion: string | null;
  observedTerm: string | null;
  mentionKind: MentionKind;
  observedVersion: string | null;
  configuredVersion: string | null;
}

export interface CompatibilityCriteriaSnapshot {
  targetRoles: string[];
  stackKeywords: string[];
}

export const JUNIOR_EXCEPTION_STATUSES = [
  'qualified',
  'excluded',
  'not_applicable',
  'unknown',
] as const;
export type JuniorExceptionStatus = (typeof JUNIOR_EXCEPTION_STATUSES)[number];

export const JUNIOR_SALARY_STATUSES = [
  'confirmed',
  'range_crosses_floor',
  'below_floor',
  'not_announced',
  'contract_unknown',
  'floor_missing',
] as const;
export type JuniorSalaryStatus = (typeof JUNIOR_SALARY_STATUSES)[number];

export const JUNIOR_SALARY_SOURCES = ['announced', 'estimate', 'none'] as const;
export type JuniorSalarySource = (typeof JUNIOR_SALARY_SOURCES)[number];

export const JUNIOR_JUSTIFICATION_STATUSES = [
  'accepted',
  'missing',
  'not_accepted_by_criteria',
] as const;
export type JuniorJustificationStatus = (typeof JUNIOR_JUSTIFICATION_STATUSES)[number];

export interface JuniorSalaryCheck {
  status: JuniorSalaryStatus;
  /** `estimate`: só há estimativa, que nunca confirma o piso. */
  source: JuniorSalarySource;
  contract: string | null;
  unit: string | null;
  floor: number | null;
  announcedMin: number | null;
  announcedMax: number | null;
}

/** Decisão do serviço, no mesmo nível usado pelo ranking (RuleBasedRanker). */
export type JuniorExceptionDecision =
  | { status: 'not_applicable' }
  | { status: 'unknown' }
  | {
      status: 'qualified' | 'excluded';
      workFit: boolean;
      salary: JuniorSalaryCheck;
      /** `text` preserva a razão da empresa mesmo quando outra condição falha. */
      justification: { status: JuniorJustificationStatus; text: string | null };
    };

export interface OpportunityDetailData extends OpportunitySummary {
  compatibilityEvidence: CompatibilityEvidenceItem[];
  compatibilityCriteria: CompatibilityCriteriaSnapshot | null;
  facts: string[];
  inferred: string[];
  missing: string[];
  sources: OpportunitySource[];
  seniority: SeniorityAssessment;
  corrections: OpportunityCorrectionEntry[];
  timeline: OpportunityTimelineEvent[];
  /** Itens de estudo calculados com o perfil atual (US006); `null` em respostas sem o campo. */
  preparation: Preparation | null;
  /** `null` em respostas anteriores ao campo (fixtures antigas). */
  juniorException: JuniorExceptionDecision | null;
}

function asRecord(raw: unknown, name: string): Record<string, unknown> {
  if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) {
    throw new Error(`Formato inválido para ${name}.`);
  }
  return raw as Record<string, unknown>;
}

function asString(raw: unknown, field: string): string {
  if (typeof raw !== 'string') throw new Error(`Campo ${field} inválido.`);
  return raw;
}

function asNonEmptyString(raw: unknown, field: string): string {
  const value = asString(raw, field);
  if (!value.trim()) throw new Error(`Campo obrigatório ${field} vazio.`);
  return value;
}

function asNullableString(raw: unknown, field: string): string | null {
  if (raw === null) return null;
  return asString(raw, field);
}

function asEnum<T extends string>(raw: unknown, allowed: readonly T[], field: string): T {
  if (typeof raw !== 'string' || !allowed.includes(raw as T)) {
    throw new Error(`Valor inesperado para ${field}.`);
  }
  return raw as T;
}

function asStringArray(raw: unknown, field: string): string[] {
  if (!Array.isArray(raw)) throw new Error(`Lista ${field} inválida.`);
  return raw.map((item, i) => asString(item, `${field}[${i}]`));
}

function parseDetailSource(raw: unknown, index: number): OpportunitySource {
  const obj = asRecord(raw, `sources[${index}]`);
  return {
    source: asString(obj.source, `sources[${index}].source`),
    url: asString(obj.url, `sources[${index}].url`),
    fetchedAt: asString(obj.fetchedAt, `sources[${index}].fetchedAt`),
  };
}

function parseSeniority(raw: unknown): SeniorityAssessment {
  if (raw === undefined || raw === null) throw new Error('Campo seniority ausente.');
  const obj = asRecord(raw, 'seniority');
  return {
    label: obj.label === undefined ? null : asNullableString(obj.label, 'seniority.label'),
    status: asEnum(obj.status, SENIORITY_STATUSES, 'seniority.status'),
    reason: obj.reason === undefined ? null : asNullableString(obj.reason, 'seniority.reason'),
  };
}

function parseCorrectionEntry(raw: unknown, index: number): OpportunityCorrectionEntry {
  const obj = asRecord(raw, `corrections[${index}]`);
  return {
    id: asString(obj.id, `corrections[${index}].id`),
    fieldName: asEnum(
      obj.fieldName,
      CORRECTABLE_OPPORTUNITY_FIELDS,
      `corrections[${index}].fieldName`,
    ),
    previousValue:
      obj.previousValue === undefined
        ? null
        : asNullableString(obj.previousValue, `corrections[${index}].previousValue`),
    correctedValue:
      obj.correctedValue === undefined
        ? null
        : asNullableString(obj.correctedValue, `corrections[${index}].correctedValue`),
    reason:
      obj.reason === undefined
        ? null
        : asNullableString(obj.reason, `corrections[${index}].reason`),
    authorLabel: asString(obj.authorLabel, `corrections[${index}].authorLabel`),
    correctedAt: asString(obj.correctedAt, `corrections[${index}].correctedAt`),
  };
}

function parseTimelineEvent(raw: unknown, index: number): OpportunityTimelineEvent {
  const obj = asRecord(raw, `timeline[${index}]`);
  return {
    id: asString(obj.id, `timeline[${index}].id`),
    kind: asEnum(obj.kind, TIMELINE_KINDS, `timeline[${index}].kind`),
    actorLabel: asString(obj.actorLabel, `timeline[${index}].actorLabel`),
    description: asString(obj.description, `timeline[${index}].description`),
    note: obj.note === undefined ? null : asNullableString(obj.note, `timeline[${index}].note`),
    at: asString(obj.at, `timeline[${index}].at`),
  };
}

function asNullableMoney(raw: unknown, field: string): number | null {
  if (raw === null || raw === undefined) return null;
  const value = typeof raw === 'string' && raw.trim() ? Number(raw) : raw;
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new Error(`Campo ${field} inválido.`);
  }
  return value;
}

export function parseJuniorException(raw: unknown): JuniorExceptionDecision | null {
  if (raw === undefined || raw === null) return null;
  const obj = asRecord(raw, 'juniorException');
  const status = asEnum(obj.status, JUNIOR_EXCEPTION_STATUSES, 'juniorException.status');
  if (status === 'not_applicable' || status === 'unknown') return { status };
  if (typeof obj.workFit !== 'boolean') throw new Error('Campo juniorException.workFit inválido.');
  const salary = asRecord(obj.salary, 'juniorException.salary');
  const justification = asRecord(obj.justification, 'juniorException.justification');
  const optionalString = (value: unknown, field: string) =>
    value === undefined ? null : asNullableString(value, field);
  return {
    status,
    workFit: obj.workFit,
    salary: {
      status: asEnum(salary.status, JUNIOR_SALARY_STATUSES, 'juniorException.salary.status'),
      source: asEnum(salary.source, JUNIOR_SALARY_SOURCES, 'juniorException.salary.source'),
      contract: optionalString(salary.contract, 'juniorException.salary.contract'),
      unit: optionalString(salary.unit, 'juniorException.salary.unit'),
      floor: asNullableMoney(salary.floor, 'juniorException.salary.floor'),
      announcedMin: asNullableMoney(salary.announcedMin, 'juniorException.salary.announcedMin'),
      announcedMax: asNullableMoney(salary.announcedMax, 'juniorException.salary.announcedMax'),
    },
    justification: {
      status: asEnum(
        justification.status,
        JUNIOR_JUSTIFICATION_STATUSES,
        'juniorException.justification.status',
      ),
      text: optionalString(justification.text, 'juniorException.justification.text'),
    },
  };
}

export function parseCompatibilityEvidenceItem(raw: unknown): CompatibilityEvidenceItem {
  const obj = asRecord(raw, 'CompatibilityEvidenceItem');
  return {
    source: asNonEmptyString(obj.source, 'source'),
    sourceUrl: asNullableString(obj.sourceUrl, 'sourceUrl'),
    fetchedAt: asNullableString(obj.fetchedAt, 'fetchedAt'),
    field: asEnum(obj.field, EVIDENCE_FIELDS, 'field'),
    section: asNullableString(obj.section, 'section'),
    quote: asString(obj.quote, 'quote'),
    matchedCriterion: asNullableString(obj.matchedCriterion, 'matchedCriterion'),
    observedTerm: asNullableString(obj.observedTerm, 'observedTerm'),
    mentionKind: asEnum(obj.mentionKind, MENTION_KINDS, 'mentionKind'),
    observedVersion: asNullableString(obj.observedVersion, 'observedVersion'),
    configuredVersion: asNullableString(obj.configuredVersion, 'configuredVersion'),
  };
}

export function parseCompatibilityCriteria(raw: unknown): CompatibilityCriteriaSnapshot | null {
  if (raw === null) return null;
  const obj = asRecord(raw, 'compatibilityCriteria');
  return {
    targetRoles: asStringArray(obj.targetRoles, 'compatibilityCriteria.targetRoles'),
    stackKeywords: asStringArray(obj.stackKeywords, 'compatibilityCriteria.stackKeywords'),
  };
}

export function parseOpportunityDetail(raw: unknown): OpportunityDetailData {
  const obj = asRecord(raw, 'OpportunityDetailResponse');
  const summary = parseOpportunitySummary(raw);
  if (!Array.isArray(obj.compatibilityEvidence)) {
    throw new Error('Campo compatibilityEvidence inválido.');
  }
  if (!('compatibilityCriteria' in obj)) {
    throw new Error('Campo compatibilityCriteria ausente.');
  }
  if (!Array.isArray(obj.sources)) {
    throw new Error('Campo sources inválido.');
  }
  if (!Array.isArray(obj.corrections)) throw new Error('Campo corrections inválido.');
  if (!Array.isArray(obj.timeline)) throw new Error('Campo timeline inválido.');
  return {
    ...summary,
    compatibilityEvidence: obj.compatibilityEvidence.map((item) =>
      parseCompatibilityEvidenceItem(item),
    ),
    compatibilityCriteria: parseCompatibilityCriteria(obj.compatibilityCriteria),
    facts: asStringArray(obj.facts, 'facts'),
    inferred: asStringArray(obj.inferred, 'inferred'),
    missing: asStringArray(obj.missing, 'missing'),
    sources: obj.sources.map((item, index) => parseDetailSource(item, index)),
    seniority: parseSeniority(obj.seniority),
    corrections: obj.corrections.map((entry, index) => parseCorrectionEntry(entry, index)),
    timeline: obj.timeline.map((event, index) => parseTimelineEvent(event, index)),
    preparation: parsePreparation(obj.preparation),
    juniorException: parseJuniorException(obj.juniorException),
  };
}
