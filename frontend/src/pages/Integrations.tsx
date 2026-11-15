import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { panel, post, type ListResult, type TaskRun } from '../api/panel';
import { api } from '../api/client';
import { StatusBadge } from '../components/StatusBadge';
import { DataAsOf } from '../components/DataAsOf';
import { Empty, MutationFeedback, PageHeader, QueryState } from '../components/Panel';
import { PAUSE_REASONS } from '../lib/labels';

export function Integrations() {
  const client = useQueryClient();
  const home = useQuery({ queryKey: ['home'], queryFn: ({ signal }) => panel.home(signal) });
  const tasks = useQuery({
    queryKey: ['task-runs'],
    queryFn: ({ signal }) => api<ListResult<TaskRun>>('/api/task-runs', { signal }),
  });
  const resume = useMutation({
    mutationFn: (id: string) => post(`/api/task-runs/${encodeURIComponent(id)}/resume`),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['task-runs'] });
      void client.invalidateQueries({ queryKey: ['home'] });
    },
  });
  return (
    <div className="stack">
      <PageHeader title="Integrações" eyebrow="O estado real da sua busca" />
      <div className="notice dsh">Acesso só na Wi-Fi de casa, com este computador ligado.</div>
      <p className="muted">Uma tentativa que falhou não altera a data do último sucesso.</p>
      <QueryState pending={home.isPending} error={home.error} retry={home.refetch} />
      <div className="jobs-grid">
        {home.data?.integrations.map((item) => (
          <section className="note-card dsh stack" key={item.id}>
            <div className="section-heading">
              <h2>{item.id}</h2>
              <StatusBadge state={item.state} />
            </div>
            <p>{item.detail || 'Nenhum detalhe adicional.'}</p>
            <div>
              <p className="meta">Último sucesso</p>
              <DataAsOf at={item.lastSuccessAt} />
            </div>
            <div>
              <p className="meta">Última tentativa</p>
              <DataAsOf at={item.lastAttemptAt} />
            </div>
          </section>
        ))}
      </div>
      {home.data?.integrations.length === 0 && <Empty>Nenhuma integração informada.</Empty>}
      <h2>Tarefas pausadas</h2>
      <QueryState pending={tasks.isPending} error={tasks.error} retry={tasks.refetch} />
      <MutationFeedback
        error={resume.error}
        success={resume.isSuccess && 'Retomada solicitada a partir do último ponto salvo.'}
      />
      {tasks.data?.items
        .filter((item) => item.state === 'paused')
        .map((item) => (
          <article className="note-card dsh stack" key={item.id}>
            <h3>{PAUSE_REASONS[item.pauseReason ?? ''] ?? 'Tarefa pausada'}</h3>
            <p className="meta">Referência: {item.id}</p>
            <p>{item.errorSummary}</p>
            <div>
              <button
                className="btn bt-sec dsh"
                disabled={resume.isPending}
                onClick={() => resume.mutate(item.id)}
              >
                Retomar tarefa
              </button>
            </div>
          </article>
        ))}
      {tasks.data && !tasks.data.items.some((item) => item.state === 'paused') && (
        <Empty>Nenhuma tarefa pausada.</Empty>
      )}
    </div>
  );
}
