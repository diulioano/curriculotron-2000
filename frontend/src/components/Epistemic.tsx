import type { ReactNode } from 'react';
import type { OpportunityCorrectionEntry, OpportunitySource } from '../api/panel';
import { formatAsOf } from '../lib/time';

const safeUrl = (url: string) => /^https?:\/\//i.test(url);

export type ProfileMatchState = 'matched' | 'missing_from_profile' | null;

export function FactItem({
  value,
  bold = false,
  profileMatch = null,
  sourceDeclared = true,
  className = '',
}: {
  value: ReactNode;
  bold?: boolean;
  profileMatch?: ProfileMatchState;
  sourceDeclared?: boolean;
  className?: string;
}) {
  return (
    <span className={`${bold ? 'ep-fact' : 'ep-fact-inline'} ${className}`.trim()}>
      {sourceDeclared && <span aria-hidden="true">✓ </span>}
      <span>{value}</span>
      {profileMatch === 'matched' && <span className="ep-match ep-match--ok"> você tem</span>}
      {profileMatch === 'missing_from_profile' && (
        <span className="ep-match ep-match--missing"> não está no perfil</span>
      )}
    </span>
  );
}

export function InferenceBox({
  value,
  reason,
  inline = false,
  children,
  className = '',
}: {
  value?: ReactNode;
  reason?: string | null;
  inline?: boolean;
  children?: ReactNode;
  className?: string;
}) {
  const effectiveReason = reason === null ? null : reason?.trim() || 'inferido pelo sistema';
  if (inline) {
    return (
      <span className={`ep-inference-inline dsh-b ${className}`.trim()}>
        ~ {value ?? children}
        {effectiveReason && <span className="ep-inference-reason"> ({effectiveReason})</span>}
      </span>
    );
  }
  return (
    <div className={`ep-inference dsh ${className}`.trim()}>
      {value !== undefined ? (
        <span>
          ~ {value}
          {effectiveReason && <span className="ep-inference-reason"> ({effectiveReason})</span>}
        </span>
      ) : (
        children
      )}
    </div>
  );
}

export function AbsentValue({
  children,
  surface = 'card',
  spanClassName,
  className = '',
}: {
  children: ReactNode;
  surface?: 'card' | 'paper';
  spanClassName?: string;
  className?: string;
}) {
  return (
    <span
      className={`ep-absent hatch ${surface === 'paper' ? 'ep-absent--paper' : ''} ${className}`.trim()}
    >
      <span className={spanClassName}>{children}</span>
    </span>
  );
}

export function CorrectedField({
  corrections,
  fields,
  children,
}: {
  corrections: OpportunityCorrectionEntry[];
  fields: string[];
  children: ReactNode;
}) {
  const historyByField = fields.map((field) =>
    corrections.filter((entry) => entry.fieldName === field),
  );
  const latestByField = historyByField.map((history) =>
    history.reduce<OpportunityCorrectionEntry | null>(
      (latest, entry) =>
        !latest || Date.parse(entry.correctedAt) > Date.parse(latest.correctedAt) ? entry : latest,
      null,
    ),
  );
  const originalByField = historyByField.map((history) =>
    history.reduce<OpportunityCorrectionEntry | null>(
      (original, entry) =>
        !original || Date.parse(entry.correctedAt) < Date.parse(original.correctedAt)
          ? entry
          : original,
      null,
    ),
  );
  const latest = latestByField
    .filter((entry): entry is OpportunityCorrectionEntry => entry !== null)
    .reduce<OpportunityCorrectionEntry | null>(
      (current, entry) =>
        !current || Date.parse(entry.correctedAt) > Date.parse(current.correctedAt)
          ? entry
          : current,
      null,
    );
  if (!latest) return <>{children}</>;

  const originals = originalByField
    .filter((entry): entry is OpportunityCorrectionEntry => entry !== null)
    .map((entry) => entry.previousValue)
    .filter((value): value is string => value !== null && value !== '');
  const fieldLabels: Record<string, string> = {
    salary_min: 'salário mínimo',
    salary_max: 'salário máximo',
    salary_unit: 'unidade salarial',
  };
  return (
    <span className="ep-corrected-field" style={{ display: 'block' }}>
      {children}
      {latestByField.map((entry, index) =>
        entry ? (
          <span className="ep-corrected-by" style={{ display: 'block' }} key={fields[index]}>
            {fields.length > 1 && fieldLabels[fields[index]]
              ? `${fieldLabels[fields[index]]}: `
              : ''}
            corrigido por {entry.authorLabel.replace(/^corrigido por\s+/i, '')} ·{' '}
            <time dateTime={entry.correctedAt}>{formatAsOf(entry.correctedAt)}</time>
          </span>
        ) : null,
      )}
      {originals.length > 0 && (
        <span className="ep-source-original" style={{ display: 'block' }}>
          valor original da fonte: {originals.join(' · ')}
        </span>
      )}
    </span>
  );
}

export function EvidenceBlock({
  sources,
  publishedAt,
  corrections,
  onCorrect,
  correctOpen = false,
  heading = 'Evidência',
  note = 'Abrir a fonte não registra candidatura.',
}: {
  sources: OpportunitySource[];
  publishedAt?: string | null;
  corrections?: OpportunityCorrectionEntry[];
  onCorrect?: () => void;
  correctOpen?: boolean;
  heading?: string;
  note?: string;
}) {
  const latestCorrection =
    corrections?.reduce<OpportunityCorrectionEntry | null>(
      (latest, entry) =>
        !latest || Date.parse(entry.correctedAt) > Date.parse(latest.correctedAt) ? entry : latest,
      null,
    ) ?? null;

  return (
    <section className="ep-evidence dsh-t" aria-labelledby="evidencia">
      <h2 id="evidencia" style={{ fontFamily: 'var(--font-hand)', fontSize: 18, fontWeight: 700 }}>
        {heading}
      </h2>
      {sources.length ? (
        sources.map((source, index) => (
          <div key={`${source.source}-${index}`} className="ep-evidence-row">
            {safeUrl(source.url) ? (
              <a href={source.url} target="_blank" rel="noopener noreferrer">
                fonte: {source.source} ↗
              </a>
            ) : (
              <span>fonte: {source.source} · link indisponível</span>
            )}
            <span>
              consultado: <time dateTime={source.fetchedAt}>{formatAsOf(source.fetchedAt)}</time>
            </span>
          </div>
        ))
      ) : (
        <span>fonte: nenhuma fonte registrada</span>
      )}
      <CorrectedField corrections={corrections ?? []} fields={['published_at']}>
        <div>
          {publishedAt ? (
            <span>publicada: {publishedAt}</span>
          ) : (
            <span>
              publicada: <AbsentValue surface="paper">data de publicação desconhecida</AbsentValue>
            </span>
          )}
        </div>
      </CorrectedField>
      {latestCorrection && (
        <div className="ep-evidence-correction" data-testid="latest-correction">
          {latestCorrection.authorLabel.toLowerCase().startsWith('corrigido por')
            ? latestCorrection.authorLabel
            : `corrigido por ${latestCorrection.authorLabel}`}{' '}
          ·{' '}
          <time dateTime={latestCorrection.correctedAt}>
            {formatAsOf(latestCorrection.correctedAt)}
          </time>
          {latestCorrection.reason ? ` — ${latestCorrection.reason}` : ''}
        </div>
      )}
      {onCorrect && (
        <button
          type="button"
          className="ep-evidence-trigger"
          aria-expanded={correctOpen}
          onClick={onCorrect}
        >
          {correctOpen ? 'Fechar correção' : 'Corrigir algum dado →'}
        </button>
      )}
      {note && <span className="muted">{note}</span>}
    </section>
  );
}
