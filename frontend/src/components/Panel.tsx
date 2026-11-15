import type { ReactNode } from 'react';
import { ApiError } from '../api/client';

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
  if (pending)
    return (
      <p className="empty mono" role="status">
        Carregando…
      </p>
    );
  if (!error) return null;
  return (
    <div className="stack">
      <div className="alert dsh" role="alert">
        {error instanceof ApiError && error.status === 404
          ? 'Esta área ainda não está disponível no serviço local.'
          : error.message || 'Não foi possível carregar os dados.'}
      </div>
      <div>
        <button className="btn bt-sec dsh" onClick={() => retry()}>
          Tentar de novo
        </button>
      </div>
    </div>
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
  return <div className="empty dsh">{children}</div>;
}
