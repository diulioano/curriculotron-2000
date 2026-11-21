import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { panel } from '../api/panel';
import { Deadline } from '../components/Deadline';
import { DataAsOf } from '../components/DataAsOf';
import { AbsentValue, FactItem } from '../components/Epistemic';
import { Empty, PageHeader, QueryState } from '../components/Panel';
import { Select } from '../components/Select';
import { APPLICATION_STATUSES } from '../lib/labels';

const FILTER_LABEL = 'Filtrar por etapa';
const FILTER_OPTIONS = [
  { value: '', label: 'Todas as etapas' },
  ...Object.entries(APPLICATION_STATUSES).map(([value, label]) => ({ value, label })),
];

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
        <label htmlFor="application-filter">{FILTER_LABEL}</label>
        <Select
          id="application-filter"
          value={status}
          options={FILTER_OPTIONS}
          onChange={(next) => setParams(next ? { status: next } : {})}
        />
      </div>
      <QueryState pending={query.isPending} error={query.error} retry={query.refetch} />
      {query.data && <DataAsOf at={query.data.dataAsOf} />}
      {items?.map((item) => (
        <article className="note-card dsh application-row" key={item.id}>
          <div className="stack compact">
            <p className="eyebrow">
              {item.companyName.trim() ? (
                <FactItem value={item.companyName} />
              ) : (
                <AbsentValue>Empresa não informada</AbsentValue>
              )}
            </p>
            <h2>
              <Link to={`/candidaturas/${item.id}`}>{item.title}</Link>
            </h2>
            <span className="stage mono">{APPLICATION_STATUSES[item.status]}</span>
          </div>
          <div className="stack compact">
            {item.nextAction ? (
              <p>{item.nextAction}</p>
            ) : (
              <AbsentValue>Nenhuma próxima ação definida.</AbsentValue>
            )}
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
