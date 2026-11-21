import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchTaskRuns } from '../api/compatibility';
import { panel, post } from '../api/panel';
import { StatusBadge } from '../components/StatusBadge';
import { DataAsOf } from '../components/DataAsOf';
import { AbsentValue } from '../components/Epistemic';
import { Empty, MutationFeedback, PageHeader, QueryState } from '../components/Panel';
import { SurfaceStateBlock } from '../components/SurfaceState';
import { PAUSE_REASONS } from '../lib/labels';

export function Integrations() {
  const client = useQueryClient();
  const home = useQuery({ queryKey: ['home'], queryFn: ({ signal }) => panel.home(signal) });
  const tasks = useQuery({
    queryKey: ['task-runs'],
    queryFn: ({ signal }) => fetchTaskRuns(signal),
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
            {item.state === 'disconnected' && (
              <SurfaceStateBlock
                kind="error"
                title="Integração desconectada"
                body={item.detail || 'Autorização ausente ou expirada.'}
              />
            )}
            {item.state === 'degraded' && (
              <SurfaceStateBlock
                kind="partial"
                title="Integração com falhas parciais"
                body={item.detail || 'Parte das consultas não respondeu.'}
              />
            )}
            {(item.state === 'ok' || item.state === 'paused') &&
              (item.detail ? (
                <p>{item.detail}</p>
              ) : (
                <AbsentValue>Nenhum detalhe adicional.</AbsentValue>
              ))}
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
        .map((item) => {
          const consulted = item.sourcesConsulted ?? [];
          const failed = item.sourcesFailed ?? [];
          const total = consulted.length + failed.length;
          return (
            <SurfaceStateBlock
              key={item.id}
              kind={failed.length > 0 ? 'partial' : 'pending_review'}
              title={PAUSE_REASONS[item.pauseReason ?? ''] ?? 'Tarefa pausada'}
              body={
                <>
                  {total > 0 && (
                    <p className="small">
                      {consulted.length} de {total} fontes consultadas
                      {failed.length > 0 ? ` · com falha: ${failed.join(', ')}` : ''}.
                    </p>
                  )}
                  <p className="meta">Referência: {item.id}</p>
                  {item.errorSummary ? (
                    <p>{item.errorSummary}</p>
                  ) : (
                    <AbsentValue>Nenhum detalhe adicional.</AbsentValue>
                  )}
                </>
              }
              action={{
                label: 'Retomar tarefa',
                disabled: resume.isPending,
                onClick: () => resume.mutate(item.id),
              }}
            />
          );
        })}
      {tasks.data && !tasks.data.items.some((item) => item.state === 'paused') && (
        <Empty>Nenhuma tarefa pausada.</Empty>
      )}
    </div>
  );
}
