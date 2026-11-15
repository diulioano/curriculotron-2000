import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { panel } from '../api/panel';
import { Deadline } from '../components/Deadline';
import { DataAsOf } from '../components/DataAsOf';
import { Empty, PageHeader, QueryState } from '../components/Panel';
import { APPLICATION_STATUSES } from '../lib/labels';

export function Applications() {
  const [params, setParams] = useSearchParams();
  const query = useQuery({
    queryKey: ['applications'],
    queryFn: ({ signal }) => panel.applications(signal),
  });
  const status = params.get('status') ?? '';
  const items = query.data?.items.filter((item) => !status || item.status === status);
  return (
    <div className="stack">
      <PageHeader title="Candidaturas" eyebrow="Cada conversa tem uma história" />
      <p className="muted">Inscrições que você confirmou, com histórico e próximos passos.</p>
      <div className="field filter-field">
        <label htmlFor="application-filter">Filtrar por etapa</label>
        <select
          className="input"
          id="application-filter"
          value={status}
          onChange={(e) => setParams(e.target.value ? { status: e.target.value } : {})}
        >
          <option value="">Todas as etapas</option>
          {Object.entries(APPLICATION_STATUSES).map(([key, label]) => (
            <option key={key} value={key}>
              {label}
            </option>
          ))}
        </select>
      </div>
      <QueryState pending={query.isPending} error={query.error} retry={query.refetch} />
      {query.data && <DataAsOf at={query.data.dataAsOf} />}
      {items?.map((item) => (
        <article className="note-card dsh application-row" key={item.id}>
          <div className="stack compact">
            <p className="eyebrow">{item.companyName}</p>
            <h2>
              <Link to={`/candidaturas/${item.id}`}>{item.title}</Link>
            </h2>
            <span className="stage mono">{APPLICATION_STATUSES[item.status]}</span>
          </div>
          <div className="stack compact">
            <p>{item.nextAction || 'Nenhuma próxima ação definida.'}</p>
            <Deadline at={item.nextActionDueAt} precision={item.duePrecision} />
          </div>
        </article>
      ))}
      {items?.length === 0 && (
        <Empty>
          Nenhuma candidatura nesta etapa. Registre uma inscrição pelo botão “Me candidatei” no
          detalhe da vaga.
        </Empty>
      )}
    </div>
  );
}
