import type { CSSProperties } from 'react';
import { Link } from 'react-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { describeCompatibility } from '../api/compatibility';
import { post, type Opportunity } from '../api/panel';
import { companyColor, companyInitials, salaryText } from '../lib/opportunity';
import { formatAsOf, ZONE } from '../lib/time';
import { MutationFeedback } from './Panel';
import { SalaryEstimate } from './SalaryEstimate';

const MODES = {
  remote: 'Remoto',
  hybrid: 'Híbrido',
  onsite: 'Presencial',
  unknown: 'Modalidade a confirmar',
};
export function OpportunityMeta({ item }: { item: Opportunity }) {
  return (
    <>
      <p className="meta">
        {MODES[item.workMode]} · {item.location || 'Local a confirmar'} ·{' '}
        {item.contractType === 'unknown' ? 'Contrato a confirmar' : item.contractType.toUpperCase()}
      </p>
      <p className="muted">
        {item.publishedAt ? `Publicada em ${item.publishedAt}` : 'Data de publicação desconhecida'}
      </p>
      <p className="salary">{salaryText(item) ?? 'Salário a confirmar'}</p>
    </>
  );
}

const TIERS = { priority: 'prioritária', suggested: 'sugerida' };

/** "inscrições até hoje", "inscrições até amanhã (15/11)" ou "inscrições até 20/11". */
function deadlineText(at: string, now = new Date()) {
  const date = new Date(at.length === 10 ? `${at}T23:59:59-03:00` : at);
  const key = (d: Date) => d.toLocaleDateString('en-CA', { timeZone: ZONE });
  const day = date.toLocaleDateString('pt-BR', {
    timeZone: ZONE,
    day: '2-digit',
    month: '2-digit',
  });
  if (date.getTime() < now.getTime()) return `inscrições encerradas em ${day}?`;
  if (key(date) === key(now)) return 'inscrições até hoje';
  if (key(date) === key(new Date(now.getTime() + 86_400_000)))
    return `inscrições até amanhã (${day})`;
  return `inscrições até ${day}`;
}

export interface CardLayout {
  flex?: string;
  rotate?: number;
  titleSize?: number;
}

export function OpportunityCard({
  item,
  discarded = false,
  layout,
  headingLevel = 2,
}: {
  item: Opportunity;
  discarded?: boolean;
  layout?: CardLayout;
  /** 3 quando o cartão fica sob um h2 de seção (página Hoje). */
  headingLevel?: 2 | 3;
}) {
  const client = useQueryClient();
  const action = useMutation({
    mutationFn: (kind: 'applied' | 'discard' | 'restore') =>
      post(`/api/opportunities/${encodeURIComponent(item.id)}/${kind}`),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['opportunities'] });
      void client.invalidateQueries({ queryKey: ['home'] });
      void client.invalidateQueries({ queryKey: ['applications'] });
    },
  });
  const Heading = headingLevel === 3 ? 'h3' : 'h2';
  const color = companyColor(item.companyName);
  const salary = salaryText(item);
  const place = [
    item.location,
    item.workMode === 'unknown' ? null : MODES[item.workMode].toLowerCase(),
  ]
    .filter(Boolean)
    .join(' · ');
  const compatibility = item.compatibility ? describeCompatibility(item.compatibility) : null;
  const style = {
    '--c': color,
    flex: layout?.flex,
    transform: layout?.rotate ? `rotate(${layout.rotate}deg)` : undefined,
  } as CSSProperties;
  return (
    <article className="job-card sk" style={style}>
      <div className="card-top">
        <span className="company-mark hatch" aria-hidden="true">
          <span>{companyInitials(item.companyName)}</span>
        </span>
        <span className="company-id">
          <span className="company-name">{item.companyName.trim() || 'Empresa não informada'}</span>
          {item.companyTier && <span className="company-tier mono">{TIERS[item.companyTier]}</span>}
        </span>
      </div>
      <Heading
        className="job-title"
        style={layout?.titleSize ? { fontSize: layout.titleSize } : undefined}
      >
        <Link to={`/vagas/${item.id}`}>{item.title}</Link>
      </Heading>
      {compatibility && (
        <p className={`job-compatibility mono compatibility-${compatibility.tone}`}>
          {compatibility.label}
        </p>
      )}
      <p className="job-place">
        {place || 'Local a confirmar'}
        {item.contractType !== 'unknown' && ` · ${item.contractType.toUpperCase()}`}
      </p>
      {salary ? (
        <p className="job-salary mono">
          ✓ {salary} <span className="muted">declarado</span>
        </p>
      ) : (
        <p className="job-salary-missing hatch mono">
          <span>salário a confirmar</span>
        </p>
      )}
      <SalaryEstimate item={item} />
      {item.glassdoorUrl && (
        <a href={item.glassdoorUrl} target="_blank" rel="noopener noreferrer" className="small">
          Ver no Glassdoor ↗
        </a>
      )}
      {item.fitReasons.length > 0 && Boolean(item.compatibility?.current) && (
        <p className="job-reason">“{item.fitReasons[0]}”</p>
      )}
      {item.deadlineAt && (
        <p className="job-deadline hl">
          <span aria-hidden="true">⏰ </span>
          {deadlineText(item.deadlineAt)}
        </p>
      )}
      <p className="job-source mono">
        {item.publishedAt ? `Publicada em ${item.publishedAt}` : 'Data de publicação desconhecida'}
      </p>
      {item.source && (
        <p className="job-source mono">
          fonte: {item.source.source} · consultado{' '}
          <time dateTime={item.source.fetchedAt}>{formatAsOf(item.source.fetchedAt)}</time>
        </p>
      )}
      <div className="card-actions">
        {discarded ? (
          <button
            className="btn bt bt-sec dsh"
            disabled={action.isPending}
            onClick={() => action.mutate('restore')}
          >
            Restaurar
          </button>
        ) : (
          <>
            <button
              className="btn bt bt-pri"
              disabled={action.isPending}
              onClick={() => action.mutate('applied')}
            >
              Me candidatei
            </button>
            <button
              className="btn bt bt-sec dsh"
              disabled={action.isPending}
              onClick={() => action.mutate('discard')}
            >
              Descartar
            </button>
          </>
        )}
        {/* Atalho visual; o título já é o link acessível para o mesmo destino. */}
        <Link className="btn card-link" to={`/vagas/${item.id}`} aria-hidden="true" tabIndex={-1}>
          ver ↗
        </Link>
      </div>
      <MutationFeedback
        error={action.error}
        success={action.isSuccess && action.variables === 'applied' && 'Candidatura registrada.'}
      />
    </article>
  );
}
