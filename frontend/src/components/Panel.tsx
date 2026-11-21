import type { ReactNode } from 'react';
import { ApiError } from '../api/client';
import { SurfaceStateBlock } from './SurfaceState';

export function PageHeader({
  title,
  eyebrow,
  children,
}: {
  title: string;
  eyebrow?: string;
  children?: ReactNode;
}) {
  return (
    <header className="page-heading">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1>{title}</h1>
      </div>
      <div className="actions">{children}</div>
    </header>
  );
}

export function QueryState({
  pending,
  error,
  retry,
}: {
  pending: boolean;
  error: Error | null;
  retry: () => unknown;
}) {
  if (pending) {
    return <SurfaceStateBlock kind="loading" title="Carregando dados…" />;
  }
  if (!error) return null;
  const message =
    error instanceof ApiError && error.status === 404
      ? 'Esta área ainda não está disponível no serviço local.'
      : error.message || 'Não foi possível carregar os dados.';
  return (
    <SurfaceStateBlock
      kind="error"
      title="Não foi possível carregar"
      body={message}
      action={{ label: 'Tentar de novo', onClick: () => void retry() }}
    />
  );
}

export function MutationFeedback({
  error,
  success,
}: {
  error: Error | null;
  success?: string | false;
}) {
  return (
    <>
      {error && (
        <p className="alert dsh" role="alert">
          {error.message}
        </p>
      )}
      {success && (
        <p className="notice dsh" role="status">
          ✓ {success}
        </p>
      )}
    </>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <SurfaceStateBlock kind="empty" title={children} />;
}
