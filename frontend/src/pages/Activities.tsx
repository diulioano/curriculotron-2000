import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { type ListResult, type TaskRun } from '../api/panel';
import { Empty, PageHeader, QueryState } from '../components/Panel';
import { PAUSE_REASONS } from '../lib/labels';
import { Link } from 'react-router';

type Activity = {
  id: string;
  author: string;
  action: string;
  result: string;
  at: string;
  error: string | null;
};
const STATES = {
  queued: 'Na fila',
  running: 'Em execução',
  completed: 'Concluída',
  failed: 'Falhou',
  paused: 'Pausada',
};
export function Activities() {
  const tasks = useQuery({
    queryKey: ['task-runs'],
    queryFn: ({ signal }) => api<ListResult<TaskRun>>('/api/task-runs', { signal }),
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
        <article className="note-card dsh stack" key={item.id}>
          <h3>{STATES[item.state]}</h3>
          <p className="meta">{item.id}</p>
          {item.pauseReason && <p>{PAUSE_REASONS[item.pauseReason] ?? item.pauseReason}</p>}
          {item.errorSummary && <p>{item.errorSummary}</p>}
          {item.state === 'paused' && <Link to="/integracoes">Revisar e retomar →</Link>}
        </article>
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
              {item.error && <p className="alert">{item.error}</p>}
            </li>
          ))}
        </ol>
      )}
      {log.data?.items.length === 0 && <Empty>Nenhuma alteração registrada.</Empty>}
    </div>
  );
}
