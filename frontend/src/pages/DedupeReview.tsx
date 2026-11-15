import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { api } from '../api/client';
import { post, type DedupeCandidate, type ListResult } from '../api/panel';
import { Empty, MutationFeedback, PageHeader, QueryState } from '../components/Panel';
import { OpportunityMeta } from '../components/OpportunityCard';

export function DedupeReview() {
  const client = useQueryClient();
  const query = useQuery({
    queryKey: ['dedupe'],
    queryFn: ({ signal }) => api<ListResult<DedupeCandidate>>('/api/dedupe-candidates', { signal }),
  });
  const resolve = useMutation({
    mutationFn: ({ id, action }: { id: string; action: string }) =>
      post(`/api/dedupe-candidates/${encodeURIComponent(id)}/${action}`),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['dedupe'] });
      void client.invalidateQueries({ queryKey: ['opportunities'] });
      void client.invalidateQueries({ queryKey: ['reviews'] });
      void client.invalidateQueries({ queryKey: ['home'] });
    },
  });
  return (
    <div className="stack">
      <Link to="/revisao">← Fila de revisão</Link>
      <PageHeader title="Possíveis duplicatas" eyebrow="Duas fontes, a mesma vaga?" />
      <p>Compare os anúncios antes de unir. Uma semelhança nunca causa fusão automática.</p>
      <QueryState pending={query.isPending} error={query.error} retry={query.refetch} />
      <MutationFeedback
        error={resolve.error}
        success={resolve.isSuccess && 'Decisão registrada.'}
      />
      {query.data?.items.map((item) => (
        <article key={item.id} className="note-card dsh stack">
          <p className="fit-note">Sinal encontrado: {item.signal}</p>
          <div className="detail-grid">
            {[item.opportunityA, item.opportunityB].map((job) => (
              <section className="stack" key={job.id}>
                <p className="eyebrow">{job.companyName}</p>
                <h2>
                  <Link to={`/vagas/${job.id}`}>{job.title}</Link>
                </h2>
                <OpportunityMeta item={job} />
              </section>
            ))}
          </div>
          <div className="actions">
            <button
              className="btn bt-pri"
              disabled={resolve.isPending}
              onClick={() => resolve.mutate({ id: item.id, action: 'merge' })}
            >
              São a mesma vaga: unir
            </button>
            <button
              className="btn bt-sec dsh"
              disabled={resolve.isPending}
              onClick={() => resolve.mutate({ id: item.id, action: 'reject' })}
            >
              São vagas diferentes
            </button>
          </div>
        </article>
      ))}
      {query.data?.items.length === 0 && <Empty>Nenhuma possível duplicata para revisar.</Empty>}
    </div>
  );
}
