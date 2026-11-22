import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
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
  PROFILE_KEY,
} from '../api/useCompatibilityRefresh';
import {
  getProfile,
  previewProfileImport,
  SENIORITY_LABELS,
  type CareerProfile,
  type DraftField,
  type ProfileView,
} from '../api/profile';
import { FieldOrigin, ProfileEditor } from '../components/ProfileEditor';
import { manualSession, toForm, type EditorSession } from '../lib/profile-form';
import { formatAsOf } from '../lib/time';
import { post, type ListResult } from '../api/panel';
import { Empty, MutationFeedback, PageHeader, QueryState } from '../components/Panel';

type Company = { id: string; name: string; tier: 'priority' | 'suggested'; notes: string };

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
const BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const ORIGIN_LABELS = { markdown: 'Importado de Markdown e revisado', manual: 'Entrada manual' };

function ListValue({ items, empty }: { items: string[]; empty: string }) {
  return items.length ? <span>{items.join(', ')}</span> : <span className="muted">{empty}</span>;
}

function floorSummary(value: number | null, regime: string): string {
  return value === null
    ? `Não definido — Júnior ${regime} não entra pela exceção`
    : `${BRL.format(value)}/mês`;
}

function ProfileSummary({ profile }: { profile: CareerProfile }) {
  const rows: [DraftField, string, ReactNode][] = [
    ['role', 'Cargo desejado', profile.role],
    ['primaryTechnology', 'Tecnologia principal', profile.primaryTechnology],
    [
      'secondaryTechnologies',
      'Tecnologias secundárias',
      <ListValue items={profile.secondaryTechnologies} empty="Nenhuma" />,
    ],
    [
      'flexibleTechnologies',
      'Preferências flexíveis',
      <ListValue items={profile.flexibleTechnologies} empty="Nenhuma" />,
    ],
    ['currentSeniority', 'Senioridade atual', SENIORITY_LABELS[profile.currentSeniority]],
    [
      'targetLevels',
      'Níveis desejados',
      profile.targetLevels.map((l) => SENIORITY_LABELS[l]).join(', '),
    ],
    [
      'priorities',
      'Empresas prioritárias',
      <ListValue items={profile.priorities} empty="Nenhuma" />,
    ],
    [
      'juniorFloorCltMonthlyBrl',
      'Piso Júnior CLT',
      floorSummary(profile.juniorFloorCltMonthlyBrl, 'CLT'),
    ],
    [
      'juniorFloorPjMonthlyBrl',
      'Piso Júnior PJ',
      floorSummary(profile.juniorFloorPjMonthlyBrl, 'PJ'),
    ],
  ];
  return (
    <section className="note-card dsh stack" aria-label="Perfil salvo">
      <div className="section-heading">
        <strong>Perfil v{profile.version}</strong>
        <span className="meta">
          {ORIGIN_LABELS[profile.origin]} · atualizado em {formatAsOf(profile.updatedAt)}
        </span>
      </div>
      <dl className="profile-summary">
        {rows.map(([field, label, value]) => {
          const origin = profile.provenance[field];
          return (
            <div key={field}>
              <dt className="meta">{label}</dt>
              <dd>{value}</dd>
              {origin && (
                <dd className="stack compact">
                  <FieldOrigin provenance={origin} />
                </dd>
              )}
            </div>
          );
        })}
      </dl>
    </section>
  );
}

function MarkdownImport({
  onDraft,
  onManual,
}: {
  onDraft: (session: EditorSession) => void;
  onManual: () => void;
}) {
  const [markdown, setMarkdown] = useState('');
  const [fileError, setFileError] = useState<string | null>(null);
  const preview = useMutation({
    mutationFn: previewProfileImport,
    onSuccess: ({ draft, warnings, provenance }) =>
      onDraft({ origin: 'markdown', initial: toForm(draft), warnings, provenance, imported: true }),
  });
  const failure = fileError ?? preview.error?.message;
  return (
    <form
      className="note-card dsh stack"
      aria-label="Importar currículo Markdown"
      onSubmit={(e) => {
        e.preventDefault();
        if (markdown.trim()) preview.mutate(markdown);
      }}
    >
      <h2>Importar currículo em Markdown</h2>
      <p className="muted">
        O conteúdo vira um rascunho para você revisar. Nada é salvo antes da sua confirmação.
      </p>
      <div className="field">
        <label htmlFor="profile-markdown-file">Arquivo .md</label>
        <input
          id="profile-markdown-file"
          type="file"
          accept=".md,.markdown,text/markdown,text/plain"
          onChange={(e) => {
            const file = e.target.files?.[0];
            setFileError(null);
            if (!file) return;
            file.text().then(setMarkdown, () => setFileError('Não foi possível ler o arquivo.'));
          }}
        />
      </div>
      <div className="field">
        <label htmlFor="profile-markdown">Ou cole o Markdown</label>
        <textarea
          className="input"
          id="profile-markdown"
          rows={8}
          value={markdown}
          onChange={(e) => setMarkdown(e.target.value)}
        />
      </div>
      {failure && (
        <p className="alert dsh" role="alert">
          {failure} Você pode preencher o perfil manualmente; ele é salvo no mesmo formato.
        </p>
      )}
      <div className="actions">
        <button
          type="submit"
          className="btn bt bt-pri"
          disabled={preview.isPending || !markdown.trim()}
        >
          {preview.isPending ? 'Lendo…' : 'Ler e revisar'}
        </button>
        <button type="button" className="btn bt bt-sec dsh" onClick={onManual}>
          Preencher manualmente
        </button>
      </div>
    </form>
  );
}

export function Profile() {
  const query = useQuery({
    queryKey: PROFILE_KEY,
    queryFn: ({ signal }) => getProfile(signal),
  });
  const [session, setSession] = useState<(EditorSession & { version: number }) | null>(null);
  const [importing, setImporting] = useState(false);
  const [saved, setSaved] = useState<ProfileView | null>(null);
  const profile = query.data?.profile ?? null;
  const refresh = useReassessMutation({
    onSuccess: (result) =>
      setSaved(
        (view) =>
          view && {
            ...view,
            assessment: { ...view.assessment, ...result, stale: result.state !== 'ready' },
          },
      ),
  });

  function start(next: EditorSession) {
    setSaved(null);
    setImporting(false);
    const suggested = query.data?.priorityCompanies ?? [];
    const prefilled =
      !profile && !next.initial.priorities && suggested.length > 0
        ? { priorities: suggested.join('\n') }
        : undefined;
    setSession({ ...next, prefilled, version: profile?.version ?? 0 });
  }

  return (
    <div className="stack">
      <PageHeader title="Seu perfil" eyebrow="A busca começa pelo que você sabe">
        {profile && !session && (
          <>
            <button
              className="btn bt bt-sec dsh"
              onClick={() => {
                setSaved(null);
                setImporting(!importing);
              }}
              aria-expanded={importing}
            >
              Importar Markdown
            </button>
            <button
              className="btn bt bt-pri"
              onClick={() =>
                start({
                  origin: profile.origin,
                  initial: toForm(profile),
                  warnings: [],
                  provenance: profile.provenance,
                  imported: false,
                })
              }
            >
              Editar perfil
            </button>
          </>
        )}
      </PageHeader>
      <ProfileNav />
      <p className="muted">
        Cargo, tecnologias e níveis orientam a busca e o ranking. Cada pessoa define os próprios
        critérios.
      </p>
      <QueryState pending={query.isPending} error={query.error} retry={query.refetch} />
      {saved && (
        <div className="notice dsh" role="status">
          <p>✓ Perfil salvo (v{saved.profile?.version}).</p>
          <p>{describeProfileAssessmentStatus(saved.assessment)}</p>
          {refresh.isPending && <p>Reavaliando as vagas com o perfil salvo…</p>}
          {refresh.error && <p role="alert">{refresh.error.message}</p>}
          <NavLink to="/vagas?list=recommended">Ver vagas para seu perfil</NavLink>
          {' · '}
          <NavLink to="/criterios">Acompanhar a avaliação nos critérios →</NavLink>
        </div>
      )}
      {query.data &&
        (session ? (
          <ProfileEditor
            session={session}
            key={session.version}
            version={session.version}
            onSaved={(view) => {
              setSession(null);
              setSaved(view);
              if (view.assessment.state === 'pending')
                refresh.mutate(view.assessment.criteriaVersion);
            }}
            onCancel={() => setSession(null)}
            onReload={async () => {
              const result = await query.refetch();
              const current = result.data?.profile;
              if (result.isError || !current)
                throw new Error('Não foi possível recarregar o perfil.');
              setSession({
                origin: current.origin,
                initial: toForm(current),
                warnings: [],
                provenance: current.provenance,
                imported: false,
                version: current.version,
              });
            }}
          />
        ) : (
          <>
            {(!profile || importing) && (
              <MarkdownImport onDraft={start} onManual={() => start(manualSession())} />
            )}
            {profile ? (
              <ProfileSummary profile={profile} />
            ) : (
              <Empty>
                Nenhum perfil salvo ainda. Importe um currículo ou preencha manualmente.
              </Empty>
            )}
          </>
        ))}
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
