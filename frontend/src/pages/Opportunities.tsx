import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { api } from '../api/client';
import { describeRefreshStatus } from '../api/compatibility';
import {
  useCompatibilityRefresh,
  useCoordinatedOpportunitiesQuery,
} from '../api/useCompatibilityRefresh';
import { post } from '../api/panel';
import { DataAsOf } from '../components/DataAsOf';
import { Empty, MutationFeedback, PageHeader, QueryState } from '../components/Panel';
import { OpportunityCard } from '../components/OpportunityCard';

const LISTS = [
  ['selection', 'Seleção do dia'],
  ['secondary', 'Lista secundária'],
  ['discarded', 'Descartadas'],
] as const;
export function Opportunities() {
  const [params] = useSearchParams();
  const list = LISTS.some(([key]) => key === params.get('list'))
    ? params.get('list')!
    : 'selection';
  const client = useQueryClient();
  const { invalidateAfterOpportunityChange } = useCompatibilityRefresh();
  const query = useCoordinatedOpportunitiesQuery(list);
  const routine = useQuery({
    queryKey: ['search-routine'],
    queryFn: () => api<{ enabled: boolean }>('/api/settings/search-routine'),
  });
  const changeRoutine = useMutation({
    mutationFn: (enabled: boolean) =>
      api('/api/settings/search-routine', { method: 'PUT', body: { enabled } }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['search-routine'] });
    },
  });
  const [manual, setManual] = useState(false);
  const [url, setUrl] = useState('');
  const [text, setText] = useState('');
  const [publishedFrom, setPublishedFrom] = useState('');
  const [publishedTo, setPublishedTo] = useState('');
  const search = useMutation({
    mutationFn: () =>
      post<{ taskRunId: string }>('/api/searches', {
        publishedFrom: publishedFrom || undefined,
        publishedTo: publishedTo || undefined,
      }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['task-runs'] });
    },
  });
  const add = useMutation({
    mutationFn: () =>
      post('/api/opportunities/manual', {
        url: url.trim() || undefined,
        text: text.trim() || undefined,
      }),
    onSuccess: () => {
      setUrl('');
      setText('');
      void invalidateAfterOpportunityChange();
    },
  });
  function submit(e: FormEvent) {
    e.preventDefault();
    if (url.trim() || text.trim()) add.mutate();
  }
  return (
    <div className="stack">
      <PageHeader title="Vagas" eyebrow="Oportunidades, sem o ruído">
        <button
          className="btn bt-sec dsh"
          onClick={() => setManual(!manual)}
          aria-expanded={manual}
        >
          + Adicionar vaga
        </button>
        <button
          className="btn bt bt-pri"
          disabled={
            search.isPending || (!!publishedFrom && !!publishedTo && publishedFrom > publishedTo)
          }
          onClick={() => search.mutate()}
        >
          {search.isPending ? 'Solicitando…' : 'Buscar vagas ↗'}
        </button>
      </PageHeader>
      <p className="muted">Até 10 novas escolhas por dia. As demais ficam na lista secundária.</p>
      <div className="note-card stack">
        <p>
          Buscar publicações dos últimos 30 dias, ou escolher outro período. Vagas sem data ficam
          identificadas.
        </p>
        <label htmlFor="published-from">Publicadas desde</label>
        <input
          className="input"
          id="published-from"
          type="date"
          value={publishedFrom}
          max={publishedTo || undefined}
          onChange={(e) => setPublishedFrom(e.target.value)}
        />
        <label htmlFor="published-to">Publicadas até</label>
        <input
          className="input"
          id="published-to"
          type="date"
          value={publishedTo}
          min={publishedFrom || undefined}
          onChange={(e) => setPublishedTo(e.target.value)}
        />
      </div>
      <MutationFeedback
        error={search.error}
        success={search.isSuccess && 'Busca solicitada. Acompanhe o resultado em Atividades.'}
      />
      {search.isSuccess && <Link to="/atividades">Acompanhar busca →</Link>}
      {manual && (
        <form className="note-card dsh stack" onSubmit={submit}>
          <h2>Encontrou uma vaga?</h2>
          <p>Cole o link ou a descrição. Adicionar uma vaga não registra candidatura.</p>
          <div className="field">
            <label htmlFor="job-url">Link da vaga</label>
            <input
              id="job-url"
              className="input"
              type="url"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://…"
            />
          </div>
          <div className="field">
            <label htmlFor="job-text">Descrição da vaga</label>
            <textarea
              id="job-text"
              className="input"
              rows={5}
              value={text}
              onChange={(e) => setText(e.target.value)}
            />
          </div>
          <div>
            <button
              type="submit"
              className="btn bt-pri"
              disabled={add.isPending || (!url.trim() && !text.trim())}
            >
              Adicionar à lista
            </button>
          </div>
          <MutationFeedback error={add.error} success={add.isSuccess && 'Vaga adicionada.'} />
        </form>
      )}
      <div className="note-card stack">
        <label>
          <input
            type="checkbox"
            checked={routine.data?.enabled ?? false}
            disabled={!routine.data || changeRoutine.isPending}
            onChange={(e) => changeRoutine.mutate(e.target.checked)}
          />{' '}
          Habilitar busca diária automática
        </label>
        <MutationFeedback error={routine.error || changeRoutine.error} />
      </div>
      <nav className="tabs dsh-b" aria-label="Listas de vagas">
        {LISTS.map(([key, label]) => (
          <Link
            key={key}
            className={list === key ? 'tab hl' : 'tab'}
            aria-current={list === key ? 'page' : undefined}
            to={`?list=${key}`}
          >
            {label}
          </Link>
        ))}
        <Link className="tab" to="/revisao/duplicatas">
          Possíveis duplicatas ↗
        </Link>
      </nav>
      <QueryState pending={query.isPending} error={query.error} retry={query.refetch} />
      {query.data &&
        (() => {
          const visibleItems =
            list === 'discarded'
              ? query.data.items
              : query.data.items.filter((item) => item.compatibility.current);
          const warning = describeRefreshStatus(query.data.assessment);
          return (
            <>
              <DataAsOf at={query.data.dataAsOf} />
              {warning && (
                <div className="notice dsh" role="status">
                  {warning} <Link to="/criterios">Ver critérios →</Link>
                </div>
              )}
              {visibleItems.length ? (
                <div className="jobs-grid">
                  {visibleItems.map((item) => (
                    <OpportunityCard key={item.id} item={item} discarded={list === 'discarded'} />
                  ))}
                </div>
              ) : (
                <Empty>
                  Nenhuma vaga nesta lista.
                  {query.data.assessment.stale &&
                    query.data.assessment.remainingCount > 0 &&
                    list !== 'discarded' && (
                      <>
                        {' '}
                        Vagas com avaliação pendente ou desatualizada ficam ocultas até a
                        atualização.
                      </>
                    )}
                </Empty>
              )}
            </>
          );
        })()}
    </div>
  );
}
