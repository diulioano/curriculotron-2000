import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { api } from '../api/client';
import type { SalaryObservation } from '../api/panel';
import { AbsentValue } from '../components/Epistemic';
import { Empty, PageHeader, QueryState } from '../components/Panel';
import { SalaryRange } from '../components/SalaryEstimate';
import { SurfaceStateBlock } from '../components/SurfaceState';
import { formatAsOf } from '../lib/time';

export function SalaryReferences() {
  const query = useQuery({
    queryKey: ['salary-references'],
    queryFn: ({ signal }) =>
      api<{ items: SalaryObservation[] }>('/api/salary-estimates', { signal }),
  });
  return (
    <div className="stack">
      <Link to="/vagas" className="back-link">
        ← Voltar às vagas
      </Link>
      <PageHeader title="Referências salariais" eyebrow="Mercado e empresa">
        <button
          type="button"
          className="btn bt bt-sec dsh"
          disabled={query.isFetching}
          onClick={() => void query.refetch()}
        >
          {query.isFetching ? 'Consultando…' : 'Atualizar lista'}
        </button>
      </PageHeader>
      <p className="muted">
        Consultas por empresa e cargo. Dados sem regime ou local compatível ficam disponíveis para
        conferência e não são aplicados automaticamente às vagas.
      </p>
      <QueryState pending={query.isPending} error={query.error} retry={query.refetch} />
      {query.data?.items.length === 0 && <Empty>Nenhuma referência coletada.</Empty>}
      {query.data?.items.map((e) => (
        <article className="note-card sk stack" key={e.id}>
          <h2>
            {e.criteria.company || 'Mercado'} — {e.criteria.role} {e.criteria.seniority}
          </h2>
          {e.stale && (
            <SurfaceStateBlock
              kind="stale"
              title={
                <>
                  Referência de <time dateTime={e.consultedAt}>{formatAsOf(e.consultedAt)}</time>
                </>
              }
              body="A validade desta consulta expirou; pode não refletir valores recentes. Esta lista só exibe consultas já coletadas; para renovar, atualize a estimativa a partir de uma vaga."
              action={{ label: 'Atualizar a partir de uma vaga →', to: '/vagas' }}
            />
          )}
          <SalaryRange estimate={e} />
          <p>
            {e.criteria.location} ·{' '}
            {e.criteria.contractType === 'unknown' ? (
              <AbsentValue>Regime não informado pela fonte</AbsentValue>
            ) : (
              e.criteria.contractType.toUpperCase()
            )}
          </p>
          {e.sampleSize != null && (
            <p className="mono small">{e.sampleSize} salários informados pela fonte</p>
          )}
          {e.sourceUpdatedOn && (
            <p className="mono small">Atualização na fonte: {e.sourceUpdatedOn}</p>
          )}
          <a href={e.sourceUrl} target="_blank" rel="noopener noreferrer">
            Ver no Glassdoor ↗
          </a>
        </article>
      ))}
    </div>
  );
}
