import type { CSSProperties } from 'react';
import { Link } from 'react-router';
import { describeRefreshStatus } from '../api/compatibility';
import { useCoordinatedHomeQuery } from '../api/useCompatibilityRefresh';
import {
  type Application,
  type HomeData,
  type Integration,
  type PendingReviewItem,
} from '../api/panel';
import { Empty, QueryState } from '../components/Panel';
import { OpportunityCard, type CardLayout } from '../components/OpportunityCard';
import { SurfaceStateBlock } from '../components/SurfaceState';
import { companyColor } from '../lib/opportunity';
import { APPLICATION_STATUSES } from '../lib/labels';
import { formatAsOf, ZONE } from '../lib/time';
import './home.css';

/** Ritmo do design: cartões de larguras, inclinações e títulos diferentes. */
const LAYOUTS: CardLayout[] = [
  { flex: '2 1 520px', rotate: -0.8, titleSize: 28 },
  { flex: '1 1 300px', rotate: 0.9, titleSize: 21 },
  { flex: '1 1 340px', rotate: -0.5, titleSize: 20 },
  { flex: '1.6 1 440px', rotate: 0.6, titleSize: 25 },
  { flex: '1 1 260px', rotate: -1.2, titleSize: 19 },
];

const URGENT_MS = 48 * 3_600_000;
/** No celular o design mostra só as três primeiras vagas. */
const MOBILE_CARDS = 3;

function integrationName(id: string) {
  if (id === 'openbot') return 'OpenBot';
  if (id === 'gmail') return 'Gmail';
  if (id.startsWith('source:')) return `Fonte ${id.slice('source:'.length)}`;
  return id;
}
const STATE_TEXT: Record<Integration['state'], string> = {
  ok: '✓',
  degraded: 'com falhas',
  disconnected: 'desconectado',
  paused: 'pausado',
};

function greeting(now: Date) {
  const hour = Number(
    now.toLocaleTimeString('pt-BR', { timeZone: ZONE, hour: '2-digit', hourCycle: 'h23' }),
  );
  return hour < 12 ? 'Bom dia.' : hour < 18 ? 'Boa tarde.' : 'Boa noite.';
}

function dueText(item: Application) {
  if (!item.nextActionDueAt || item.duePrecision === 'ambiguous') return 'prazo a confirmar';
  const at = item.nextActionDueAt;
  const date = new Date(at.length === 10 ? `${at}T23:59:59-03:00` : at);
  const weekday = date
    .toLocaleDateString('pt-BR', { timeZone: ZONE, weekday: 'short' })
    .replace('.', '');
  const day = date.toLocaleDateString('pt-BR', {
    timeZone: ZONE,
    day: '2-digit',
    month: '2-digit',
  });
  const time =
    item.duePrecision === 'exact' && at.length > 10
      ? ` às ${date.toLocaleTimeString('pt-BR', { timeZone: ZONE, hour: '2-digit', minute: '2-digit' })}`
      : '';
  return `${date.getTime() < Date.now() ? 'venceu ' : ''}${weekday}, ${day}${time}`;
}

/** Prazos (vagas e candidaturas) que vencem nas próximas 48 horas. */
function upcomingDeadlines(data: HomeData, now: number) {
  const dates = [
    ...data.selectionToday.items.map((item) => item.deadlineAt),
    ...data.applicationsWithNextActions
      .filter((item) => item.duePrecision !== 'ambiguous')
      .map((item) => item.nextActionDueAt),
  ];
  return dates.filter((at): at is string => {
    if (!at) return false;
    const time = new Date(at.length === 10 ? `${at}T23:59:59-03:00` : at).getTime();
    return time >= now && time - now <= URGENT_MS;
  }).length;
}

export function Home() {
  const query = useCoordinatedHomeQuery();
  const data = query.data;
  const visibleSelectionItems = data
    ? data.selectionToday.items.filter((item) => item.compatibility.current)
    : [];
  const now = new Date();
  const today = now.toLocaleDateString('pt-BR', {
    timeZone: ZONE,
    weekday: 'long',
    day: 'numeric',
    month: 'short',
  });
  return (
    <div className="home">
      {data && <SystemStrip data={data} />}

      <div className="home-hero">
        <div className="home-title">
          <p className="mono muted home-date">{today.replace('.', '')} · revisão de ~10 min</p>
          <h1>
            <span className="only-wide">
              {greeting(now)}
              <br />
              {data ? <Headline count={data.selectionToday.count} /> : 'Sua revisão do dia.'}
            </span>
            <span className="only-narrow">
              {data ? <ShortHeadline data={data} now={now.getTime()} /> : 'Sua revisão do dia.'}
            </span>
          </h1>
          <svg className="squiggle" width="420" height="16" viewBox="0 0 420 16" aria-hidden="true">
            <polyline points="2,10 30,4 58,12 86,3 114,11 142,5 170,12 198,4 226,11 254,5 282,12 310,4 338,10 366,5 394,11 418,6" />
          </svg>
        </div>
        {data && <Stats data={data} now={now.getTime()} />}
      </div>

      <QueryState pending={query.isPending} error={query.error} retry={query.refetch} />

      {data && <MobileAlerts integrations={data.integrations} />}
      {data &&
        (() => {
          const warning = describeRefreshStatus(data.assessment);
          return (
            warning && (
              <div className="notice dsh" role="status">
                {warning} <Link to="/criterios">Ver critérios →</Link>
              </div>
            )
          );
        })()}

      {data && (
        <div className="home-columns">
          <section className="home-main" aria-labelledby="vagas-do-dia">
            <div className="home-section-head">
              <h2 id="vagas-do-dia">
                Vagas do dia{' '}
                <span className="count">
                  {data.selectionToday.count}/{data.selectionToday.limit}
                </span>
              </h2>
              <p className="muted home-consumption">
                Seleção de hoje: consumidas {data.selectionToday.consumedCount} de{' '}
                {data.selectionToday.limit}
                {data.selectionToday.count !== data.selectionToday.consumedCount
                  ? ` (${data.selectionToday.count} visível(is) agora)`
                  : ''}
                . <Link to="/vagas?list=secondary">Ver a lista secundária →</Link>
              </p>
            </div>
            {visibleSelectionItems.length ? (
              <div className="jobs-flow">
                {visibleSelectionItems.map((item, index) => (
                  <OpportunityCard
                    key={item.id}
                    item={item}
                    layout={LAYOUTS[index % LAYOUTS.length]}
                    headingLevel={3}
                  />
                ))}
              </div>
            ) : (
              <Empty>
                Nenhuma vaga selecionada hoje.{' '}
                {data.assessment.stale && data.assessment.remainingCount > 0 ? (
                  <>
                    Vagas com avaliação pendente ou desatualizada ficam ocultas até a atualização.
                  </>
                ) : (
                  <Link to="/vagas">Abra Vagas para iniciar uma busca.</Link>
                )}
              </Empty>
            )}
            {visibleSelectionItems.length > MOBILE_CARDS && (
              <Link className="only-narrow more-jobs" to="/vagas">
                Ver as outras {visibleSelectionItems.length - MOBILE_CARDS} vagas do dia →
              </Link>
            )}
            <p className="muted home-footnote">
              Abrir o link da vaga não registra candidatura — só “Me candidatei” registra.
            </p>
          </section>

          <aside className="home-aside">
            <NeedsYou data={data} />
            <NextActions items={data.applicationsWithNextActions} />
            {data.integrations
              .filter((item) => item.state === 'disconnected' || item.state === 'degraded')
              .map((item) => (
                <IntegrationAlert key={item.id} item={item} />
              ))}
          </aside>
        </div>
      )}
    </div>
  );
}

function Headline({ count }: { count: number }) {
  if (count === 0) return <>Hoje nenhuma vaga passou no filtro.</>;
  return (
    <>
      Hoje tem <span className="hl">{count === 1 ? '1 vaga' : `${count} vagas`}</span>{' '}
      {count === 1 ? 'que vale a pena.' : 'que valem a pena.'}
    </>
  );
}

/** Versão curta do título no celular: "Hoje: 5 vagas, 3 pendências, 1 prazo." */
function ShortHeadline({ data, now }: { data: HomeData; now: number }) {
  const count = data.selectionToday.count;
  const pending = data.pendingReviewItems.length;
  const deadlines = upcomingDeadlines(data, now);
  const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;
  return (
    <>
      Hoje: <span className="hl">{plural(count, 'vaga', 'vagas')}</span>,
      <br />
      {plural(pending, 'pendência', 'pendências')}, {plural(deadlines, 'prazo', 'prazos')}.
    </>
  );
}

function MobileAlerts({ integrations }: { integrations: Integration[] }) {
  const broken = integrations.filter(
    (item) => item.state === 'disconnected' || item.state === 'degraded',
  );
  return broken.map((item) => (
    <p key={item.id} className="only-narrow mobile-alert dsh">
      <strong>
        {integrationName(item.id)} {item.state === 'disconnected' ? 'desconectado' : 'com falhas'}.
      </strong>{' '}
      {item.lastSuccessAt && <>Último sucesso: {formatAsOf(item.lastSuccessAt)}. </>}
      {item.detail} <Link to="/integracoes">Resolver</Link>
    </p>
  ));
}

function SystemStrip({ data }: { data: HomeData }) {
  return (
    <section aria-label="Estado do sistema" className="status-strip dsh-b mono">
      <span>última atualização real →</span>
      {data.integrations.map((item) =>
        item.state === 'ok' ? (
          <span key={item.id}>
            {integrationName(item.id)}{' '}
            {item.lastSuccessAt ? (
              <strong>
                <time dateTime={item.lastSuccessAt}>{formatAsOf(item.lastSuccessAt)}</time>
              </strong>
            ) : (
              <strong>sem sucesso ainda</strong>
            )}{' '}
            ✓
          </span>
        ) : (
          <span key={item.id} className={item.state === 'paused' ? undefined : 'strip-alert'}>
            {integrationName(item.id)} {STATE_TEXT[item.state]}
            {item.lastSuccessAt && (
              <>
                {' '}
                — último sucesso{' '}
                <time dateTime={item.lastSuccessAt}>{formatAsOf(item.lastSuccessAt)}</time>
              </>
            )}
          </span>
        ),
      )}
      {!data.integrations.length && (
        <span>
          {data.dataAsOf ? (
            <strong>
              <time dateTime={data.dataAsOf}>{formatAsOf(data.dataAsOf)}</time>
            </strong>
          ) : (
            'sem atualização bem-sucedida ainda'
          )}
        </span>
      )}
    </section>
  );
}

function pendingActionLabel(kind: PendingReviewItem['kind']) {
  switch (kind) {
    case 'dedupe_candidate':
      return 'Comparar anúncios →';
    case 'email_attention':
      return 'Ler e classificar →';
    case 'missed_routine':
      return 'Ver atividades →';
    default:
      return 'Abrir fila de revisão →';
  }
}

function Stats({ data, now }: { data: HomeData; now: number }) {
  const deadlines = upcomingDeadlines(data, now);
  return (
    <ul className="home-stats" aria-label="Resumo do dia">
      <li className="stat sk">
        <span className="stat-value">
          {data.selectionToday.count}
          <small>/{data.selectionToday.limit}</small>
        </span>
        <span>vagas do dia</span>
      </li>
      <li className="stat sk">
        <span className="stat-value">{data.pendingReviewItems.length}</span>
        <span>pedem você</span>
      </li>
      <li className={deadlines ? 'stat sk hl' : 'stat sk'}>
        <span className="stat-value">{deadlines}</span>
        <span>{deadlines === 1 ? 'prazo até amanhã' : 'prazos até amanhã'}</span>
      </li>
    </ul>
  );
}

function NeedsYou({ data }: { data: HomeData }) {
  const items = data.pendingReviewItems;
  const total = items.length;
  return (
    <section className="needs-you sk" aria-labelledby="precisa-de-voce">
      <h2 id="precisa-de-voce">
        Precisa de você <span className="count">({total})</span>
      </h2>
      {items.map((item) => (
        <SurfaceStateBlock
          key={item.id}
          kind="pending_review"
          subLabel={item.kindLabel}
          title={item.title}
          body={item.summary}
          action={{ label: pendingActionLabel(item.kind), to: item.targetRoute }}
        />
      ))}
      {total === 0 && <SurfaceStateBlock kind="empty" title="Nada esperando por você agora." />}
    </section>
  );
}

function NextActions({ items }: { items: Application[] }) {
  return (
    <section className="next-actions" aria-labelledby="proximas-acoes">
      <h2 id="proximas-acoes">Candidaturas · próximas ações</h2>
      {items.length ? (
        items.map((item) => (
          <div key={item.id} className="next-action">
            <span
              className="hatch next-bar"
              style={{ '--c': companyColor(item.companyName) } as CSSProperties}
              aria-hidden="true"
            />
            <div className="next-body">
              <Link to={`/candidaturas/${item.id}`} className="next-title">
                {item.companyName} · {item.nextAction ?? item.title}
              </Link>
              <span
                className={
                  item.nextActionDueAt && item.duePrecision !== 'ambiguous'
                    ? 'hl next-due'
                    : 'muted next-due'
                }
              >
                {dueText(item)}
              </span>
              <span className="mono muted next-meta">{APPLICATION_STATUSES[item.status]}</span>
            </div>
          </div>
        ))
      ) : (
        <p className="muted">Nenhuma próxima ação agendada.</p>
      )}
      <Link to="/candidaturas">Ver todas as candidaturas →</Link>
    </section>
  );
}

function IntegrationAlert({ item }: { item: Integration }) {
  const name = integrationName(item.id);
  return (
    <div className="integration-alert-wrap">
      <SurfaceStateBlock
        kind={item.state === 'disconnected' ? 'error' : 'partial'}
        titleAs="h2"
        title={`${name} ${item.state === 'disconnected' ? 'desconectado' : 'com falhas'}`}
        body={
          <>
            {item.detail && (
              <>
                <strong>O que aconteceu:</strong> {item.detail}
                <br />
              </>
            )}
            <strong>Último sucesso:</strong>{' '}
            {item.lastSuccessAt ? (
              <time dateTime={item.lastSuccessAt}>{formatAsOf(item.lastSuccessAt)}</time>
            ) : (
              'nenhum ainda'
            )}
            .
          </>
        }
        action={{ label: 'Resolver em Integrações', to: '/integracoes' }}
      />
    </div>
  );
}
