import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '../api/client';

export const SESSION_KEY = ['session'] as const;

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Erros 4xx não melhoram repetindo.
        retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 2,
        refetchOnWindowFocus: true,
      },
    },
  });
}
