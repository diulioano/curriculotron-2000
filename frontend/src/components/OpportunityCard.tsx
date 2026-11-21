import type { CSSProperties } from 'react';
import { Link } from 'react-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { describeCompatibility } from '../api/compatibility';
import { post, type Opportunity } from '../api/panel';
import { companyColor, companyInitials, salaryText } from '../lib/opportunity';
import { formatAsOf, ZONE } from '../lib/time';
import { AbsentValue, FactItem, InferenceBox } from './Epistemic';
import { MutationFeedback } from './Panel';
import { SalaryEstimate } from './SalaryEstimate';

const MODES = {
  remote: 'Remoto',
  hybrid: 'Híbrido',
  onsite: 'Presencial',
  unknown: 'Modalidade a confirmar',
};

type FieldName = NonNullable<Opportunity['correctedFields']>[number];

function corrected(item: Opportunity, ...fields: FieldName[]) {
  return fields.some((field) => item.correctedFields?.includes(field));
}

function correctionLabel(item: Opportunity, ...fields: FieldName[]) {
  return corrected(item, ...fields) ? (
    <span className="ep-corrected-by"> corrigido por você</span>
  ) : null;
}

export function OpportunityHeading({ item }: { item: Opportunity }) {
  return (
    <>
      <p className="eyebrow">
        {item.companyName}
        {correctionLabel(item, 'company_name')}
      </p>
      <h2>
        <Link to={`/vagas/${item.id}`}>{item.title}</Link>
        {correctionLabel(item, 'title')}
      </h2>
    </>
  );
}
function SummarySalary({ item, compact = false }: { item: Opportunity; compact?: boolean }) {
  const salary = salaryText(item);
  const fields = ['salary_min', 'salary_max', 'salary_unit'] as const;
  return (
    <p className={compact ? 'salary' : salary ? 'job-salary mono' : 'job-salary-missing mono'}>
      {salary ? (
        <FactItem bold value={salary} sourceDeclared={!corrected(item, ...fields)} />
      ) : (
        <AbsentValue>{compact ? 'Salário a confirmar' : 'salário a confirmar'}</AbsentValue>
      )}
      {correctionLabel(item, ...fields)}
    </p>
  );
}

export function OpportunityMeta({ item }: { item: Opportunity }) {
  return (
    <>
      <p className="meta">
        {MODES[item.workMode]}
        {correctionLabel(item, 'work_mode')} · {item.location || 'Local a confirmar'}
        {correctionLabel(item, 'location')} ·{' '}
        {item.contractType === 'unknown' ? 'Contrato a confirmar' : item.contractType.toUpperCase()}
        {correctionLabel(item, 'contract_type')}
      </p>
      <p className="muted">
        {item.publishedAt ? (
          <FactItem
            value={`Publicada em ${item.publishedAt}`}
            sourceDeclared={!corrected(item, 'published_at')}
          />
        ) : (
          <AbsentValue>Data de publicação desconhecida</AbsentValue>
        )}
        {correctionLabel(item, 'published_at')}
      </p>
      <SummarySalary item={item} compact />
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
  const compatibility = item.compatibility ? describeCompatibility(item.compatibility) : null;
  const style = {
    '--c': color,
    flex: layout?.flex,
    transform: layout?.rotate ? `rotate(${layout.rotate}deg)` : undefined,
  } as CSSProperties;
  const companyTrimmed = item.companyName.trim();
  return (
    <article className="job-card sk" style={style}>
      <div className="card-top">
        <span className="company-mark hatch" aria-hidden="true">
          <span>{companyInitials(item.companyName)}</span>
        </span>
        <span className="company-id">
          <span className="company-name">
            {companyTrimmed ? companyTrimmed : <AbsentValue>Empresa não informada</AbsentValue>}
            {correctionLabel(item, 'company_name')}
          </span>
          {item.companyTier && <span className="company-tier mono">{TIERS[item.companyTier]}</span>}
        </span>
      </div>
      <Heading
        className="job-title"
        style={layout?.titleSize ? { fontSize: layout.titleSize } : undefined}
      >
        <Link to={`/vagas/${item.id}`}>{item.title}</Link>
        {correctionLabel(item, 'title')}
      </Heading>
      {compatibility && (
        <p className={`job-compatibility mono compatibility-${compatibility.tone}`}>
          {compatibility.label}
        </p>
      )}
      <p className="job-place">
        {item.location ? (
          <FactItem value={item.location} sourceDeclared={!corrected(item, 'location')} />
        ) : (
          <AbsentValue>Local a confirmar</AbsentValue>
        )}
        {correctionLabel(item, 'location')}
        {item.workMode !== 'unknown' && (
          <>
            {' · '}
            <FactItem
              value={MODES[item.workMode].toLowerCase()}
              sourceDeclared={!corrected(item, 'work_mode')}
            />
          </>
        )}
        {correctionLabel(item, 'work_mode')}
        {item.contractType !== 'unknown' && (
          <>
            {' · '}
            <FactItem
              value={item.contractType.toUpperCase()}
              sourceDeclared={!corrected(item, 'contract_type')}
            />
          </>
        )}
        {correctionLabel(item, 'contract_type')}
      </p>
      <SummarySalary item={item} />
      <SalaryEstimate item={item} />
      {(corrected(item, 'availability') || corrected(item, 'deadline_at')) && (
        <p className="job-source mono">
          {corrected(item, 'availability') && (
            <>
              Disponibilidade:{' '}
              {item.availability === 'open'
                ? 'aberta'
                : item.availability === 'closed'
                  ? 'encerrada'
                  : 'a confirmar'}{' '}
              {correctionLabel(item, 'availability')}
            </>
          )}
          {corrected(item, 'deadline_at') && (
            <>
              Prazo: {item.deadlineAt || 'não informado'} {correctionLabel(item, 'deadline_at')}
            </>
          )}
        </p>
      )}
      {item.glassdoorUrl && (
        <a href={item.glassdoorUrl} target="_blank" rel="noopener noreferrer" className="small">
          Ver no Glassdoor ↗
        </a>
      )}
      {item.fitReasons.length > 0 && Boolean(item.compatibility?.current) && (
        <InferenceBox
          inline
          className="job-reason"
          value={`“${item.fitReasons[0]}”`}
          reason={null}
        />
      )}
      {item.deadlineAt && (
        <p className="job-deadline hl">
          <span aria-hidden="true">⏰ </span>
          {deadlineText(item.deadlineAt)}
        </p>
      )}
      <div className="job-source mono">
        {item.publishedAt ? (
          <span>publicada: {item.publishedAt}</span>
        ) : (
          <AbsentValue>Data de publicação desconhecida</AbsentValue>
        )}
        {correctionLabel(item, 'published_at')}
      </div>
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
