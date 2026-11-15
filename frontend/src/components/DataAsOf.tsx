import { formatAsOf } from '../lib/time';

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
}

export function DataAsOf({ at, now, prefix = 'dados de' }: Props) {
  if (!at) {
    return (
      <span className="mono muted" data-as-of="none">
        sem atualização bem-sucedida ainda
      </span>
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
