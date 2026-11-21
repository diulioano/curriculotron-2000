import { useState, type CSSProperties } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import {
  post,
  recordExternalVisit,
  type OpportunityDetail as Detail,
  type OpportunityTimelineEvent,
  type TaskRun,
} from '../api/panel';
import {
  useCompatibilityRefresh,
  useCoordinatedOpportunityDetailQuery,
} from '../api/useCompatibilityRefresh';
import { CompatibilityEvidence } from '../components/CompatibilityEvidence';
import { Deadline } from '../components/Deadline';
import {
  AbsentValue,
  CorrectedField,
  EvidenceBlock,
  FactItem,
  InferenceBox,
  type ProfileMatchState,
} from '../components/Epistemic';
import { OpportunityCorrectionForm } from '../components/OpportunityCorrectionForm';
import { SalaryEstimate } from '../components/SalaryEstimate';
import { MutationFeedback, QueryState } from '../components/Panel';
import { SurfaceStateBlock } from '../components/SurfaceState';
import { companyColor, companyInitials, salaryText } from '../lib/opportunity';
import { profileMatchFor } from '../lib/profile-match';
import { formatAsOf } from '../lib/time';
import { api } from '../api/client';
import './opportunity-detail.css';

const MODES = { remote: 'Remoto', hybrid: 'Híbrido', onsite: 'Presencial' };
const TIERS = { priority: 'prioritária', suggested: 'sugerida' };
const safeUrl = (url: string) => /^https?:\/\//i.test(url);

function profileMatch(fact: string, item: Detail): ProfileMatchState {
  return profileMatchFor(fact, item.compatibilityCriteria ?? null, item.compatibilityEvidence);
}

function sortTimeline(events: readonly OpportunityTimelineEvent[]): OpportunityTimelineEvent[] {
  return [...events].sort((a, b) => new Date(a.at).getTime() - new Date(b.at).getTime());
}

export function OpportunityDetail() {
  const { id = '' } = useParams();
  const client = useQueryClient();
  const { invalidateAfterOpportunityChange } = useCompatibilityRefresh();
  const query = useCoordinatedOpportunityDetailQuery(id);
  const [correcting, setCorrecting] = useState(false);
  const [correctionSaved, setCorrectionSaved] = useState(false);

  const action = useMutation({
    mutationFn: (kind: string) => post(`/api/opportunities/${encodeURIComponent(id)}/${kind}`),
    onSuccess: () => {
      void invalidateAfterOpportunityChange();
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
  const missingProfileFacts = item
    ? item.facts.filter((fact) => profileMatch(fact, item) === 'missing_from_profile')
    : [];
  const timelineEvents = item ? sortTimeline(item.timeline) : [];

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
                {item.companyName.trim() ? (
                  <CorrectedField corrections={item.corrections} fields={['company_name']}>
                    {item.companyName}
                  </CorrectedField>
                ) : (
                  <CorrectedField corrections={item.corrections} fields={['company_name']}>
                    <AbsentValue>Empresa não informada</AbsentValue>
                  </CorrectedField>
                )}
                {item.companyTier && (
                  <span className="tier-tag mono">{TIERS[item.companyTier]}</span>
                )}
              </p>
              <h1>
                <CorrectedField corrections={item.corrections} fields={['title']}>
                  {item.title}
                </CorrectedField>
              </h1>
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

              <CompatibilityEvidence item={item} />

              {item.fitReasons.length > 0 && Boolean(item.compatibility?.current) && (
                <section className="why sk" aria-labelledby="por-que">
                  <h2 id="por-que">Por que recomendei</h2>
                  {item.fitReasons.map((reason, i) => (
                    <p key={i}>{reason}</p>
                  ))}
                  {missingProfileFacts.length > 0 && (
                    <p className="muted small">
                      Ponto de atenção: a vaga cita {missingProfileFacts[0]}, que não está no seu
                      perfil.
                    </p>
                  )}
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
                          <li key={i}>
                            <FactItem value={fact} profileMatch={profileMatch(fact, item)} />
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <AbsentValue>Nenhum requisito declarado registrado.</AbsentValue>
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
                        className="btn bt bt-sec dsh"
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
                          <li key={i}>
                            <InferenceBox value={text} />
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <AbsentValue>Nenhuma inferência.</AbsentValue>
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
                      <li key={i}>
                        <AbsentValue>{text}</AbsentValue>
                      </li>
                    ))}
                  </ul>
                </section>
              )}

              <section className="job-timeline-section" aria-labelledby="linha-do-tempo">
                <h2 id="linha-do-tempo">Linha do tempo</h2>
                {timelineEvents.length > 0 ? (
                  <ol className="job-timeline">
                    {timelineEvents.map((event) => (
                      <li key={event.id}>
                        <span className="job-timeline-meta">
                          <time dateTime={event.at}>{formatAsOf(event.at)}</time> ·{' '}
                          {event.actorLabel}
                        </span>
                        <span className="job-timeline-text">
                          {event.description}{' '}
                          {event.note && <span className="job-timeline-note">({event.note})</span>}
                        </span>
                      </li>
                    ))}
                  </ol>
                ) : (
                  <SurfaceStateBlock
                    kind="empty"
                    title="Nenhum evento registrado ainda."
                    body="A linha do tempo registra descoberta, correções e decisões explícitas nesta vaga."
                  />
                )}
              </section>
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
                    onClick={() => {
                      void recordExternalVisit(item.id)
                        .then(() => {
                          void client.invalidateQueries({ queryKey: ['opportunity', id] });
                        })
                        .catch(() => undefined);
                    }}
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

              <EvidenceBlock
                sources={item.sources}
                publishedAt={item.publishedAt}
                corrections={item.corrections}
                correctOpen={correcting}
                onCorrect={() => {
                  setCorrectionSaved(false);
                  setCorrecting((open) => !open);
                }}
              />

              {correctionSaved && (
                <p role="status">Correção registrada — fonte original preservada.</p>
              )}

              {correcting && (
                <OpportunityCorrectionForm
                  item={item}
                  onClose={() => setCorrecting(false)}
                  onCorrected={() => {
                    setCorrectionSaved(true);
                    setCorrecting(false);
                  }}
                />
              )}

              <section className="prepare-section" aria-labelledby="preparar">
                <h2 id="preparar">Preparar</h2>
                <SurfaceStateBlock
                  kind="empty"
                  title="Simulador de entrevista em construção"
                  body="Esta área reunirá preparação orientada aos requisitos declarados e lacunas desta vaga."
                  action={{ label: 'Ver critérios do perfil →', to: '/criterios' }}
                />
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
  const seniority = item.seniority;
  return (
    <section aria-label="Resumo" className="job-summary">
      <div>
        <span className="summary-label mono">LOCAL</span>
        {item.location ? (
          <CorrectedField corrections={item.corrections} fields={['location']}>
            <FactItem
              className="summary-value"
              value={item.location}
              sourceDeclared={!item.corrections.some((entry) => entry.fieldName === 'location')}
            />
          </CorrectedField>
        ) : (
          <CorrectedField corrections={item.corrections} fields={['location']}>
            <AbsentValue>a confirmar</AbsentValue>
          </CorrectedField>
        )}
      </div>
      <div>
        <span className="summary-label mono">MODALIDADE</span>
        {item.workMode === 'unknown' ? (
          <CorrectedField corrections={item.corrections} fields={['work_mode']}>
            <AbsentValue>a confirmar</AbsentValue>
          </CorrectedField>
        ) : (
          <CorrectedField corrections={item.corrections} fields={['work_mode']}>
            <FactItem
              className="summary-value"
              value={MODES[item.workMode]}
              sourceDeclared={!item.corrections.some((entry) => entry.fieldName === 'work_mode')}
            />
          </CorrectedField>
        )}
      </div>
      <div>
        <span className="summary-label mono">CONTRATO</span>
        {item.contractType === 'unknown' ? (
          <CorrectedField corrections={item.corrections} fields={['contract_type']}>
            <AbsentValue>a confirmar</AbsentValue>
          </CorrectedField>
        ) : (
          <CorrectedField corrections={item.corrections} fields={['contract_type']}>
            <FactItem
              className="summary-value"
              value={item.contractType.toUpperCase()}
              sourceDeclared={
                !item.corrections.some((entry) => entry.fieldName === 'contract_type')
              }
            />
          </CorrectedField>
        )}
      </div>
      <div>
        <span className="summary-label mono">SALÁRIO</span>
        {salary ? (
          <CorrectedField
            corrections={item.corrections}
            fields={['salary_min', 'salary_max', 'salary_unit']}
          >
            <FactItem
              bold
              className="summary-value salary"
              value={salary}
              sourceDeclared={
                !item.corrections.some((entry) =>
                  ['salary_min', 'salary_max', 'salary_unit'].includes(entry.fieldName),
                )
              }
            />
          </CorrectedField>
        ) : (
          <CorrectedField
            corrections={item.corrections}
            fields={['salary_min', 'salary_max', 'salary_unit']}
          >
            <AbsentValue spanClassName="salary">Salário a confirmar</AbsentValue>
          </CorrectedField>
        )}
      </div>
      <div>
        <span className="summary-label mono">NÍVEL</span>
        {seniority.status === 'corrected_title' && seniority.label ? (
          <InferenceBox
            inline
            className="summary-value"
            value={seniority.label}
            reason="derivado do título corrigido"
          />
        ) : seniority.status === 'fact' && seniority.label ? (
          <FactItem className="summary-value" value={seniority.label} />
        ) : seniority.status === 'inferred' && seniority.label ? (
          <InferenceBox
            inline
            className="summary-value"
            value={seniority.label}
            reason={seniority.reason}
          />
        ) : (
          <AbsentValue>a confirmar</AbsentValue>
        )}
      </div>
      <div>
        <span className="summary-label mono">DISPONIBILIDADE</span>
        {item.availability === 'unconfirmed' ? (
          <CorrectedField corrections={item.corrections} fields={['availability']}>
            <AbsentValue>a confirmar</AbsentValue>
          </CorrectedField>
        ) : item.availability === 'open' ? (
          <CorrectedField corrections={item.corrections} fields={['availability']}>
            <FactItem
              className="summary-value"
              value="aberta"
              sourceDeclared={!item.corrections.some((entry) => entry.fieldName === 'availability')}
            />
          </CorrectedField>
        ) : (
          <CorrectedField corrections={item.corrections} fields={['availability']}>
            <span className="summary-value">✕ encerrada</span>
          </CorrectedField>
        )}
      </div>
      <div>
        <span className="summary-label mono">PRAZO</span>
        <CorrectedField corrections={item.corrections} fields={['deadline_at']}>
          {item.deadlineAt ? (
            <Deadline at={item.deadlineAt} precision="date_only" />
          ) : (
            <AbsentValue>Prazo não informado</AbsentValue>
          )}
        </CorrectedField>
      </div>
      <div>
        <span className="summary-label mono">PUBLICAÇÃO</span>
        <CorrectedField corrections={item.corrections} fields={['published_at']}>
          {item.publishedAt ? (
            item.publishedAt.slice(0, 10)
          ) : (
            <AbsentValue>Data de publicação desconhecida</AbsentValue>
          )}
        </CorrectedField>
      </div>
    </section>
  );
}
