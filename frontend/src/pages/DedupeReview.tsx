import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { api } from '../api/client';
import { post, type DedupeCandidate, type ListResult } from '../api/panel';
import { Empty, MutationFeedback, PageHeader, QueryState } from '../components/Panel';
import { OpportunityMeta, OpportunityHeading } from '../components/OpportunityCard';
import { SurfaceStateBlock } from '../components/SurfaceState';

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
      <Link to="/revisao" className="back-link">
        ← Fila de revisão
      </Link>
      <PageHeader title="Possíveis duplicatas" eyebrow="Duas fontes, a mesma vaga?" />
      <p>Compare os anúncios antes de unir. Uma semelhança nunca causa fusão automática.</p>
      <QueryState pending={query.isPending} error={query.error} retry={query.refetch} />
      <MutationFeedback
        error={resolve.error}
        success={resolve.isSuccess && 'Decisão registrada.'}
      />
      {query.data?.items.map((item) => (
        <article key={item.id} className="stack">
          <SurfaceStateBlock
            kind="pending_review"
            subLabel="DUPLICATA"
            title={`Sinal encontrado: ${item.signal}`}
          >
            <div className="detail-grid" style={{ marginTop: 8 }}>
              {[item.opportunityA, item.opportunityB].map((job) => (
                <section className="note-card sk stack" key={job.id}>
                  <OpportunityHeading item={job} />
                  <OpportunityMeta item={job} />
                </section>
              ))}
            </div>
            <div className="actions" style={{ marginTop: 12 }}>
              <button
                className="btn bt bt-pri"
                disabled={resolve.isPending}
                onClick={() => resolve.mutate({ id: item.id, action: 'merge' })}
              >
                São a mesma vaga: unir
              </button>
              <button
                className="btn bt bt-sec dsh"
                disabled={resolve.isPending}
                onClick={() => resolve.mutate({ id: item.id, action: 'reject' })}
              >
                São vagas diferentes
              </button>
            </div>
          </SurfaceStateBlock>
        </article>
      ))}
      {query.data?.items.length === 0 && <Empty>Nenhuma possível duplicata para revisar.</Empty>}
    </div>
  );
}
