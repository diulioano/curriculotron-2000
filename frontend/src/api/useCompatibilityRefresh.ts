import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import {
  getCompatibilityStatus,
  getCriteria,
  reassessCompatibility,
  updateCriteria,
  type CriteriaResponse,
  type CriteriaUpdatePayload,
  type ReassessResponse,
  type RefreshStatus,
} from './compatibility';
import { panel } from './panel';
import { saveProfile, type ProfileView, type SavePayload } from './profile';

export const PROFILE_KEY = ['profile'] as const;

export const COMPATIBILITY_QUERY_KEYS = [
  ['criteria'],
  ['compatibility-status'],
  ['opportunities'],
  ['opportunity'],
  ['home'],
] as const;

const VIEW_QUERY_KEYS = [['opportunities'], ['opportunity'], ['home']] as const;

interface CoordinatorState {
  generation: number;
  minCriteriaVersion: number;
  pendingMutations: Set<Promise<unknown>>;
}

const coordinatorStates = new WeakMap<QueryClient, CoordinatorState>();

function getCoordinatorState(client: QueryClient): CoordinatorState {
  let state = coordinatorStates.get(client);
  if (!state) {
    state = {
      generation: 0,
      minCriteriaVersion: 0,
      pendingMutations: new Set(),
    };
    coordinatorStates.set(client, state);
  }
  return state;
}

export function getCompatibilityGeneration(client: QueryClient): number {
  return getCoordinatorState(client).generation;
}

export function getMinCriteriaVersion(client: QueryClient): number {
  return getCoordinatorState(client).minCriteriaVersion;
}

export function advanceCompatibilityGeneration(client: QueryClient, minVersion?: number): number {
  const state = getCoordinatorState(client);
  state.generation += 1;
  if (minVersion !== undefined && minVersion > state.minCriteriaVersion) {
    state.minCriteriaVersion = minVersion;
  }
  return state.generation;
}

export async function cancelCompatibilityQueries(client: QueryClient): Promise<void> {
  await Promise.all(COMPATIBILITY_QUERY_KEYS.map((queryKey) => client.cancelQueries({ queryKey })));
}

export async function invalidateCompatibilityQueries(client: QueryClient): Promise<void> {
  await Promise.all(
    COMPATIBILITY_QUERY_KEYS.map((queryKey) => client.invalidateQueries({ queryKey })),
  );
}

export async function cancelCompatibilityViews(client: QueryClient): Promise<void> {
  await Promise.all(VIEW_QUERY_KEYS.map((queryKey) => client.cancelQueries({ queryKey })));
}

export async function invalidateCompatibilityViews(client: QueryClient): Promise<void> {
  await cancelCompatibilityViews(client);
  await Promise.all(VIEW_QUERY_KEYS.map((queryKey) => client.invalidateQueries({ queryKey })));
}

const MAX_READ_ATTEMPTS = 4;

export async function runCoordinatedRead<T>(
  client: QueryClient,
  options: {
    signal?: AbortSignal;
    fetcher: (signal?: AbortSignal) => Promise<T>;
    getVersion?: (data: T) => number | null;
  },
): Promise<T> {
  const state = getCoordinatorState(client);

  for (let attempt = 0; attempt < MAX_READ_ATTEMPTS; attempt++) {
    if (state.pendingMutations.size > 0) {
      await Promise.allSettled([...state.pendingMutations]);
    }
    if (options.signal?.aborted) {
      throw new DOMException('Aborted', 'AbortError');
    }

    const attemptGeneration = state.generation;
    const result = await options.fetcher(options.signal);

    if (options.signal?.aborted) {
      throw new DOMException('Aborted', 'AbortError');
    }

    const version = options.getVersion ? options.getVersion(result) : null;
    const staleByGeneration = state.generation !== attemptGeneration;
    const staleByVersion = version !== null && version < state.minCriteriaVersion;

    if (!staleByGeneration && !staleByVersion) {
      if (version !== null && version > state.minCriteriaVersion) {
        state.minCriteriaVersion = version;
      }
      return result;
    }
  }

  throw new Error('Dados de compatibilidade foram atualizados durante a leitura; tente novamente.');
}

async function runTrackedMutation<T>(client: QueryClient, executor: () => Promise<T>): Promise<T> {
  const state = getCoordinatorState(client);
  state.generation += 1;

  const work = (async () => {
    await cancelCompatibilityQueries(client);
    return executor();
  })();

  state.pendingMutations.add(work);
  try {
    return await work;
  } finally {
    state.pendingMutations.delete(work);
  }
}

export function useCoordinatedHomeQuery() {
  const client = useQueryClient();
  return useQuery({
    queryKey: ['home'],
    queryFn: ({ signal }) =>
      runCoordinatedRead(client, {
        signal,
        fetcher: panel.home,
        getVersion: (data) => data.assessment.criteriaVersion,
      }),
  });
}

export function useCoordinatedOpportunitiesQuery(list: string) {
  const client = useQueryClient();
  return useQuery({
    queryKey: ['opportunities', list],
    queryFn: ({ signal }) =>
      runCoordinatedRead(client, {
        signal,
        fetcher: (abortSignal) => panel.opportunities(list, abortSignal),
        getVersion: (data) => data.assessment.criteriaVersion,
      }),
  });
}

export function useCoordinatedOpportunityDetailQuery(id: string) {
  const client = useQueryClient();
  return useQuery({
    queryKey: ['opportunity', id],
    queryFn: ({ signal }) =>
      runCoordinatedRead(client, {
        signal,
        fetcher: (abortSignal) => panel.opportunity(id, abortSignal),
      }),
  });
}

export function useCoordinatedCriteriaQuery() {
  const client = useQueryClient();
  return useQuery({
    queryKey: ['criteria'],
    queryFn: async ({ signal }) => {
      const criteria = await runCoordinatedRead(client, {
        signal,
        fetcher: getCriteria,
        getVersion: (data) => data.version,
      });
      client.setQueryData(['compatibility-status'], criteria.assessment);
      return criteria;
    },
  });
}

export function useCoordinatedCompatibilityStatusQuery(
  initialStatus: RefreshStatus,
  options: { onNonRunningStatus?: (status: RefreshStatus) => void } = {},
) {
  const client = useQueryClient();
  return useQuery({
    queryKey: ['compatibility-status'],
    queryFn: async ({ signal }) => {
      const latest = await runCoordinatedRead(client, {
        signal,
        fetcher: getCompatibilityStatus,
        getVersion: (data) => data.criteriaVersion,
      });
      client.setQueryData<CriteriaResponse>(['criteria'], (old) =>
        old && old.version === latest.criteriaVersion ? { ...old, assessment: latest } : old,
      );
      if (latest.state !== 'running') {
        options.onNonRunningStatus?.(latest);
        await invalidateCompatibilityViews(client);
      }
      return latest;
    },
    retry: false,
    enabled: (query) =>
      !query.state.error && (query.state.data ?? initialStatus).state === 'running',
    refetchInterval: (query) =>
      !query.state.error && (query.state.data ?? initialStatus).state === 'running' ? 200 : false,
  });
}

export function useSaveCriteriaMutation(callbacks?: {
  onSuccess?: (saved: CriteriaResponse) => void;
  onError?: (error: Error) => void;
}) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (payload: CriteriaUpdatePayload) =>
      runTrackedMutation(client, () => updateCriteria(payload)),
    onSuccess: async (saved) => {
      advanceCompatibilityGeneration(client, saved.version);
      await cancelCompatibilityQueries(client);
      client.setQueryData(['criteria'], saved);
      client.setQueryData(['compatibility-status'], saved.assessment);
      await invalidateCompatibilityQueries(client);
      callbacks?.onSuccess?.(saved);
    },
    onError: (error) => {
      advanceCompatibilityGeneration(client);
      callbacks?.onError?.(error);
    },
  });
}

export function useReassessMutation(callbacks?: {
  onMutate?: () => void;
  onSuccess?: (result: ReassessResponse) => void;
  onError?: (error: Error) => Promise<void> | void;
}) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (criteriaVersion: number) =>
      runTrackedMutation(client, () => reassessCompatibility(criteriaVersion)),
    onMutate: () => {
      callbacks?.onMutate?.();
    },
    onSuccess: async (result) => {
      const previousStatus = client.getQueryData<RefreshStatus>(['compatibility-status']);
      const nextStatus: RefreshStatus = {
        criteriaVersion: result.criteriaVersion,
        state: result.state,
        stale: result.remainingCount > 0 || result.state !== 'ready',
        assessedCount: result.assessedCount,
        remainingCount: result.remainingCount,
        startedAt: previousStatus?.startedAt ?? null,
        finishedAt: result.finishedAt,
        errorCode: null,
      };
      advanceCompatibilityGeneration(client, result.criteriaVersion);
      await cancelCompatibilityQueries(client);
      client.setQueryData(['compatibility-status'], nextStatus);
      client.setQueryData<CriteriaResponse>(['criteria'], (old) =>
        old && old.version === nextStatus.criteriaVersion
          ? { ...old, assessment: nextStatus }
          : old,
      );
      await invalidateCompatibilityQueries(client);
      callbacks?.onSuccess?.(result);
    },
    onError: async (error) => {
      advanceCompatibilityGeneration(client);
      await callbacks?.onError?.(error);
    },
  });
}

export function useCompatibilityRefresh() {
  const client = useQueryClient();

  async function reloadCriteriaSnapshot(): Promise<CriteriaResponse> {
    const fresh = await runCoordinatedRead(client, {
      fetcher: getCriteria,
      getVersion: (data) => data.version,
    });
    advanceCompatibilityGeneration(client, fresh.version);
    await cancelCompatibilityQueries(client);
    client.setQueryData(['criteria'], fresh);
    client.setQueryData(['compatibility-status'], fresh.assessment);
    await invalidateCompatibilityViews(client);
    return fresh;
  }

  async function consultLatestStatus(): Promise<RefreshStatus> {
    const latest = await client.fetchQuery({
      queryKey: ['compatibility-status'],
      queryFn: ({ signal }) =>
        runCoordinatedRead(client, {
          signal,
          fetcher: getCompatibilityStatus,
          getVersion: (data) => data.criteriaVersion,
        }),
      staleTime: 0,
      retry: false,
    });
    advanceCompatibilityGeneration(client, latest.criteriaVersion);
    client.setQueryData<CriteriaResponse>(['criteria'], (old) =>
      old && old.version === latest.criteriaVersion ? { ...old, assessment: latest } : old,
    );
    if (latest.state === 'ready' && !latest.stale) {
      await cancelCompatibilityQueries(client);
      client.setQueryData(['compatibility-status'], latest);
      await invalidateCompatibilityViews(client);
    }
    return latest;
  }

  async function invalidateAfterOpportunityChange(): Promise<void> {
    advanceCompatibilityGeneration(client);
    await cancelCompatibilityQueries(client);
    await invalidateCompatibilityQueries(client);
    await client.invalidateQueries({ queryKey: ['applications'] });
  }

  return {
    reloadCriteriaSnapshot,
    consultLatestStatus,
    invalidateAfterOpportunityChange,
  };
}

/**
 * Salvar o perfil muda cargo e tecnologias dos critérios e sobe a versão deles (api.md US006),
 * então segue a mesma coordenação de leituras de `useSaveCriteriaMutation`.
 */
export function useSaveProfileMutation(callbacks?: {
  onSuccess?: (saved: ProfileView) => void;
  onError?: (error: Error) => void;
}) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (payload: SavePayload) => runTrackedMutation(client, () => saveProfile(payload)),
    onSuccess: async (saved) => {
      advanceCompatibilityGeneration(client, saved.assessment.criteriaVersion);
      await cancelCompatibilityQueries(client);
      client.setQueryData(PROFILE_KEY, saved);
      client.setQueryData(['compatibility-status'], saved.assessment);
      await invalidateCompatibilityQueries(client);
      callbacks?.onSuccess?.(saved);
    },
    onError: (error) => {
      advanceCompatibilityGeneration(client);
      callbacks?.onError?.(error);
    },
  });
}
