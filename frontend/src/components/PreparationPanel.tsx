import { Link } from 'react-router';
import type { Preparation, PreparationItem, PreparationTier } from '../api/profile';
import { SurfaceStateBlock } from './SurfaceState';

const GROUPS = [
  ['required', 'Exigência obrigatória'],
  ['optional', 'Diferencial'],
  ['mentioned', 'Mencionada pela equipe'],
] as const;

const TIER_LABELS: Record<PreparationTier, string> = {
  primary: 'sua tecnologia principal',
  secondary: 'no perfil como secundária',
  flexible: 'no perfil como preferência flexível',
  none: 'não consta no perfil',
};

const STUDY_REASON = {
  required: 'O anúncio exige essa competência. Priorize esse estudo para a vaga.',
  optional:
    'O anúncio cita essa competência como diferencial. Ela pode fortalecer sua candidatura.',
  mentioned: 'A equipe menciona essa tecnologia. Conhecê-la ajuda a entender o trabalho descrito.',
};

function Item({ item, group }: { item: PreparationItem; group: keyof typeof STUDY_REASON }) {
  const study = item.profileTier === 'none';
  return (
    <li className={study ? 'prep-item study' : 'prep-item'}>
      <strong>{item.technology}</strong>{' '}
      <span className="meta">
        — {study ? `${TIER_LABELS.none}: estudar` : TIER_LABELS[item.profileTier]}
      </span>
      {study && <p>{STUDY_REASON[group]}</p>}
      <blockquote className="prep-quote">
        <span className="meta">Evidência no anúncio:</span> “{item.quote}”
      </blockquote>
    </li>
  );
}

/** Itens de estudo vinculados ao trecho do anúncio, separados pelo peso da exigência. */
export function PreparationPanel({ preparation }: { preparation: Preparation | null }) {
  if (!preparation) {
    return (
      <SurfaceStateBlock
        kind="empty"
        title="Preparação indisponível"
        body="O serviço local ainda não informou os itens de estudo desta vaga."
      />
    );
  }
  const total = GROUPS.reduce((sum, [key]) => sum + preparation[key].length, 0);
  return (
    <div className="stack compact">
      {preparation.profileVersion === null ? (
        <p className="muted small">
          Sem perfil salvo, todas as tecnologias aparecem como “não consta no perfil”.{' '}
          <Link to="/perfil">Cadastrar perfil →</Link>
        </p>
      ) : (
        <p className="muted small">
          Comparado com o seu perfil v{preparation.profileVersion}.{' '}
          <Link to="/perfil">Editar perfil →</Link>
        </p>
      )}
      {total === 0 && (
        <SurfaceStateBlock
          kind="empty"
          title="Nenhuma tecnologia identificada no anúncio."
          body="Sem trecho do anúncio, não há item de estudo a sugerir."
        />
      )}
      {GROUPS.map(([key, label]) =>
        preparation[key].length > 0 ? (
          <section key={key} aria-label={label}>
            <h3>{label}</h3>
            <ul className="prep-list">
              {preparation[key].map((item) => (
                <Item key={item.technology} item={item} group={key} />
              ))}
            </ul>
          </section>
        ) : null,
      )}
    </div>
  );
}
