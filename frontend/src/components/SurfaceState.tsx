import type { ReactNode } from 'react';
import { Link } from 'react-router';

export type SurfaceStateKind =
  'empty' | 'loading' | 'partial' | 'error' | 'stale' | 'pending_review';

const STATE_META: Record<
  SurfaceStateKind,
  { label: string; className: string; role?: 'status' | 'alert' }
> = {
  empty: { label: 'VAZIO', className: 'surface-state surface-empty sk' },
  loading: { label: 'CARREGANDO', className: 'surface-state surface-loading dsh', role: 'status' },
  partial: { label: 'PARCIAL', className: 'surface-state surface-partial' },
  error: { label: 'FALHA', className: 'surface-state surface-error dsh', role: 'alert' },
  stale: { label: 'DESATUALIZADO', className: 'surface-state surface-stale hatch' },
  pending_review: {
    label: 'PENDENTE DE REVISÃO',
    className: 'surface-state surface-pending-review hl',
  },
};

export interface SurfaceAction {
  label: string;
  to?: string;
  onClick?: () => void;
  disabled?: boolean;
}

export function SurfaceStateBlock({
  kind,
  title,
  body,
  subLabel,
  progressPercent,
  coverage,
  action,
  children,
  className = '',
  titleAs: TitleTag = 'p',
}: {
  kind: SurfaceStateKind;
  title?: ReactNode;
  body?: ReactNode;
  subLabel?: string;
  progressPercent?: number;
  coverage?: { respondedCount: number; totalCount: number };
  action?: SurfaceAction;
  children?: ReactNode;
  className?: string;
  titleAs?: 'p' | 'h2' | 'h3';
}) {
  const meta = STATE_META[kind];
  const isStale = kind === 'stale';
  const lineClass = isStale ? 'surface-stale-line' : '';
  const pct =
    typeof progressPercent === 'number' && Number.isFinite(progressPercent)
      ? Math.max(0, Math.min(100, Math.round(progressPercent)))
      : undefined;

  return (
    <div
      className={`${meta.className} ${className}`.trim()}
      role={meta.role}
      data-surface-state={kind}
    >
      <span className={`surface-label ${lineClass}`.trim()}>
        {meta.label}
        {subLabel ? ` · ${subLabel}` : ''}
      </span>
      {title && <TitleTag className={`surface-title ${lineClass}`.trim()}>{title}</TitleTag>}
      {kind === 'partial' && coverage && (
        <p className="surface-title">
          {coverage.respondedCount} de {coverage.totalCount} fontes consultadas
        </p>
      )}
      {kind === 'loading' && pct !== undefined && (
        <span className="surface-progress-track" aria-hidden="true">
          <span className="surface-progress-fill hatch" style={{ width: `${pct}%` }} />
        </span>
      )}
      {kind === 'loading' && pct === undefined && (
        <progress className="surface-progress-track hatch" aria-label="Em andamento" />
      )}
      {body && <div className={`surface-body ${lineClass}`.trim()}>{body}</div>}
      {children}
      {action && (
        <div className="surface-actions">
          {action.to ? (
            <Link className={`btn bt bt-sec dsh ${lineClass}`.trim()} to={action.to}>
              {action.label}
            </Link>
          ) : (
            <button
              type="button"
              className={`btn bt bt-sec dsh ${lineClass}`.trim()}
              disabled={action.disabled}
              onClick={action.onClick}
            >
              {action.label}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
