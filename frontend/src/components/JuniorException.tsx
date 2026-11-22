import type {
  JuniorExceptionDecision,
  JuniorSalaryCheck,
  OpportunityDetailData,
} from '../api/compatibility-detail';
import type { CareerProfile } from '../api/profile';
import { legacyJuniorException } from './junior-exception-legacy';

const BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

type Check = { label: string; ok: boolean; detail: string };
type Decided = Extract<JuniorExceptionDecision, { status: 'qualified' | 'excluded' }>;

const WORK_LABEL = 'Trabalho aderente';
const SALARY_LABEL = 'Remuneração confirmada acima do piso';
const JUSTIFICATION_LABEL = 'Justificativa da empresa';

function workCheck(decision: Decided): Check {
  return {
    label: WORK_LABEL,
    ok: decision.workFit,
    detail: decision.workFit
      ? 'Cargo e tecnologia da vaga correspondem ao seu perfil.'
      : 'Cargo ou tecnologia não confirmados como compatíveis com o seu perfil.',
  };
}

function salaryDetail(salary: JuniorSalaryCheck): string {
  const regime = salary.contract === 'clt' ? 'CLT' : salary.contract === 'pj' ? 'PJ' : null;
  if (salary.status === 'contract_unknown' || !regime) {
    return 'Regime da vaga (CLT ou PJ) não informado.';
  }
  if (salary.status === 'floor_missing' || salary.floor === null) {
    return `Você não definiu piso Júnior ${regime}.`;
  }
  const floorText = `piso Júnior ${regime} de ${BRL.format(salary.floor)}/mês`;
  switch (salary.status) {
    case 'confirmed':
      return `Mínimo anunciado de ${BRL.format(salary.announcedMin ?? salary.floor)} atinge o ${floorText}.`;
    case 'range_crosses_floor':
      return `A faixa anunciada cruza o ${floorText}; o mínimo não confirma o valor.`;
    case 'below_floor':
      return `Salário anunciado fica abaixo do ${floorText}.`;
    case 'not_announced':
      return salary.source === 'estimate'
        ? `Só há estimativa salarial para esta vaga; estimativas não confirmam o ${floorText}.`
        : `Salário mensal não anunciado; estimativas não confirmam o ${floorText}.`;
  }
}

function justificationCheck(decision: Decided, known = true): Check {
  const { status, text } = decision.justification;
  if (status === 'accepted' && text) return { label: JUSTIFICATION_LABEL, ok: true, detail: text };
  let detail: string;
  if (status === 'not_accepted_by_criteria') {
    detail = `Os critérios atuais não aceitam vagas Júnior. Justificativa registrada: ${text ?? ''}`;
  } else if (known) {
    detail =
      'Nenhuma justificativa registrada para esta empresa. Ser prioritária, sozinha, não qualifica.';
  } else {
    detail = 'A avaliação atual não informa a justificativa desta empresa.';
  }
  return { label: JUSTIFICATION_LABEL, ok: false, detail };
}

function checksOf(decision: Decided, justificationKnown = true): Check[] {
  return [
    workCheck(decision),
    {
      label: SALARY_LABEL,
      ok: decision.salary.status === 'confirmed',
      detail: salaryDetail(decision.salary),
    },
    justificationCheck(decision, justificationKnown),
  ];
}

interface View {
  checks: Check[];
  qualifies: boolean | null;
  serviceReason: string | null;
}

function viewOf(item: OpportunityDetailData, profile: CareerProfile): View | null {
  const excludesJunior = !profile.targetLevels.includes('junior');
  const decision = item.juniorException;
  if (decision === null) {
    // Resposta sem o campo tipado: apenas compatibilidade com fixtures antigas.
    if (!excludesJunior || item.seniority.status === 'absent' || item.seniority.label !== 'Júnior')
      return null;
    const legacy = legacyJuniorException(item, profile);
    return {
      checks: checksOf(legacy.decision, legacy.justificationKnown),
      qualifies: legacy.qualifies,
      serviceReason: legacy.serviceReason,
    };
  }
  if (decision.status === 'not_applicable') return null;
  if (decision.status === 'unknown') {
    if (!excludesJunior || item.seniority.label !== 'Júnior') return null;
    return { checks: [], qualifies: null, serviceReason: null };
  }
  return {
    checks: checksOf(decision),
    qualifies: item.compatibility.current ? decision.status === 'qualified' : null,
    serviceReason: null,
  };
}

/**
 * Explica as três condições da exceção Júnior para quem busca Pleno/Sênior com a decisão que o
 * serviço calculou no mesmo nível usado pelo ranking; o componente não reinterpreta motivos.
 */
export function JuniorException({
  item,
  profile,
}: {
  item: OpportunityDetailData;
  profile: CareerProfile;
}) {
  const view = viewOf(item, profile);
  if (!view) return null;
  const { checks, qualifies, serviceReason } = view;
  return (
    <section className="why sk" aria-labelledby="excecao-junior">
      <h2 id="excecao-junior">Exceção Júnior</h2>
      <p>
        Vaga Júnior fora dos níveis que você busca.{' '}
        {checks.length === 0
          ? 'Sem dados suficientes para decidir a exceção Júnior.'
          : qualifies === null
            ? 'A avaliação da vaga não confirmou uma decisão pela exceção Júnior.'
            : qualifies
              ? 'Atende às três condições da exceção.'
              : 'Não atende a todas as condições da exceção e fica fora da seleção principal.'}
      </p>
      {checks.length > 0 && (
        <ul className="exception-checks">
          {checks.map((check) => (
            <li key={check.label}>
              <span aria-hidden="true">{check.ok ? '✓' : '✕'}</span> <strong>{check.label}</strong>
              <span className="visually-hidden">
                {check.ok ? ' (atendida)' : ' (não atendida)'}
              </span>
              : {check.detail}
            </li>
          ))}
        </ul>
      )}
      {serviceReason && <p className="muted small">Avaliação do serviço: {serviceReason}</p>}
    </section>
  );
}
