import type {
  CompatibilityCriteriaSnapshot,
  CompatibilityEvidenceItem,
} from '../api/compatibility-detail';

export type ProfileMatch = 'matched' | 'missing_from_profile' | null;

const POSITIVE_MENTIONS = new Set(['required', 'usage', 'optional']);
/** Um fato só é tratado como requisito isolado (e pode ser marcado como ausente) se for curto. */
const MAX_SINGLE_REQUIREMENT_WORDS = 3;
const MAX_SINGLE_REQUIREMENT_LENGTH = 32;

function normalize(value: string): string {
  return value.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/\s+/g, ' ').trim();
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Termo inteiro, sem coincidência por partes de nome (spec 004):
 * "Java" não casa com "JavaScript", "C" não casa com "C#"/"C++", "Node" não casa com "Node.js".
 */
function containsWholeTerm(text: string, term: string): boolean {
  const pattern = new RegExp(
    `(?<![\\p{L}\\p{N}.#+])${escapeRegExp(term)}(?![\\p{L}\\p{N}+#]|\\.[\\p{L}\\p{N}])`,
    'u',
  );
  return pattern.test(text);
}

function isVersionToken(token: string): boolean {
  const core = token.endsWith('+') ? token.slice(0, -1) : token;
  if (core.length === 0) return false;
  return core.split('.').every((part) => /^\d+$/.test(part));
}

function withoutVersion(term: string): string {
  for (let i = term.length - 1; i >= 0; i -= 1) {
    if (term[i].trim() === '') {
      const suffix = term.slice(i + 1);
      if (isVersionToken(suffix)) {
        return term.slice(0, i).trim();
      }
      break;
    }
  }
  return term.trim();
}

function profileTerms(
  criteria: CompatibilityCriteriaSnapshot,
  evidence: readonly CompatibilityEvidenceItem[],
): string[] {
  const fromEvidence = evidence
    .filter((ev) => POSITIVE_MENTIONS.has(ev.mentionKind) && ev.matchedCriterion)
    .flatMap((ev) => [ev.matchedCriterion, ev.observedTerm])
    .filter((term): term is string => Boolean(term));
  const terms = [...criteria.stackKeywords, ...fromEvidence]
    .map(normalize)
    .flatMap((term) => [term, withoutVersion(term)])
    .filter((term) => term.length > 0);
  return [...new Set(terms)];
}

function isSingleRequirement(fact: string): boolean {
  return (
    fact.length <= MAX_SINGLE_REQUIREMENT_LENGTH &&
    fact.split(' ').length <= MAX_SINGLE_REQUIREMENT_WORDS &&
    !/[,;:.!?]\s/.test(fact)
  );
}

/**
 * Indica se um requisito declarado aparece nas tecnologias do perfil.
 * Só afirma `missing_from_profile` para requisitos curtos e isolados; textos longos
 * ou sem critérios configurados não recebem marcador, evitando afirmar ausência sem base.
 */
export function profileMatchFor(
  fact: string,
  criteria: CompatibilityCriteriaSnapshot | null,
  evidence: readonly CompatibilityEvidenceItem[] = [],
): ProfileMatch {
  if (!criteria) return null;
  const normalizedFact = normalize(fact);
  if (!normalizedFact) return null;
  const terms = profileTerms(criteria, evidence);
  if (terms.length === 0) return null;
  if (terms.some((term) => containsWholeTerm(normalizedFact, term))) return 'matched';
  return isSingleRequirement(normalizedFact) ? 'missing_from_profile' : null;
}
