import { api } from './client';
import type { IntegrationState } from '../components/StatusBadge';

export interface ListResult<T> {
  items: T[];
  dataAsOf: string | null;
}
export interface Opportunity {
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
}
export interface OpportunitySource {
  source: string;
  url: string;
  fetchedAt: string;
}
export interface OpportunityDetail extends Opportunity {
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
  integrations: Integration[];
  selectionToday: { localDate: string; count: number; limit: number; items: Opportunity[] };
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
  home: (signal?: AbortSignal) => api<HomeData>('/api/home', { signal }),
  opportunities: (list: string, signal?: AbortSignal) =>
    api<ListResult<Opportunity>>(`/api/opportunities?list=${encodeURIComponent(list)}`, { signal }),
  opportunity: (id: string, signal?: AbortSignal) =>
    api<OpportunityDetail>(`/api/opportunities/${encodeURIComponent(id)}`, { signal }),
  applications: (signal?: AbortSignal) =>
    api<ListResult<Application>>('/api/applications', { signal }),
  application: (id: string, signal?: AbortSignal) =>
    api<ApplicationDetail>(`/api/applications/${encodeURIComponent(id)}`, { signal }),
};
export function post<T = void>(path: string, body?: unknown) {
  return api<T>(path, { method: 'POST', body, idempotencyKey: crypto.randomUUID() });
}
