import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { api } from '../api/client';
import { post, type ListResult, type ReviewItem } from '../api/panel';
import { Empty, MutationFeedback, PageHeader, QueryState } from '../components/Panel';

const KINDS: Record<string, string> = {
  ambiguous_mail: 'Mensagem ambígua',
  dedupe_candidate: 'Possível duplicata',
  conflict: 'Informações conflitantes',
  bot_config: 'Configuração de bot',
  salary_ambiguous: 'Salário a confirmar',
};
function ReviewCard({ item }: { item: ReviewItem }) {
  const client = useQueryClient();
  const [resolution, setResolution] = useState('');
  const save = useMutation({
    mutationFn: () =>
      post(`/api/review-items/${encodeURIComponent(item.id)}/resolve`, {
        resolution: resolution.trim(),
      }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['reviews'] });
      void client.invalidateQueries({ queryKey: ['home'] });
    },
  });
  function submit(e: FormEvent) {
    e.preventDefault();
    save.mutate();
  }
  return (
    <article className="note-card dsh stack">
      <p className="eyebrow">{KINDS[item.kind] ?? item.kind}</p>
      <h2>{item.summary}</h2>
      {item.kind === 'dedupe_candidate' ? (
        <Link to="/revisao/duplicatas">Comparar anúncios →</Link>
      ) : item.kind === 'bot_config' ? (
        <Link to="/bots">Revisar configuração nos bots →</Link>
      ) : (
        <form className="stack" onSubmit={submit}>
          <div className="field">
            <label htmlFor={`resolution-${item.id}`}>Sua decisão</label>
            <textarea
              id={`resolution-${item.id}`}
              className="input"
              required
              value={resolution}
              onChange={(e) => setResolution(e.target.value)}
            />
          </div>
          <div>
            <button
              type="submit"
              className="btn bt-pri"
              disabled={save.isPending || !resolution.trim()}
            >
              Registrar resolução
            </button>
          </div>
          <MutationFeedback error={save.error} />
        </form>
      )}
    </article>
  );
}
export function ReviewQueue() {
  const query = useQuery({
    queryKey: ['reviews'],
    queryFn: ({ signal }) => api<ListResult<ReviewItem>>('/api/review-items', { signal }),
  });
  return (
    <div className="stack">
      <PageHeader title="Fila de revisão" eyebrow="Aqui, a decisão é sua">
        <Link className="btn bt-sec dsh" to="/revisao/duplicatas">
          Comparar duplicatas ↗
        </Link>
      </PageHeader>
      <p className="muted">Informações ambíguas ficam pendentes até você revisar.</p>
      <QueryState pending={query.isPending} error={query.error} retry={query.refetch} />
      {query.data?.items.map((item) => (
        <ReviewCard key={item.id} item={item} />
      ))}
      {query.data?.items.length === 0 && <Empty>Tudo revisado. Nenhuma decisão pendente.</Empty>}
    </div>
  );
}
