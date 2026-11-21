import { formatAsOf } from '../lib/time';
import { AbsentValue } from './Epistemic';
import { SurfaceStateBlock } from './SurfaceState';

/**
 * "Dados de <data>": mostra a última atualização bem-sucedida real (FR-016, US5-4).
 * `null` nunca vira "agora" — aparece como "sem atualização bem-sucedida ainda".
 */

interface Props {
  /** ISO-8601 da última atualização real, ou null. */
  at: string | null | undefined;
  /** Referência para "hoje"/"ontem" (padrão: agora). */
  now?: Date;
  prefix?: string;
  stale?: boolean;
  onRefresh?: () => void;
}

export function DataAsOf({ at, now, prefix = 'dados de', stale = false, onRefresh }: Props) {
  if (!at) {
    return (
      <span className="mono muted" data-as-of="none">
        <AbsentValue surface="paper">sem atualização bem-sucedida ainda</AbsentValue>
      </span>
    );
  }
  if (stale) {
    return (
      <SurfaceStateBlock
        kind="stale"
        title={
          <>
            Dados de <time dateTime={at}>{formatAsOf(at, now)}</time>
          </>
        }
        body="Pode não refletir o estado atual."
        action={onRefresh ? { label: 'Atualizar →', onClick: onRefresh } : undefined}
      />
    );
  }
  return (
    <span className="mono muted" data-as-of={at}>
      {prefix}{' '}
      <time dateTime={at} style={{ color: 'var(--ink)', fontWeight: 700 }}>
        {formatAsOf(at, now)}
      </time>
    </span>
  );
}
