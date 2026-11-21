import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { fetchTaskRuns } from '../api/compatibility';
import { type ListResult, type TaskRun } from '../api/panel';
import { AbsentValue } from '../components/Epistemic';
import { Empty, PageHeader, QueryState } from '../components/Panel';
import { SurfaceStateBlock } from '../components/SurfaceState';
import { PAUSE_REASONS } from '../lib/labels';
import { formatAsOf } from '../lib/time';

type Activity = {
  id: string;
  author: string;
  action: string;
  result: string;
  at: string;
  error: string | null;
};

function TaskRunCard({ item }: { item: TaskRun }) {
  const consulted = item.sourcesConsulted ?? [];
  const failed = item.sourcesFailed ?? [];
  const total = consulted.length + failed.length;
  const when = item.finishedAt ?? item.startedAt ?? item.createdAt;

  if (item.state === 'queued' || item.state === 'running') {
    const title =
      total > 0
        ? `Buscando vagas… ${consulted.length} de ${total} fontes`
        : item.state === 'queued'
          ? 'Execução na fila aguardando início'
          : 'Execução em andamento…';
    const progressPercent = total > 0 ? (consulted.length / total) * 100 : undefined;
    return (
      <SurfaceStateBlock
        kind="loading"
        title={title}
        progressPercent={progressPercent}
        body={
          <span className="meta">
            {item.id}
            {when ? ` · ${formatAsOf(when)}` : ''}
          </span>
        }
      />
    );
  }

  if (item.state === 'completed' && failed.length > 0) {
    return (
      <SurfaceStateBlock
        kind="partial"
        coverage={{ respondedCount: consulted.length, totalCount: total }}
        body={
          <>
            <span>Fontes com falha: {failed.join(', ')}.</span>
            {item.errorSummary && <span> {item.errorSummary}</span>}
            <p className="meta">{item.id}</p>
          </>
        }
      />
    );
  }

  if (item.state === 'failed') {
    return (
      <SurfaceStateBlock
        kind="error"
        title="Execução falhou"
        body={
          <>
            <span>{item.errorSummary || 'Falha sem detalhes adicionais.'}</span>
            <p className="meta">{item.id}</p>
          </>
        }
        action={{ label: 'Verificar integrações →', to: '/integracoes' }}
      />
    );
  }

  if (item.state === 'paused') {
    return (
      <SurfaceStateBlock
        kind="pending_review"
        subLabel="EXECUÇÃO PAUSADA"
        title={PAUSE_REASONS[item.pauseReason ?? ''] ?? item.pauseReason ?? 'Execução pausada'}
        body={
          <>
            <span>{item.errorSummary || 'Aguardando verificação para retomar.'}</span>
            <p className="meta">{item.id}</p>
          </>
        }
        action={{ label: 'Revisar e retomar →', to: '/integracoes' }}
      />
    );
  }

  return (
    <article className="note-card dsh stack">
      <h3>Concluída</h3>
      <p className="meta">
        {item.id}
        {when && (
          <>
            {' · '}
            <time dateTime={when}>{formatAsOf(when)}</time>
          </>
        )}
      </p>
      {consulted.length > 0 ? (
        <p className="small">Fontes consultadas: {consulted.join(', ')}.</p>
      ) : (
        <AbsentValue>Nenhuma fonte registrada nesta execução.</AbsentValue>
      )}
      {item.errorSummary && <p>{item.errorSummary}</p>}
    </article>
  );
}

export function Activities() {
  const tasks = useQuery({
    queryKey: ['task-runs'],
    queryFn: ({ signal }) => fetchTaskRuns(signal),
    refetchInterval: (query) =>
      query.state.data?.items.some((item) => item.state === 'queued' || item.state === 'running')
        ? 5000
        : false,
  });
  const log = useQuery({
    queryKey: ['activity'],
    queryFn: ({ signal }) => api<ListResult<Activity>>('/api/activity', { signal }),
  });
  return (
    <div className="stack">
      <PageHeader title="Atividades" eyebrow="O que aconteceu por aqui" />
      <h2>Execuções</h2>
      <QueryState pending={tasks.isPending} error={tasks.error} retry={tasks.refetch} />
      {tasks.data?.items.map((item) => (
        <TaskRunCard key={item.id} item={item} />
      ))}
      {tasks.data?.items.length === 0 && <Empty>Nenhuma tarefa executada.</Empty>}
      <h2>Registro de alterações</h2>
      <QueryState pending={log.isPending} error={log.error} retry={log.refetch} />
      {log.data && (
        <ol className="timeline">
          {log.data.items.map((item) => (
            <li key={item.id}>
              <p className="meta">
                <time dateTime={item.at}>
                  {new Date(item.at).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' })}
                </time>{' '}
                · {item.author === 'user' ? 'Você' : item.author}
              </p>
              <h3>{item.action}</h3>
              <p>{item.result}</p>
              {item.error && <p className="alert dsh">{item.error}</p>}
            </li>
          ))}
        </ol>
      )}
      {log.data?.items.length === 0 && <Empty>Nenhuma alteração registrada.</Empty>}
    </div>
  );
}
