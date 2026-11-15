import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { api } from '../api/client';
import type { SalaryObservation } from '../api/panel';
import { QueryState } from '../components/Panel';
import { SalaryRange } from '../components/SalaryEstimate';

export function SalaryReferences() {
  const query = useQuery({
    queryKey: ['salary-references'],
    queryFn: () => api<{ items: SalaryObservation[] }>('/api/salary-estimates'),
  });
  return (
    <main>
      <Link to="/vagas">Voltar às vagas</Link>
      <h1>Referências salariais</h1>
      <p>
        Consultas por empresa e cargo. Dados sem regime ou local compatível ficam disponíveis para
        conferência e não são aplicados automaticamente às vagas.
      </p>
      <QueryState pending={query.isPending} error={query.error} retry={query.refetch} />
      {query.data?.items.length === 0 && <p>Nenhuma referência coletada.</p>}
      {query.data?.items.map((e) => (
        <article className="sk" key={e.id} style={{ padding: 20, marginTop: 20 }}>
          <h2>
            {e.criteria.company || 'Mercado'} — {e.criteria.role} {e.criteria.seniority}
          </h2>
          <SalaryRange estimate={e} />
          <p>
            {e.criteria.location} ·{' '}
            {e.criteria.contractType === 'unknown'
              ? 'Regime não informado pela fonte'
              : e.criteria.contractType.toUpperCase()}
          </p>
          {e.sampleSize != null && <p>{e.sampleSize} salários informados pela fonte</p>}
          {e.sourceUpdatedOn && <p>Atualização na fonte: {e.sourceUpdatedOn}</p>}
          <a href={e.sourceUrl} target="_blank" rel="noopener noreferrer">
            Ver no Glassdoor ↗
          </a>
        </article>
      ))}
    </main>
  );
}
