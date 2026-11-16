import { api } from './client';
import {
  parseHomeData,
  parseOpportunityListResponse,
  type CompatibilitySummary,
  type OpportunityListResponse,
  type OpportunitySummary,
  type RefreshStatus,
} from './compatibility';
import {
  parseOpportunityDetail,
  type CompatibilityCriteriaSnapshot,
  type CompatibilityEvidenceItem,
  type OpportunityDetailData,
} from './compatibility-detail';
import type { IntegrationState } from '../components/StatusBadge';

export type {
  CompatibilityCriteriaSnapshot,
  CompatibilityEvidenceItem,
  CompatibilitySummary,
  OpportunityDetailData,
  OpportunityListResponse,
  OpportunitySummary,
  RefreshStatus,
};

export interface ListResult<T> {
  items: T[];
  dataAsOf: string | null;
}
export interface Opportunity {
  salaryReference?: {
    status: 'available' | 'unavailable' | 'non_monthly';
    reason?: string;
    estimate?: SalaryObservation;
  } | null;
  glassdoorUrl?: string;
  id: string;
  title: string;
  companyName: string;
  location: string;
  workMode: 'remote' | 'hybrid' | 'onsite' | 'unknown';
  contractType: 'clt' | 'pj' | 'other' | 'unknown';
  salary: {
    min: number | null;
    max: number | null;
    unit: 'monthly' | 'hourly' | 'yearly' | 'unknown';
  } | null;
  fitReasons: string[];
  userDecision: 'none' | 'discarded' | 'applied';
  availability: 'open' | 'closed' | 'unconfirmed';
  deadlineAt?: string | null;
  publishedAt?: string | null;
  /** Fonte consultada por último (resumos de lista e da página inicial). */
  source?: OpportunitySource | null;
  /** Nível da empresa, quando o serviço informar. */
  companyTier?: 'priority' | 'suggested' | null;
  compatibility?: CompatibilitySummary;
}
export interface SalaryObservation {
  id: string;
  criteria: {
    company?: string;
    role: string;
    seniority: string;
    location: string;
    workMode: string;
    contractType: string;
  };
  monthlyMin: number | null;
  monthlyMax: number | null;
  originalMin: number;
  originalMax: number;
  originalUnit: string;
  currency: string;
  convertedFromAnnual: boolean;
  sampleSize: number | null;
  source: string;
  sourceUrl: string;
  consultedAt: string;
  sourceUpdatedOn?: string | null;
  expiresAt: string;
  stale: boolean;
  scope: 'company' | 'market';
}
export interface OpportunitySource {
  source: string;
  url: string;
  fetchedAt: string;
}
export interface OpportunityDetail extends Opportunity {
  compatibilityEvidence?: CompatibilityEvidenceItem[];
  compatibilityCriteria?: CompatibilityCriteriaSnapshot | null;
  facts: string[];
  inferred: string[];
  missing: string[];
  sources: OpportunitySource[];
}
export interface Integration {
  id: string;
  state: IntegrationState;
  lastSuccessAt: string | null;
  lastAttemptAt: string | null;
  detail?: string;
}
export type ApplicationStatus =
  | 'submitted_awaiting'
  | 'contact_received'
  | 'interview'
  | 'offer'
  | 'closed_rejected'
  | 'withdrawn';
export interface Application {
  id: string;
  opportunityId: string;
  title: string;
  companyName: string;
  status: ApplicationStatus;
  nextAction: string | null;
  nextActionDueAt: string | null;
  duePrecision: 'exact' | 'date_only' | 'ambiguous';
}
export interface ApplicationDetail extends Application {
  history: {
    id: string;
    toStatus: ApplicationStatus;
    author: string;
    reason: string;
    at: string;
    evidence: { ref: string; quote: string } | null;
  }[];
}
export interface HomeData {
  dataAsOf: string | null;
  assessment: RefreshStatus;
  integrations: Integration[];
  selectionToday: {
    localDate: string;
    count: number;
    consumedCount: number;
    limit: number;
    items: OpportunitySummary[];
  };
  applicationsWithNextActions: Application[];
  mailNeedingAttention: { id: string; subject: string }[];
  reviewQueueCount: number;
  missedRoutines: { routineId: string; scheduledFor: string }[];
}
export interface DedupeCandidate {
  id: string;
  opportunityA: Opportunity;
  opportunityB: Opportunity;
  signal: string;
}
export interface ReviewItem {
  id: string;
  kind: string;
  summary: string;
  targetRef: string;
  createdAt: string;
}
export interface TaskRun {
  id: string;
  state: 'queued' | 'running' | 'completed' | 'failed' | 'paused';
  pauseReason: string | null;
  errorSummary: string | null;
}
export const panel = {
  home: async (signal?: AbortSignal): Promise<HomeData> =>
    parseHomeData(await api<unknown>('/api/home', { signal })),
  opportunities: async (list: string, signal?: AbortSignal): Promise<OpportunityListResponse> =>
    parseOpportunityListResponse(
      await api<unknown>(`/api/opportunities?list=${encodeURIComponent(list)}`, { signal }),
    ),
  opportunity: async (id: string, signal?: AbortSignal): Promise<OpportunityDetailData> =>
    parseOpportunityDetail(
      await api<unknown>(`/api/opportunities/${encodeURIComponent(id)}`, { signal }),
    ),
  applications: (signal?: AbortSignal) =>
    api<ListResult<Application>>('/api/applications', { signal }),
  application: (id: string, signal?: AbortSignal) =>
    api<ApplicationDetail>(`/api/applications/${encodeURIComponent(id)}`, { signal }),
};
export function post<T = void>(path: string, body?: unknown) {
  return api<T>(path, { method: 'POST', body, idempotencyKey: crypto.randomUUID() });
}
