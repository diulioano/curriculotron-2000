import { Link } from 'react-router';
import { describeCompatibility } from '../api/compatibility';
import type {
  CompatibilityEvidenceItem,
  EvidenceField,
  MentionKind,
} from '../api/compatibility-detail';
import type { OpportunityDetail } from '../api/panel';
import { formatAsOf } from '../lib/time';

const safeUrl = (url: string | null): url is string => Boolean(url && /^https?:\/\//i.test(url));

const ROLE_LABELS = {
  matched: 'Compatível com cargo configurado',
  mismatched: 'Função incompatível com os cargos configurados',
  unknown: 'Função compatível não confirmada',
} as const;

const TECH_LABELS = {
  matched: 'Tecnologia compatível confirmada',
  mismatched: 'Tecnologias incompatíveis com o perfil',
  unknown: 'Tecnologia não confirmada (descrição insuficiente ou contexto incerto)',
  unrestricted: 'Sem restrição tecnológica no perfil',
} as const;

const FIELD_LABELS: Record<EvidenceField, string> = {
  title: 'Título',
  description: 'Descrição',
};

const MENTION_LABELS: Record<MentionKind, { label: string; tone: 'ok' | 'warn' | 'muted' }> = {
  required: { label: 'Requisito obrigatório', tone: 'ok' },
  optional: { label: 'Diferencial / desejável (não obrigatório)', tone: 'warn' },
  usage: { label: 'Uso informado na função', tone: 'ok' },
  negated: { label: 'Negação na vaga (não conta como aderência)', tone: 'muted' },
  incidental: {
    label: 'Menção incidental / outra equipe (não conta como aderência)',
    tone: 'muted',
  },
  uncertain: { label: 'Contexto incerto (exige confirmação)', tone: 'warn' },
};

function hasInsufficientDescription(item: OpportunityDetail): boolean {
  const codes = item.compatibility?.reasonCodes ?? [];
  if (
    codes.includes('technology_not_confirmed') ||
    codes.includes('role_not_confirmed') ||
    codes.includes('missing_description')
  ) {
    return true;
  }
  return item.missing.some((text) => /descrição completa/i.test(text));
}

function EvidenceEntry({ entry }: { entry: CompatibilityEvidenceItem }) {
  const mention = MENTION_LABELS[entry.mentionKind];
  const hasVersionInfo = entry.observedVersion !== null || entry.configuredVersion !== null;
  const versionDiffers = hasVersionInfo && entry.observedVersion !== entry.configuredVersion;

  return (
    <li className="compat-evidence-item">
      <div className="compat-evidence-meta">
        <span className={`job-compatibility mono compatibility-${mention.tone}`}>
          {mention.label}
        </span>
        <span className="mono small">Campo: {FIELD_LABELS[entry.field]}</span>
        {entry.section && <span className="mono small">Seção: {entry.section}</span>}
      </div>

      {(entry.matchedCriterion || entry.observedTerm) && (
        <p className="compat-evidence-terms">
          {entry.matchedCriterion && (
            <span>
              Critério: <strong>{entry.matchedCriterion}</strong>
            </span>
          )}
          {entry.matchedCriterion && entry.observedTerm && ' · '}
          {entry.observedTerm && (
            <span>
              Observado na vaga: <strong>{entry.observedTerm}</strong>
            </span>
          )}
        </p>
      )}

      {hasVersionInfo && (
        <p className="compat-version-note mono small">
          Versão na vaga: {entry.observedVersion ?? 'não informada'} · configurada:{' '}
          {entry.configuredVersion ?? 'não informada'}
          {versionDiffers && ' — Diferença de versão explícita (sem presumir experiência)'}
        </p>
      )}

      <blockquote className="compat-quote">“{entry.quote}”</blockquote>

      <p className="compat-origin mono small muted">
        {safeUrl(entry.sourceUrl) ? (
          <a href={entry.sourceUrl} target="_blank" rel="noopener noreferrer">
            fonte: {entry.source} ↗
          </a>
        ) : (
          <span>fonte: {entry.source} · link indisponível</span>
        )}
        {entry.fetchedAt && (
          <>
            {' · consultado '}
            <time dateTime={entry.fetchedAt}>{formatAsOf(entry.fetchedAt)}</time>
          </>
        )}
      </p>
    </li>
  );
}

export function CompatibilityEvidence({ item }: { item: OpportunityDetail }) {
  const summary = item.compatibility;
  const overview = summary ? describeCompatibility(summary) : null;
  const notAssessed = Boolean(summary?.reasonCodes.includes('not_assessed'));
  const insufficient = hasInsufficientDescription(item);

  return (
    <section className="compat-panel sk" aria-labelledby="compatibilidade-perfil">
      <div className="compat-panel-head">
        <h2 id="compatibilidade-perfil">Compatibilidade com o perfil</h2>
        {overview && (
          <p className={`job-compatibility mono compatibility-${overview.tone}`}>
            {overview.label}
          </p>
        )}
      </div>

      {summary && !summary.current && (
        <div role="status" className="notice dsh">
          <p>
            {notAssessed
              ? 'Esta vaga ainda não foi avaliada contra os critérios atuais.'
              : `Avaliação desatualizada (critério v${summary.criteriaVersion ?? '?'}). Exibindo o resultado e o retrato dos critérios salvos nessa avaliação.`}
          </p>
          <Link to="/criterios">Ver critérios →</Link>
        </div>
      )}

      {summary && (
        <dl className="compat-axes">
          <div className="compat-axis">
            <dt className="summary-label mono">FUNÇÃO</dt>
            <dd>{ROLE_LABELS[summary.roleStatus]}</dd>
          </div>
          <div className="compat-axis">
            <dt className="summary-label mono">TECNOLOGIA</dt>
            <dd>{TECH_LABELS[summary.technologyStatus]}</dd>
          </div>
        </dl>
      )}

      {insufficient && (
        <p className="compat-gap dsh mono small">
          Descrição parcial ou ausente: lacunas não foram preenchidas por suposição.
          {item.missing.length > 0 && (
            <>
              {' '}
              Confira a seção <a href="#a-confirmar">A confirmar</a>.
            </>
          )}
        </p>
      )}

      <div className="compat-criteria">
        <h3>Critérios usados nesta avaliação</h3>
        {item.compatibilityCriteria ? (
          <dl className="compat-criteria-list">
            <div>
              <dt className="summary-label mono">CARGOS-ALVO</dt>
              <dd>
                {item.compatibilityCriteria.targetRoles.length > 0
                  ? item.compatibilityCriteria.targetRoles.join(', ')
                  : 'Nenhum cargo configurado'}
              </dd>
            </div>
            <div>
              <dt className="summary-label mono">TECNOLOGIAS</dt>
              <dd>
                {item.compatibilityCriteria.stackKeywords.length > 0
                  ? item.compatibilityCriteria.stackKeywords.join(', ')
                  : 'Sem restrição de tecnologia'}
              </dd>
            </div>
          </dl>
        ) : (
          <p className="muted small">
            Nenhum snapshot de critérios armazenado (vaga ainda não avaliada).
          </p>
        )}
      </div>

      <div className="compat-evidence-block">
        <h3>Evidências encontradas na vaga</h3>
        {item.compatibilityEvidence && item.compatibilityEvidence.length > 0 ? (
          <ul className="compat-evidence-list">
            {item.compatibilityEvidence.map((entry, idx) => (
              <EvidenceEntry key={`${entry.source}-${entry.field}-${idx}`} entry={entry} />
            ))}
          </ul>
        ) : (
          <p className="muted small">
            Nenhuma menção explícita de cargo ou tecnologia foi registrada nas evidências desta
            avaliação.
          </p>
        )}
      </div>
    </section>
  );
}
