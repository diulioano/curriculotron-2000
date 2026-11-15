import type { CSSProperties } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { panel, post, type OpportunityDetail as Detail, type TaskRun } from '../api/panel';
import { Deadline } from '../components/Deadline';
import { SalaryEstimate } from '../components/SalaryEstimate';
import { MutationFeedback, QueryState } from '../components/Panel';
import { companyColor, companyInitials, salaryText } from '../lib/opportunity';
import { formatAsOf } from '../lib/time';
import { api } from '../api/client';
import './opportunity-detail.css';

const MODES = { remote: 'Remoto', hybrid: 'Híbrido', onsite: 'Presencial' };
const TIERS = { priority: 'prioritária', suggested: 'sugerida' };
const safeUrl = (url: string) => /^https?:\/\//i.test(url);

export function OpportunityDetail() {
  const { id = '' } = useParams();
  const client = useQueryClient();
  const query = useQuery({
    queryKey: ['opportunity', id],
    queryFn: ({ signal }) => panel.opportunity(id, signal),
  });
  const action = useMutation({
    mutationFn: (kind: string) => post(`/api/opportunities/${encodeURIComponent(id)}/${kind}`),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['opportunity', id] });
      void client.invalidateQueries({ queryKey: ['opportunities'] });
      void client.invalidateQueries({ queryKey: ['home'] });
      void client.invalidateQueries({ queryKey: ['applications'] });
    },
  });
  const item = query.data;
  const refresh = useMutation({
    mutationFn: async () => {
      const result = await post<{ taskRunId: string }>(
        `/api/opportunities/${encodeURIComponent(id)}/salary-estimate/refresh`,
      );
      for (let attempt = 0; attempt < 60; attempt++) {
        const task = await api<{ state: TaskRun['state']; errorSummary?: string }>(
          `/api/task-runs/${result.taskRunId}`,
        );
        if (task.state === 'completed') return;
        if (['failed', 'paused'].includes(task.state))
          throw new Error(
            task.errorSummary ||
              'Pesquisa assistida necessária. A referência anterior foi mantida.',
          );
        await new Promise((resolve) => setTimeout(resolve, 500));
      }
      throw new Error('A consulta continua em andamento. Confira as atividades.');
    },
    onSettled: () => {
      void client.invalidateQueries({ queryKey: ['opportunity', id] });
      void client.invalidateQueries({ queryKey: ['opportunities'] });
      void client.invalidateQueries({ queryKey: ['home'] });
    },
  });
  const color = item ? companyColor(item.companyName) : undefined;
  return (
    <div className="job-detail" style={{ '--c': color } as CSSProperties}>
      <Link to="/" className="back-link">
        <svg width="22" height="16" viewBox="0 0 22 16" aria-hidden="true">
          <polyline points="9,2 2,8 9,14" />
          <line x1="3" y1="8" x2="21" y2="7" />
        </svg>
        Voltar para a revisão do dia
      </Link>
      <QueryState pending={query.isPending} error={query.error} retry={query.refetch} />
      {item && (
        <>
          <header className="job-detail-head">
            <div className="company-stamp hatch" aria-hidden="true">
              <span>{companyInitials(item.companyName)}</span>
            </div>
            <div className="job-detail-title">
              <p className="job-detail-company">
                {item.companyName}
                {item.companyTier && (
                  <span className="tier-tag mono">{TIERS[item.companyTier]}</span>
                )}
              </p>
              <h1>{item.title}</h1>
              <svg
                className="squiggle"
                width="360"
                height="14"
                viewBox="0 0 360 14"
                aria-hidden="true"
              >
                <polyline points="2,9 28,3 54,11 80,3 106,10 132,4 158,11 184,3 210,10 236,4 262,11 288,3 314,10 340,5 358,8" />
              </svg>
            </div>
          </header>

          <div className="job-detail-columns">
            <div className="job-detail-main">
              <Summary item={item} />

              {item.fitReasons.length > 0 && (
                <section className="why sk" aria-labelledby="por-que">
                  <h2 id="por-que">Por que recomendei</h2>
                  {item.fitReasons.map((reason, i) => (
                    <p key={i}>{reason}</p>
                  ))}
                </section>
              )}

              <div className="requirements">
                <h2 id="requisitos">Requisitos</h2>
                <div className="requirements-columns">
                  <section className="req-facts">
                    <h3>Fatos da vaga</h3>
                    {item.facts.length ? (
                      <ul>
                        {item.facts.map((fact, i) => (
                          <li key={i}>✓ {fact}</li>
                        ))}
                      </ul>
                    ) : (
                      <p className="muted">Nenhum requisito declarado registrado.</p>
                    )}
                  </section>
                  <section className="req-inferred dsh">
                    <h3>Inferências</h3>
                    <SalaryEstimate item={item} detail />
                    <Link to="/referencias-salariais">
                      Consultar referências por empresa e cargo
                    </Link>
                    {!item.salary && (
                      <button
                        className="btn bt bt-sec"
                        disabled={refresh.isPending}
                        onClick={() => refresh.mutate()}
                      >
                        {refresh.isPending ? 'Atualizando…' : 'Atualizar estimativa'}
                      </button>
                    )}
                    {refresh.error && <p role="alert">{refresh.error.message}</p>}
                    {refresh.isSuccess && <p role="status">Estimativa atualizada.</p>}
                    {item.glassdoorUrl && (
                      <a href={item.glassdoorUrl} target="_blank" rel="noopener noreferrer">
                        Ver no Glassdoor ↗
                      </a>
                    )}
                    {item.inferred.length ? (
                      <ul>
                        {item.inferred.map((text, i) => (
                          <li key={i}>~ {text}</li>
                        ))}
                      </ul>
                    ) : (
                      <p className="muted">Nenhuma inferência.</p>
                    )}
                    <p className="muted small">Inferências não contam como requisito oficial.</p>
                  </section>
                </div>
              </div>

              {item.missing.length > 0 && (
                <section className="missing" aria-labelledby="a-confirmar">
                  <h2 id="a-confirmar">A confirmar</h2>
                  <ul>
                    {item.missing.map((text, i) => (
                      <li key={i} className="hatch absent">
                        <span>{text}</span>
                      </li>
                    ))}
                  </ul>
                </section>
              )}
            </div>

            <aside className="job-detail-aside">
              <section className="decide sk" aria-labelledby="decidir">
                <h2 id="decidir">Decidir</h2>
                <button
                  className="btn bt bt-pri"
                  disabled={
                    action.isPending ||
                    item.userDecision === 'applied' ||
                    (action.isSuccess && action.variables === 'applied')
                  }
                  onClick={() => action.mutate('applied')}
                >
                  {item.userDecision === 'applied' ? 'Candidatura já registrada' : 'Me candidatei'}
                </button>
                <button
                  className="btn bt bt-sec dsh"
                  disabled={action.isPending || item.userDecision === 'applied'}
                  onClick={() =>
                    action.mutate(item.userDecision === 'discarded' ? 'restore' : 'discard')
                  }
                >
                  {item.userDecision === 'discarded' ? 'Restaurar' : 'Descartar vaga'}
                </button>
                {item.sources.find((s) => safeUrl(s.url)) && (
                  <a
                    className="btn open-site"
                    href={item.sources.find((s) => safeUrl(s.url))!.url}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Abrir vaga no site ↗
                  </a>
                )}
                <p className="muted small">
                  Só “Me candidatei” registra a candidatura. Use depois de concluir a inscrição no
                  site da empresa.
                </p>
                <MutationFeedback
                  error={action.error}
                  success={
                    action.isSuccess &&
                    (action.variables === 'applied'
                      ? 'Candidatura registrada. Acompanhe em Candidaturas.'
                      : action.variables === 'restore'
                        ? 'Vaga restaurada.'
                        : 'Vaga descartada.')
                  }
                />
              </section>

              <section className="evidence dsh-t mono" aria-labelledby="evidencia">
                <h2 id="evidencia">Evidência</h2>
                {item.sources.length ? (
                  item.sources.map((source, index) => (
                    <div key={index} className="evidence-item">
                      {safeUrl(source.url) ? (
                        <a href={source.url} target="_blank" rel="noopener noreferrer">
                          fonte: {source.source} ↗
                        </a>
                      ) : (
                        <span>fonte: {source.source} · link indisponível</span>
                      )}
                      <span>
                        consultado:{' '}
                        <time dateTime={source.fetchedAt}>{formatAsOf(source.fetchedAt)}</time>
                      </span>
                    </div>
                  ))
                ) : (
                  <span>Nenhuma fonte registrada.</span>
                )}
                <span className="muted">Abrir a fonte não registra candidatura.</span>
              </section>
            </aside>
          </div>
        </>
      )}
    </div>
  );
}

function Summary({ item }: { item: Detail }) {
  const salary = salaryText(item);
  return (
    <section aria-label="Resumo" className="job-summary">
      <div>
        <span className="summary-label mono">LOCAL</span>
        {item.location ? (
          <span className="summary-value">✓ {item.location}</span>
        ) : (
          <Absent>a confirmar</Absent>
        )}
      </div>
      <div>
        <span className="summary-label mono">MODALIDADE</span>
        {item.workMode === 'unknown' ? (
          <Absent>a confirmar</Absent>
        ) : (
          <span className="summary-value">✓ {MODES[item.workMode]}</span>
        )}
      </div>
      <div>
        <span className="summary-label mono">CONTRATO</span>
        {item.contractType === 'unknown' ? (
          <Absent>a confirmar</Absent>
        ) : (
          <span className="summary-value">✓ {item.contractType.toUpperCase()}</span>
        )}
      </div>
      <div>
        <span className="summary-label mono">SALÁRIO</span>
        {salary ? (
          <span className="summary-value salary">✓ {salary}</span>
        ) : (
          <Absent className="salary">Salário a confirmar</Absent>
        )}
      </div>
      <div>
        <span className="summary-label mono">DISPONIBILIDADE</span>
        {item.availability === 'unconfirmed' ? (
          <Absent>a confirmar</Absent>
        ) : (
          <span className="summary-value">
            {item.availability === 'open' ? '✓ aberta' : '✕ encerrada'}
          </span>
        )}
      </div>
      {item.deadlineAt && (
        <div>
          <span className="summary-label mono">PRAZO</span>
          <Deadline at={item.deadlineAt} precision="date_only" />
        </div>
      )}
    </section>
  );
}

function Absent({ children, className }: { children: string; className?: string }) {
  return (
    <span className="absent hatch">
      <span className={className}>{children}</span>
    </span>
  );
}
