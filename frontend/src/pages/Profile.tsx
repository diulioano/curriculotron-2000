import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { NavLink } from 'react-router';
import { ApiError, api } from '../api/client';
import {
  describeProfileAssessmentStatus,
  type CriteriaResponse,
  type CriteriaUpdatePayload,
  type RefreshStatus,
} from '../api/compatibility';
import {
  useCompatibilityRefresh,
  useCoordinatedCompatibilityStatusQuery,
  useCoordinatedCriteriaQuery,
  useReassessMutation,
  useSaveCriteriaMutation,
} from '../api/useCompatibilityRefresh';
import { post, type ListResult } from '../api/panel';
import { Empty, MutationFeedback, PageHeader, QueryState } from '../components/Panel';

type Fact = {
  id: string;
  kind: string;
  statement: string;
  origin: 'documented' | 'declared' | 'study_recommended';
  originRef: string;
};
type ProfileData = { id: string; status: 'imported' | 'reviewed'; facts: Fact[] };
type Company = { id: string; name: string; tier: 'priority' | 'suggested'; notes: string };
const ORIGINS = {
  documented: 'Documentado no currículo',
  declared: 'Declarado por você',
  study_recommended: 'Recomendação de estudo',
};
const KINDS: Record<string, string> = {
  experience: 'Experiência',
  skill: 'Competência',
  education: 'Formação',
  language: 'Idioma',
  preference: 'Preferência',
};
function ProfileNav() {
  return (
    <nav className="tabs dsh-b" aria-label="Configurações do perfil">
      {[
        ['/perfil', 'Perfil'],
        ['/criterios', 'Critérios'],
        ['/empresas', 'Empresas'],
        ['/dados', 'Dados e privacidade'],
      ].map(([to, label]) => (
        <NavLink className={({ isActive }) => (isActive ? 'tab hl' : 'tab')} key={to} to={to}>
          {label}
        </NavLink>
      ))}
    </nav>
  );
}
function ProfileForm({ data }: { data: ProfileData }) {
  const client = useQueryClient();
  const [facts, setFacts] = useState(data.facts);
  const save = useMutation({
    mutationFn: () =>
      api('/api/profile', { method: 'PUT', body: { id: data.id, status: 'reviewed', facts } }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['profile'] });
    },
  });
  function submit(e: FormEvent) {
    e.preventDefault();
    save.mutate();
  }
  return (
    <form className="stack" onSubmit={submit}>
      {facts.map((fact, i) => (
        <section className="note-card dsh stack" key={fact.id}>
          <div className="section-heading">
            <strong>{KINDS[fact.kind] ?? fact.kind}</strong>
            <span className="meta">{ORIGINS[fact.origin]}</span>
          </div>
          {fact.origin === 'declared' ? (
            <div className="field">
              <label htmlFor={`fact-${fact.id}`}>Informação profissional</label>
              <textarea
                className="input"
                id={`fact-${fact.id}`}
                required
                value={fact.statement}
                onChange={(e) =>
                  setFacts(facts.map((f, j) => (j === i ? { ...f, statement: e.target.value } : f)))
                }
              />
            </div>
          ) : (
            <p>{fact.statement}</p>
          )}
          <p className="meta">Origem: {fact.originRef || 'Sem referência registrada'}</p>
          {fact.origin === 'study_recommended' && (
            <p className="meta">Esta recomendação não é usada como comprovação de competência.</p>
          )}
        </section>
      ))}
      <div className="actions">
        <button
          type="button"
          className="btn bt bt-sec dsh"
          onClick={() =>
            setFacts([
              ...facts,
              {
                id: crypto.randomUUID(),
                kind: 'skill',
                statement: '',
                origin: 'declared',
                originRef: 'Declaração no painel',
              },
            ])
          }
        >
          + Adicionar informação declarada
        </button>
        <button
          type="submit"
          className="btn bt bt-pri"
          disabled={save.isPending || facts.some((f) => !f.statement.trim())}
        >
          Salvar e marcar como revisado
        </button>
      </div>
      <MutationFeedback error={save.error} success={save.isSuccess && 'Perfil revisado e salvo.'} />
    </form>
  );
}
export function Profile() {
  const client = useQueryClient();
  const query = useQuery({
    queryKey: ['profile'],
    queryFn: ({ signal }) => api<ProfileData>('/api/profile', { signal }),
  });
  const reimport = useMutation({
    mutationFn: () => post('/api/profile/import'),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['profile'] });
    },
  });
  return (
    <div className="stack">
      <PageHeader title="Seu perfil" eyebrow="A busca começa pelo que você sabe">
        <button
          className="btn bt bt-sec dsh"
          disabled={reimport.isPending}
          onClick={() => reimport.mutate()}
        >
          Reimportar currículo
        </button>
      </PageHeader>
      <ProfileNav />
      <p className="muted">
        Fatos do currículo, declarações e recomendações de estudo mantêm suas origens separadas.
      </p>
      <MutationFeedback
        error={reimport.error}
        success={reimport.isSuccess && 'Currículo reimportado.'}
      />
      <QueryState pending={query.isPending} error={query.error} retry={query.refetch} />
      {query.data && <ProfileForm key={JSON.stringify(query.data.facts)} data={query.data} />}
    </div>
  );
}
function toUpdatePayload(data: CriteriaResponse): CriteriaUpdatePayload {
  return {
    version: data.version,
    targetRoles: data.targetRoles,
    stackKeywords: data.stackKeywords,
    remotePreferred: data.remotePreferred,
    hybridRegions: data.hybridRegions,
    cltMinMonthlyBrl: data.cltMinMonthlyBrl,
    pjMinMonthlyBrl: data.pjMinMonthlyBrl,
    acceptJuniorIfJustified: data.acceptJuniorIfJustified,
    dailySearchTimeLocal: data.dailySearchTimeLocal,
    deadlineHighlightDays: data.deadlineHighlightDays,
    urgentHours: data.urgentHours,
    emailDigestEnabled: data.emailDigestEnabled,
  };
}
function cleanLines(lines: string[]): string[] {
  return lines.map((line) => line.trim()).filter((line) => line.length > 0);
}
function CriteriaForm({ data }: { data: CriteriaResponse }) {
  const { reloadCriteriaSnapshot } = useCompatibilityRefresh();
  const [draft, setDraft] = useState<CriteriaUpdatePayload | null>(null);
  const [conflictMessage, setConflictMessage] = useState<string | null>(null);
  const [reloadError, setReloadError] = useState<string | null>(null);
  const [reloading, setReloading] = useState(false);

  const form = draft ?? toUpdatePayload(data);

  const save = useSaveCriteriaMutation({
    onSuccess: () => {
      setDraft(null);
      setConflictMessage(null);
      setReloadError(null);
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'criteria_version_conflict') {
        setConflictMessage(error.message);
      }
    },
  });

  async function reloadCriteria() {
    setReloading(true);
    setReloadError(null);
    try {
      await reloadCriteriaSnapshot();
      setDraft(null);
      setConflictMessage(null);
      save.reset();
    } catch (error) {
      setReloadError(
        error instanceof Error ? error.message : 'Não foi possível recarregar os critérios atuais.',
      );
    } finally {
      setReloading(false);
    }
  }

  const arrays = [
    ['targetRoles', 'Cargos desejados'],
    ['stackKeywords', 'Tecnologias'],
    ['hybridRegions', 'Regiões para trabalho híbrido'],
  ] as const;

  return (
    <form
      className="note-card dsh stack"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate({
          ...form,
          targetRoles: cleanLines(form.targetRoles),
          stackKeywords: cleanLines(form.stackKeywords),
          hybridRegions: cleanLines(form.hybridRegions),
        });
      }}
    >
      <div className="detail-grid">
        {arrays.map(([key, label]) => (
          <div className="field" key={key}>
            <label htmlFor={key}>{label} (um por linha)</label>
            <textarea
              className="input"
              id={key}
              rows={3}
              value={form[key].join('\n')}
              onChange={(e) => setDraft({ ...form, [key]: e.target.value.split('\n') })}
            />
          </div>
        ))}
        <div className="field">
          <label htmlFor="clt">Mínimo CLT mensal (R$)</label>
          <input
            className="input"
            id="clt"
            type="number"
            min="0.01"
            step="any"
            required
            value={form.cltMinMonthlyBrl}
            onChange={(e) => setDraft({ ...form, cltMinMonthlyBrl: Number(e.target.value) })}
          />
        </div>
        <div className="field">
          <label htmlFor="pj">Mínimo PJ mensal (R$)</label>
          <input
            className="input"
            id="pj"
            type="number"
            min="0.01"
            step="any"
            required
            value={form.pjMinMonthlyBrl}
            onChange={(e) => setDraft({ ...form, pjMinMonthlyBrl: Number(e.target.value) })}
          />
        </div>
        <div className="field">
          <label htmlFor="search-time">Horário da busca (São Paulo)</label>
          <input
            className="input"
            id="search-time"
            type="time"
            required
            value={form.dailySearchTimeLocal}
            onChange={(e) => setDraft({ ...form, dailySearchTimeLocal: e.target.value })}
          />
        </div>
      </div>
      <label className="check-field">
        <input
          type="checkbox"
          checked={form.remotePreferred}
          onChange={(e) => setDraft({ ...form, remotePreferred: e.target.checked })}
        />{' '}
        Priorizar trabalho remoto
      </label>
      <label className="check-field">
        <input
          type="checkbox"
          checked={form.acceptJuniorIfJustified}
          onChange={(e) => setDraft({ ...form, acceptJuniorIfJustified: e.target.checked })}
        />{' '}
        Aceitar vagas júnior apenas com justificativa
      </label>
      <div className="actions">
        <button type="submit" className="btn bt bt-pri" disabled={save.isPending}>
          Salvar critérios
        </button>
        {conflictMessage && (
          <button
            type="button"
            className="btn bt bt-sec dsh"
            disabled={reloading}
            onClick={() => void reloadCriteria()}
          >
            {reloading ? 'Recarregando…' : 'Recarregar critérios'}
          </button>
        )}
      </div>
      {conflictMessage ? (
        <p className="alert dsh" role="alert">
          {conflictMessage} Suas edições permanecem no formulário.
        </p>
      ) : (
        <MutationFeedback error={save.error} success={save.isSuccess && 'Critérios salvos.'} />
      )}
      {reloadError && (
        <p className="alert dsh" role="alert">
          {reloadError}
        </p>
      )}
    </form>
  );
}
function AssessmentPanel({ initialStatus }: { initialStatus: RefreshStatus }) {
  const { consultLatestStatus } = useCompatibilityRefresh();
  const [feedbackError, setFeedbackError] = useState<string | null>(null);
  const [feedbackInfo, setFeedbackInfo] = useState<string | null>(null);
  const [statusUnconfirmed, setStatusUnconfirmed] = useState(false);

  const statusQuery = useCoordinatedCompatibilityStatusQuery(initialStatus, {
    onNonRunningStatus: () => {
      setFeedbackInfo(null);
    },
  });

  const currentStatus = statusQuery.data ?? initialStatus;
  const pollingFailed = statusQuery.isError;
  const isUnconfirmed = statusUnconfirmed || pollingFailed;
  const isRunning = currentStatus.state === 'running' && !isUnconfirmed;

  async function consultStatusAfterUncertainty(reasonMessage: string, isTimeout: boolean) {
    try {
      const latest = await consultLatestStatus();
      setStatusUnconfirmed(false);
      if (latest.state === 'ready' && !latest.stale) {
        setFeedbackError(null);
        setFeedbackInfo(null);
      } else if (latest.state === 'running') {
        setFeedbackError(null);
        setFeedbackInfo(
          isTimeout
            ? 'Tempo esgotado na resposta; atualização segue em andamento. Acompanhando progresso…'
            : 'Já existe uma atualização em andamento. Acompanhando progresso…',
        );
      } else {
        setFeedbackInfo(null);
        setFeedbackError(
          isTimeout
            ? `Tempo esgotado na solicitação. Status consultado: ${describeProfileAssessmentStatus(latest)}`
            : reasonMessage,
        );
      }
    } catch {
      setStatusUnconfirmed(true);
      setFeedbackInfo(null);
      setFeedbackError(
        'Não foi possível confirmar o status da atualização. Consulte o status antes de tentar novamente.',
      );
    }
  }

  const reassess = useReassessMutation({
    onMutate: () => {
      setFeedbackError(null);
      setFeedbackInfo(null);
      setStatusUnconfirmed(false);
    },
    onSuccess: () => {
      setFeedbackError(null);
      setFeedbackInfo(null);
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.code === 'reassessment_running') {
        await consultStatusAfterUncertainty(error.message, false);
        return;
      }
      if (error instanceof ApiError && error.code === 'criteria_version_conflict') {
        await consultStatusAfterUncertainty(error.message, false);
        return;
      }
      const isTimeoutOrNetwork =
        !(error instanceof ApiError) || error.status === 408 || error.status === 504;
      await consultStatusAfterUncertainty(
        error instanceof Error ? error.message : 'Falha ao reavaliar compatibilidade.',
        isTimeoutOrNetwork,
      );
    },
  });

  const canTriggerReassess =
    currentStatus.state === 'pending' ||
    currentStatus.state === 'failed' ||
    currentStatus.state === 'running' ||
    currentStatus.stale;

  const displayedError =
    feedbackError ??
    (pollingFailed
      ? 'Não foi possível confirmar o andamento da atualização. Consulte o status antes de tentar novamente.'
      : null);

  return (
    <section className="note-card dsh stack" aria-label="Status da compatibilidade">
      <div className="section-heading">
        <strong>Avaliação de compatibilidade</strong>
        <span className="meta">Critério v{currentStatus.criteriaVersion}</span>
      </div>
      <p role="status">{describeProfileAssessmentStatus(currentStatus)}</p>
      {feedbackInfo && !isUnconfirmed && <p className="notice dsh">{feedbackInfo}</p>}
      {displayedError && (
        <p className="alert dsh" role="alert">
          {displayedError}
        </p>
      )}
      <div className="actions">
        {canTriggerReassess && !isUnconfirmed && (
          <button
            type="button"
            className="btn bt bt-sec dsh"
            disabled={reassess.isPending || isRunning}
            onClick={() => reassess.mutate(currentStatus.criteriaVersion)}
          >
            {reassess.isPending || isRunning ? 'Atualizando avaliação…' : 'Atualizar avaliação'}
          </button>
        )}
        {isUnconfirmed && (
          <button
            type="button"
            className="btn bt bt-sec dsh"
            onClick={() =>
              void consultStatusAfterUncertainty('Status consultado manualmente.', false)
            }
          >
            Consultar status
          </button>
        )}
      </div>
    </section>
  );
}
export function Criteria() {
  const query = useCoordinatedCriteriaQuery();
  return (
    <div className="stack">
      <PageHeader title="Critérios de busca" eyebrow="Uma busca com a sua medida" />
      <ProfileNav />
      <QueryState pending={query.isPending} error={query.error} retry={query.refetch} />
      {query.data && (
        <>
          <AssessmentPanel initialStatus={query.data.assessment} />
          <CriteriaForm data={query.data} />
        </>
      )}
    </div>
  );
}
export function Companies() {
  const client = useQueryClient();
  const query = useQuery({
    queryKey: ['companies'],
    queryFn: ({ signal }) => api<ListResult<Company>>('/api/companies', { signal }),
  });
  const [name, setName] = useState('');
  const [notes, setNotes] = useState('');
  const change = useMutation({
    mutationFn: ({ path, body }: { path: string; body?: unknown }) => post(path, body),
    onSuccess: () => {
      setName('');
      setNotes('');
      void client.invalidateQueries({ queryKey: ['companies'] });
    },
  });
  return (
    <div className="stack">
      <PageHeader title="Empresas" eyebrow="Onde você quer chegar" />
      <ProfileNav />
      <QueryState pending={query.isPending} error={query.error} retry={query.refetch} />
      {(['priority', 'suggested'] as const).map((tier) => (
        <section className="stack" key={tier}>
          <h2>{tier === 'priority' ? 'Prioritárias' : 'Sugeridas para você'}</h2>
          {query.data?.items
            .filter((item) => item.tier === tier)
            .map((item) => (
              <article className="note-card dsh section-heading" key={item.id}>
                <div className="stack compact">
                  <h3>{item.name}</h3>
                  <p>{item.notes}</p>
                </div>
                {tier === 'suggested' && (
                  <button
                    className="btn bt bt-sec dsh"
                    disabled={change.isPending}
                    onClick={() =>
                      change.mutate({
                        path: `/api/companies/${encodeURIComponent(item.id)}/promote`,
                      })
                    }
                  >
                    Tornar prioritária
                  </button>
                )}
              </article>
            ))}
          {query.data && !query.data.items.some((item) => item.tier === tier) && (
            <Empty>Nenhuma empresa nesta lista.</Empty>
          )}
        </section>
      ))}
      <form
        className="note-card dsh stack"
        onSubmit={(e) => {
          e.preventDefault();
          change.mutate({
            path: '/api/companies',
            body: { name: name.trim(), notes: notes.trim(), tier: 'priority' },
          });
        }}
      >
        <h2>Adicionar uma prioridade</h2>
        <div className="field">
          <label htmlFor="company-name">Empresa</label>
          <input
            className="input"
            id="company-name"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="company-notes">Observações</label>
          <textarea
            className="input"
            id="company-notes"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
        </div>
        <div>
          <button
            type="submit"
            className="btn bt bt-pri"
            disabled={change.isPending || !name.trim()}
          >
            Adicionar empresa
          </button>
        </div>
      </form>
      <MutationFeedback
        error={change.error}
        success={change.isSuccess && 'Empresas atualizadas.'}
      />
    </div>
  );
}
export function DataPrivacy() {
  const query = useQuery({
    queryKey: ['inventory'],
    queryFn: ({ signal }) =>
      api<{ items: { category: string; count: number; retention: string }[] }>(
        '/api/data-inventory',
        { signal },
      ),
  });
  return (
    <div className="stack">
      <PageHeader title="Dados e privacidade" eyebrow="Seu computador, seus dados" />
      <ProfileNav />
      <div className="notice dsh">
        Acesso só na Wi-Fi de casa. O painel depende deste computador ligado.
      </div>
      <QueryState pending={query.isPending} error={query.error} retry={query.refetch} />
      {query.data?.items.map((item) => (
        <section className="note-card dsh stack" key={item.category}>
          <h2>{item.category}</h2>
          <p>{item.count} registros</p>
          <p className="meta">Retenção: {item.retention}</p>
        </section>
      ))}
      <p className="muted">
        A exclusão de dados será disponibilizada junto ao serviço de retenção e revogação do Gmail.
      </p>
    </div>
  );
}
