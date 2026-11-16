import type {
  Application,
  HomeData,
  Integration,
  Opportunity,
  OpportunitySource,
  SalaryObservation,
} from './panel';

export const REFRESH_STATES = ['pending', 'running', 'ready', 'failed'] as const;
export type RefreshState = (typeof REFRESH_STATES)[number];

export const ROLE_STATUSES = ['matched', 'mismatched', 'unknown'] as const;
export type RoleStatus = (typeof ROLE_STATUSES)[number];

export const TECHNOLOGY_STATUSES = ['matched', 'mismatched', 'unknown', 'unrestricted'] as const;
export type TechnologyStatus = (typeof TECHNOLOGY_STATUSES)[number];

const WORK_MODES = ['remote', 'hybrid', 'onsite', 'unknown'] as const;
const CONTRACT_TYPES = ['clt', 'pj', 'other', 'unknown'] as const;
const USER_DECISIONS = ['none', 'discarded', 'applied'] as const;
const AVAILABILITIES = ['open', 'closed', 'unconfirmed'] as const;
const COMPANY_TIERS = ['priority', 'suggested'] as const;
const SALARY_UNITS = ['monthly', 'hourly', 'yearly', 'unknown'] as const;
const INTEGRATION_STATES = ['ok', 'degraded', 'disconnected', 'paused'] as const;
const APPLICATION_STATUSES = [
  'submitted_awaiting',
  'contact_received',
  'interview',
  'offer',
  'closed_rejected',
  'withdrawn',
] as const;
const DUE_PRECISIONS = ['exact', 'date_only', 'ambiguous'] as const;

export interface RefreshStatus {
  criteriaVersion: number;
  state: RefreshState;
  stale: boolean;
  assessedCount: number;
  remainingCount: number;
  startedAt: string | null;
  finishedAt: string | null;
  errorCode: string | null;
}

export interface CompatibilitySummary {
  criteriaVersion: number | null;
  evaluatorVersion: number | null;
  assessedAt: string | null;
  current: boolean;
  roleStatus: RoleStatus;
  technologyStatus: TechnologyStatus;
  reasonCodes: string[];
}

export interface OpportunitySummary extends Opportunity {
  compatibility: CompatibilitySummary;
}

export interface OpportunityListResponse {
  items: OpportunitySummary[];
  dataAsOf: string | null;
  assessment: RefreshStatus;
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

function asNullableString(raw: unknown, field: string): string | null {
  if (raw === null) return null;
  return asString(raw, field);
}

function asOptionalString(raw: unknown, field: string): string | undefined {
  if (raw === null || raw === undefined) return undefined;
  return asString(raw, field);
}

function asOptionalNullableString(raw: unknown, field: string): string | null | undefined {
  if (raw === undefined) return undefined;
  if (raw === null) return null;
  return asString(raw, field);
}

function asBoolean(raw: unknown, field: string): boolean {
  if (typeof raw !== 'boolean') throw new Error(`Campo ${field} inválido.`);
  return raw;
}

function asInt(raw: unknown, field: string, min: number): number {
  if (typeof raw !== 'number' || !Number.isInteger(raw) || raw < min) {
    throw new Error(`Campo numérico ${field} inválido.`);
  }
  return raw;
}

function asNullablePositiveInt(raw: unknown, field: string): number | null {
  if (raw === null) return null;
  return asInt(raw, field, 1);
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

export function parseRefreshStatus(raw: unknown): RefreshStatus {
  const obj = asRecord(raw, 'RefreshStatus');
  if (obj.startedAt === undefined || obj.finishedAt === undefined || obj.errorCode === undefined) {
    throw new Error('Campos de execução ausentes em RefreshStatus.');
  }
  return {
    criteriaVersion: asInt(obj.criteriaVersion, 'criteriaVersion', 1),
    state: asEnum(obj.state, REFRESH_STATES, 'state'),
    stale: asBoolean(obj.stale, 'stale'),
    assessedCount: asInt(obj.assessedCount, 'assessedCount', 0),
    remainingCount: asInt(obj.remainingCount, 'remainingCount', 0),
    startedAt: asNullableString(obj.startedAt, 'startedAt'),
    finishedAt: asNullableString(obj.finishedAt, 'finishedAt'),
    errorCode: asNullableString(obj.errorCode, 'errorCode'),
  };
}

export function parseCompatibilitySummary(raw: unknown): CompatibilitySummary {
  const obj = asRecord(raw, 'CompatibilitySummary');
  if (
    obj.criteriaVersion === undefined ||
    obj.evaluatorVersion === undefined ||
    obj.assessedAt === undefined
  ) {
    throw new Error('Metadados de versão ausentes em CompatibilitySummary.');
  }
  return {
    criteriaVersion: asNullablePositiveInt(obj.criteriaVersion, 'criteriaVersion'),
    evaluatorVersion: asNullablePositiveInt(obj.evaluatorVersion, 'evaluatorVersion'),
    assessedAt: asNullableString(obj.assessedAt, 'assessedAt'),
    current: asBoolean(obj.current, 'current'),
    roleStatus: asEnum(obj.roleStatus, ROLE_STATUSES, 'roleStatus'),
    technologyStatus: asEnum(obj.technologyStatus, TECHNOLOGY_STATUSES, 'technologyStatus'),
    reasonCodes: asStringArray(obj.reasonCodes, 'reasonCodes'),
  };
}

function parseSalary(raw: unknown): Opportunity['salary'] {
  if (raw === null) return null;
  const obj = asRecord(raw, 'salary');
  return {
    min: obj.min === null ? null : asNumber(obj.min, 'salary.min'),
    max: obj.max === null ? null : asNumber(obj.max, 'salary.max'),
    unit: asEnum(obj.unit, SALARY_UNITS, 'salary.unit'),
  };
}

function asNumber(raw: unknown, field: string): number {
  if (typeof raw !== 'number' || !Number.isFinite(raw)) {
    throw new Error(`Campo numérico ${field} inválido.`);
  }
  return raw;
}

function parseSource(raw: unknown): OpportunitySource | null {
  if (raw === null || raw === undefined) return null;
  const obj = asRecord(raw, 'source');
  return {
    source: asString(obj.source, 'source.source'),
    url: asString(obj.url, 'source.url'),
    fetchedAt: asString(obj.fetchedAt, 'source.fetchedAt'),
  };
}

function parseSalaryObservation(raw: unknown): SalaryObservation {
  const obj = asRecord(raw, 'salaryReference.estimate');
  const criteria = asRecord(obj.criteria, 'salaryReference.estimate.criteria');
  return {
    id: asString(obj.id, 'salaryReference.estimate.id'),
    criteria: {
      company: asOptionalString(criteria.company, 'salaryReference.estimate.criteria.company'),
      role: asString(criteria.role, 'salaryReference.estimate.criteria.role'),
      seniority: asString(criteria.seniority, 'salaryReference.estimate.criteria.seniority'),
      location: asString(criteria.location, 'salaryReference.estimate.criteria.location'),
      workMode: asString(criteria.workMode, 'salaryReference.estimate.criteria.workMode'),
      contractType: asString(
        criteria.contractType,
        'salaryReference.estimate.criteria.contractType',
      ),
    },
    monthlyMin:
      obj.monthlyMin === null
        ? null
        : asNumber(obj.monthlyMin, 'salaryReference.estimate.monthlyMin'),
    monthlyMax:
      obj.monthlyMax === null
        ? null
        : asNumber(obj.monthlyMax, 'salaryReference.estimate.monthlyMax'),
    originalMin: asNumber(obj.originalMin, 'salaryReference.estimate.originalMin'),
    originalMax: asNumber(obj.originalMax, 'salaryReference.estimate.originalMax'),
    originalUnit: asString(obj.originalUnit, 'salaryReference.estimate.originalUnit'),
    currency: asString(obj.currency, 'salaryReference.estimate.currency'),
    convertedFromAnnual: asBoolean(
      obj.convertedFromAnnual,
      'salaryReference.estimate.convertedFromAnnual',
    ),
    sampleSize:
      obj.sampleSize === null
        ? null
        : asInt(obj.sampleSize, 'salaryReference.estimate.sampleSize', 0),
    source: asString(obj.source, 'salaryReference.estimate.source'),
    sourceUrl: asString(obj.sourceUrl, 'salaryReference.estimate.sourceUrl'),
    consultedAt: asString(obj.consultedAt, 'salaryReference.estimate.consultedAt'),
    sourceUpdatedOn: asOptionalNullableString(
      obj.sourceUpdatedOn,
      'salaryReference.estimate.sourceUpdatedOn',
    ),
    expiresAt: asString(obj.expiresAt, 'salaryReference.estimate.expiresAt'),
    stale: asBoolean(obj.stale, 'salaryReference.estimate.stale'),
    scope: asEnum(obj.scope, ['company', 'market'] as const, 'salaryReference.estimate.scope'),
  };
}

function parseSalaryReference(raw: unknown): Opportunity['salaryReference'] {
  if (raw === null || raw === undefined) return null;
  const obj = asRecord(raw, 'salaryReference');
  const status = asEnum(obj.status, ['available', 'unavailable', 'non_monthly'] as const, 'status');
  return {
    status,
    reason: asOptionalString(obj.reason, 'salaryReference.reason'),
    estimate:
      obj.estimate === null || obj.estimate === undefined
        ? undefined
        : parseSalaryObservation(obj.estimate),
  };
}

export function parseOpportunitySummary(raw: unknown): OpportunitySummary {
  const obj = asRecord(raw, 'OpportunitySummary');
  return {
    id: asString(obj.id, 'id'),
    title: asString(obj.title, 'title'),
    companyName: asString(obj.companyName, 'companyName'),
    location: asNullableString(obj.location, 'location') ?? '',
    workMode: asEnum(obj.workMode, WORK_MODES, 'workMode'),
    contractType: asEnum(obj.contractType, CONTRACT_TYPES, 'contractType'),
    salary: parseSalary(obj.salary),
    fitReasons: asStringArray(obj.fitReasons, 'fitReasons'),
    userDecision: asEnum(obj.userDecision, USER_DECISIONS, 'userDecision'),
    availability: asEnum(obj.availability, AVAILABILITIES, 'availability'),
    deadlineAt: asOptionalNullableString(obj.deadlineAt, 'deadlineAt'),
    publishedAt: asOptionalNullableString(obj.publishedAt, 'publishedAt'),
    source: parseSource(obj.source),
    companyTier:
      obj.companyTier === null || obj.companyTier === undefined
        ? null
        : asEnum(obj.companyTier, COMPANY_TIERS, 'companyTier'),
    salaryReference: parseSalaryReference(obj.salaryReference),
    glassdoorUrl: asOptionalString(obj.glassdoorUrl, 'glassdoorUrl'),
    compatibility: parseCompatibilitySummary(obj.compatibility),
  };
}

export function parseOpportunityListResponse(raw: unknown): OpportunityListResponse {
  const obj = asRecord(raw, 'OpportunityListResponse');
  if (!Array.isArray(obj.items)) throw new Error('Campo items inválido.');
  if (!('dataAsOf' in obj)) throw new Error('Campo dataAsOf ausente.');
  return {
    items: obj.items.map(parseOpportunitySummary),
    dataAsOf: asNullableString(obj.dataAsOf, 'dataAsOf'),
    assessment: parseRefreshStatus(obj.assessment),
  };
}

function parseIntegration(raw: unknown): Integration {
  const obj = asRecord(raw, 'Integration');
  return {
    id: asString(obj.id, 'integration.id'),
    state: asEnum(obj.state, INTEGRATION_STATES, 'integration.state'),
    lastSuccessAt: asNullableString(obj.lastSuccessAt, 'integration.lastSuccessAt'),
    lastAttemptAt: asNullableString(obj.lastAttemptAt, 'integration.lastAttemptAt'),
    detail: asOptionalString(obj.detail, 'integration.detail'),
  };
}

function parseApplication(raw: unknown): Application {
  const obj = asRecord(raw, 'Application');
  return {
    id: asString(obj.id, 'application.id'),
    opportunityId: asString(obj.opportunityId, 'application.opportunityId'),
    title: asString(obj.title, 'application.title'),
    companyName: asString(obj.companyName, 'application.companyName'),
    status: asEnum(obj.status, APPLICATION_STATUSES, 'application.status'),
    nextAction: asNullableString(obj.nextAction, 'application.nextAction'),
    nextActionDueAt: asNullableString(obj.nextActionDueAt, 'application.nextActionDueAt'),
    duePrecision: asEnum(obj.duePrecision, DUE_PRECISIONS, 'application.duePrecision'),
  };
}

export function parseHomeData(raw: unknown): HomeData {
  const obj = asRecord(raw, 'HomeData');
  if (!('dataAsOf' in obj)) throw new Error('Campo dataAsOf ausente.');
  if (!Array.isArray(obj.integrations)) throw new Error('Campo integrations inválido.');
  if (!Array.isArray(obj.applicationsWithNextActions)) {
    throw new Error('Campo applicationsWithNextActions inválido.');
  }
  if (!Array.isArray(obj.mailNeedingAttention)) {
    throw new Error('Campo mailNeedingAttention inválido.');
  }
  if (!Array.isArray(obj.missedRoutines)) throw new Error('Campo missedRoutines inválido.');

  const sel = asRecord(obj.selectionToday, 'selectionToday');
  if (!Array.isArray(sel.items)) throw new Error('Campo selectionToday.items inválido.');

  return {
    dataAsOf: asNullableString(obj.dataAsOf, 'dataAsOf'),
    assessment: parseRefreshStatus(obj.assessment),
    integrations: obj.integrations.map(parseIntegration),
    selectionToday: {
      localDate: asString(sel.localDate, 'selectionToday.localDate'),
      count: asInt(sel.count, 'selectionToday.count', 0),
      consumedCount: asInt(sel.consumedCount, 'selectionToday.consumedCount', 0),
      limit: asInt(sel.limit, 'selectionToday.limit', 1),
      items: sel.items.map(parseOpportunitySummary),
    },
    applicationsWithNextActions: obj.applicationsWithNextActions.map(parseApplication),
    mailNeedingAttention: obj.mailNeedingAttention.map((mail) => {
      const m = asRecord(mail, 'MailSummary');
      return {
        id: asString(m.id, 'mail.id'),
        subject: asString(m.subject, 'mail.subject'),
      };
    }),
    reviewQueueCount: asInt(obj.reviewQueueCount, 'reviewQueueCount', 0),
    missedRoutines: obj.missedRoutines.map((routine) => {
      const r = asRecord(routine, 'MissedRoutine');
      return {
        routineId: asString(r.routineId, 'missedRoutine.routineId'),
        scheduledFor: asString(r.scheduledFor, 'missedRoutine.scheduledFor'),
      };
    }),
  };
}

export interface CompatibilityDescription {
  label: string;
  tone: 'ok' | 'warn' | 'muted';
  pending: boolean;
}

/** Traduz o estado já calculado pelo backend para rótulo em texto acessível. */
export function describeCompatibility(summary: CompatibilitySummary): CompatibilityDescription {
  if (summary.reasonCodes.includes('not_assessed')) {
    return {
      label: 'Compatibilidade: ainda não avaliada',
      tone: 'muted',
      pending: true,
    };
  }
  if (!summary.current) {
    return {
      label: `Avaliação desatualizada (critério v${summary.criteriaVersion ?? '?'})`,
      tone: 'warn',
      pending: true,
    };
  }
  if (summary.roleStatus === 'mismatched' || summary.technologyStatus === 'mismatched') {
    return {
      label: 'Incompatível com o perfil',
      tone: 'muted',
      pending: false,
    };
  }
  if (summary.roleStatus === 'unknown' && summary.technologyStatus === 'unknown') {
    return {
      label: 'Pendência: função e tecnologia não confirmadas',
      tone: 'warn',
      pending: true,
    };
  }
  if (summary.roleStatus === 'unknown') {
    return {
      label: 'Pendência: função não confirmada',
      tone: 'warn',
      pending: true,
    };
  }
  if (summary.technologyStatus === 'unknown') {
    return {
      label: 'Pendência: tecnologia não confirmada',
      tone: 'warn',
      pending: true,
    };
  }
  if (summary.technologyStatus === 'unrestricted') {
    return {
      label: 'Compatível com o cargo (sem restrição de tecnologia)',
      tone: 'ok',
      pending: false,
    };
  }
  return {
    label: 'Compatível com o perfil',
    tone: 'ok',
    pending: false,
  };
}

export function describeRefreshStatus(status: RefreshStatus): string | null {
  if (!status.stale) return null;
  if (status.state === 'failed') {
    return status.remainingCount > 0
      ? `A atualização da compatibilidade falhou (${status.remainingCount} vaga(s) pendente(s)). Resultados antigos permanecem ocultos até concluir.`
      : 'A atualização da compatibilidade falhou. Verifique os critérios para tentar novamente.';
  }
  if (status.state === 'running') {
    return `Atualizando compatibilidade para o critério v${status.criteriaVersion} (${status.remainingCount} vaga(s) pendente(s)). Resultados antigos permanecem ocultos.`;
  }
  if (status.remainingCount > 0) {
    return `Avaliação de compatibilidade desatualizada ou pendente para o critério v${status.criteriaVersion} (${status.remainingCount} vaga(s) pendente(s)). Resultados antigos ficam ocultos; vagas já atualizadas permanecem visíveis.`;
  }
  return `Critérios v${status.criteriaVersion} aguardando avaliação nas próximas buscas ou atualização no perfil.`;
}
