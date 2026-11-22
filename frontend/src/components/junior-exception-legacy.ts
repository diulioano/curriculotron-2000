import type {
  JuniorExceptionDecision,
  JuniorSalaryCheck,
  OpportunityDetailData,
} from '../api/compatibility-detail';
import type { CareerProfile } from '../api/profile';

/**
 * Compatibilidade com respostas do detalhe anteriores ao campo `juniorException` (fixtures
 * antigas): reconstrói uma decisão a partir dos dados da vaga e do perfil. Respostas atuais do
 * serviço trazem a decisão tipada e nunca passam por aqui. Remover quando não houver mais
 * respostas sem o campo.
 */
export interface LegacyJuniorException {
  decision: Extract<JuniorExceptionDecision, { status: 'qualified' | 'excluded' }>;
  /** `null`: a avaliação não confirmou um veredito. */
  qualifies: boolean | null;
  serviceReason: string | null;
  justificationKnown: boolean;
}

const EXCEPTION_PREFIXES = ['Exceção júnior:', 'Justificativa para júnior:'];
const EXCLUDED_PREFIX = 'Vaga júnior excluída';
const MISSING_JUSTIFICATION = 'justificativa júnior da empresa';

function legacySalary(item: OpportunityDetailData, profile: CareerProfile): JuniorSalaryCheck {
  const contract =
    item.contractType === 'clt' || item.contractType === 'pj' ? item.contractType : null;
  const floor =
    contract === 'clt'
      ? profile.juniorFloorCltMonthlyBrl
      : contract === 'pj'
        ? profile.juniorFloorPjMonthlyBrl
        : null;
  const min = item.salary?.min ?? null;
  const max = item.salary?.max ?? null;
  const monthly = item.salary?.unit === 'monthly' && (min !== null || max !== null);
  const base = {
    source: monthly ? ('announced' as const) : ('none' as const),
    contract,
    unit: item.salary?.unit ?? null,
    floor,
    announcedMin: min,
    announcedMax: max,
  };
  if (!contract) return { ...base, status: 'contract_unknown' };
  if (floor === null) return { ...base, status: 'floor_missing' };
  if (!monthly) return { ...base, status: 'not_announced' };
  if (min !== null && min >= floor) return { ...base, status: 'confirmed' };
  const top = max ?? min;
  if (top !== null && top >= floor) return { ...base, status: 'range_crosses_floor' };
  return { ...base, status: 'below_floor' };
}

export function legacyJuniorException(
  item: OpportunityDetailData,
  profile: CareerProfile,
): LegacyJuniorException {
  const { roleStatus, technologyStatus, current } = item.compatibility;
  const workFit =
    roleStatus === 'matched' &&
    (technologyStatus === 'matched' || technologyStatus === 'unrestricted');
  let text: string | null = null;
  for (const reason of item.fitReasons) {
    const prefix = EXCEPTION_PREFIXES.find((p) => reason.startsWith(p));
    const value = prefix ? reason.slice(prefix.length).trim() : '';
    if (value) {
      text = value;
      break;
    }
  }
  const excluded = item.fitReasons.find((r) => r.startsWith(EXCLUDED_PREFIX)) ?? null;
  const missingJustification = excluded?.includes(MISSING_JUSTIFICATION) ?? false;
  const qualified = item.fitReasons.some((r) => r.startsWith(EXCEPTION_PREFIXES[0]));
  const qualifies = current ? (excluded ? false : qualified ? true : null) : null;
  return {
    decision: {
      status: qualifies ? 'qualified' : 'excluded',
      workFit,
      salary: legacySalary(item, profile),
      justification: { status: text ? 'accepted' : 'missing', text },
    },
    qualifies,
    serviceReason: current ? excluded : null,
    justificationKnown: text !== null || missingJustification,
  };
}
