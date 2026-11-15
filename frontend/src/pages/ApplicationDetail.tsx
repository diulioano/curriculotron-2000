import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import {
  panel,
  post,
  type ApplicationDetail as Detail,
  type ApplicationStatus,
} from '../api/panel';
import { APPLICATION_STATUSES } from '../lib/labels';
import { Deadline } from '../components/Deadline';
import { MutationFeedback, PageHeader, QueryState } from '../components/Panel';

function ApplicationForms({ item }: { item: Detail }) {
  const client = useQueryClient();
  const [status, setStatus] = useState<ApplicationStatus>(item.status);
  const [reason, setReason] = useState('');
  const [text, setText] = useState(item.nextAction ?? '');
  const [due, setDue] = useState(() =>
    item.nextActionDueAt
      ? item.nextActionDueAt.length === 10
        ? item.nextActionDueAt
        : new Date(item.nextActionDueAt).toLocaleDateString('en-CA', {
            timeZone: 'America/Sao_Paulo',
          })
      : '',
  );
  const save = useMutation({
    mutationFn: ({ route, body }: { route: string; body: unknown }) =>
      post(`/api/applications/${encodeURIComponent(item.id)}/${route}`, body),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['application', item.id] });
      void client.invalidateQueries({ queryKey: ['applications'] });
      void client.invalidateQueries({ queryKey: ['home'] });
    },
  });
  function statusSubmit(e: FormEvent) {
    e.preventDefault();
    save.mutate({ route: 'status', body: { toStatus: status, reason: reason.trim() } });
  }
  function actionSubmit(e: FormEvent) {
    e.preventDefault();
    save.mutate({
      route: 'next-action',
      body: {
        text: text.trim(),
        dueAt: due ? `${due}T23:59:59-03:00` : null,
        duePrecision: due ? 'date_only' : 'ambiguous',
      },
    });
  }
  return (
    <>
      <div className="detail-grid">
        <form className="note-card dsh stack" onSubmit={statusSubmit}>
          <h2>Atualizar etapa</h2>
          <div className="field">
            <label htmlFor="status">Etapa</label>
            <select
              className="input"
              id="status"
              value={status}
              onChange={(e) => setStatus(e.target.value as ApplicationStatus)}
            >
              {Object.entries(APPLICATION_STATUSES).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="reason">Motivo ou correção</label>
            <textarea
              id="reason"
              className="input"
              required
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>
          <div>
            <button className="btn bt-pri" disabled={save.isPending || !reason.trim()}>
              Salvar etapa
            </button>
          </div>
        </form>
        <form className="note-card dsh stack" onSubmit={actionSubmit}>
          <h2>Próximo passo</h2>
          <div className="field">
            <label htmlFor="next-action">O que precisa fazer?</label>
            <textarea
              className="input"
              id="next-action"
              required
              value={text}
              onChange={(e) => setText(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="due">Prazo (opcional, horário de São Paulo)</label>
            <input
              className="input"
              id="due"
              type="date"
              value={due}
              onChange={(e) => setDue(e.target.value)}
            />
          </div>
          <div>
            <button className="btn bt-pri" disabled={save.isPending || !text.trim()}>
              Salvar próximo passo
            </button>
          </div>
        </form>
      </div>
      <MutationFeedback
        error={save.error}
        success={save.isSuccess && 'Alteração registrada no histórico.'}
      />
    </>
  );
}
export function ApplicationDetail() {
  const { id = '' } = useParams();
  const query = useQuery({
    queryKey: ['application', id],
    queryFn: ({ signal }) => panel.application(id, signal),
  });
  const item = query.data;
  return (
    <div className="stack">
      <Link to="/candidaturas">← Candidaturas</Link>
      <QueryState pending={query.isPending} error={query.error} retry={query.refetch} />
      {item && (
        <>
          <PageHeader title={item.title} eyebrow={item.companyName}>
            <Link className="btn bt-sec dsh" to={`/vagas/${item.opportunityId}`}>
              Ver vaga ↗
            </Link>
          </PageHeader>
          <p className="stage mono">{APPLICATION_STATUSES[item.status]}</p>
          <Deadline at={item.nextActionDueAt} precision={item.duePrecision} />
          <ApplicationForms key={item.id} item={item} />
          <section className="note-card dsh stack">
            <h2>Histórico preservado</h2>
            <p className="muted">
              Correções criam novos registros. As alterações anteriores continuam aqui.
            </p>
            <ol className="timeline">
              {item.history.map((event) => (
                <li key={event.id}>
                  <span className="meta">
                    <time dateTime={event.at}>
                      {new Date(event.at).toLocaleString('pt-BR', {
                        timeZone: 'America/Sao_Paulo',
                      })}
                    </time>{' '}
                    · {event.author === 'user' ? 'Você' : event.author}
                  </span>
                  <h3>{APPLICATION_STATUSES[event.toStatus]}</h3>
                  <p>{event.reason}</p>
                  {event.evidence && (
                    <blockquote>
                      <p>{event.evidence.quote}</p>
                      <cite>{event.evidence.ref}</cite>
                    </blockquote>
                  )}
                </li>
              ))}
            </ol>
          </section>
        </>
      )}
    </div>
  );
}
