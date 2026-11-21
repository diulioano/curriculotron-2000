import { describe, expect, it } from 'vitest';
import type { CompatibilityEvidenceItem } from '../../src/api/compatibility-detail';
import { profileMatchFor } from '../../src/lib/profile-match';

const criteria = (stackKeywords: string[]) => ({ targetRoles: [], stackKeywords });

function evidence(overrides: Partial<CompatibilityEvidenceItem>): CompatibilityEvidenceItem {
  return {
    source: 'lever:acme',
    sourceUrl: null,
    fetchedAt: null,
    field: 'description',
    section: null,
    quote: 'trecho',
    matchedCriterion: null,
    observedTerm: null,
    mentionKind: 'required',
    observedVersion: null,
    configuredVersion: null,
    ...overrides,
  };
}

describe('profileMatchFor', () => {
  it('não confunde Java com JavaScript nem C com C# ou C++', () => {
    expect(profileMatchFor('JavaScript', criteria(['Java']))).toBe('missing_from_profile');
    expect(profileMatchFor('C#', criteria(['C']))).toBe('missing_from_profile');
    expect(profileMatchFor('C++', criteria(['C']))).toBe('missing_from_profile');
    expect(profileMatchFor('C', criteria(['C#']))).toBe('missing_from_profile');
    expect(profileMatchFor('Node.js', criteria(['Node']))).toBe('missing_from_profile');
  });

  it('reconhece o termo inteiro, sem acento/caixa e ignorando sufixo de versão', () => {
    expect(profileMatchFor('Java 17 obrigatório', criteria(['java']))).toBe('matched');
    expect(profileMatchFor('Java', criteria(['Java 21']))).toBe('matched');
    expect(profileMatchFor('C#', criteria(['C#']))).toBe('matched');
    expect(profileMatchFor('Experiência com Go e Kafka', criteria(['Go']))).toBe('matched');
  });

  it.each([
    ['Python', 'Python 3.12', 'matched'],
    ['Java', 'Java 17+', 'matched'],
    ['Java', 'Java 17 LTS', 'missing_from_profile'],
    ['Java', 'Java 17.', 'missing_from_profile'],
    ['Java', 'Java 17++', 'missing_from_profile'],
  ])('trata o sufixo de versão de "%s" com critério "%s" como %s', (text, keyword, expected) => {
    expect(profileMatchFor(text, criteria([keyword]))).toBe(expected);
  });

  it('não casa palavra curta dentro de outra palavra', () => {
    expect(profileMatchFor('Google Cloud', criteria(['Go']))).toBe('missing_from_profile');
    expect(profileMatchFor('Trabalho com algoritmos e cargos', criteria(['Go']))).toBeNull();
  });

  it('usa apenas evidência positiva do backend vinculada a um critério', () => {
    expect(
      profileMatchFor('Kotlin', criteria([]), [
        evidence({ matchedCriterion: 'Kotlin', observedTerm: 'Kotlin' }),
      ]),
    ).toBe('matched');
    expect(
      profileMatchFor('Kotlin', criteria(['Java']), [
        evidence({ matchedCriterion: 'Kotlin', observedTerm: 'Kotlin', mentionKind: 'negated' }),
      ]),
    ).toBe('missing_from_profile');
    expect(
      profileMatchFor('Kotlin', criteria(['Java']), [
        evidence({ matchedCriterion: null, observedTerm: 'Kotlin' }),
      ]),
    ).toBe('missing_from_profile');
  });

  it('não afirma ausência em texto longo nem sem critérios configurados', () => {
    expect(
      profileMatchFor(
        'Atuar com times multidisciplinares em produto digital, inglês avançado',
        criteria(['Java']),
      ),
    ).toBeNull();
    expect(profileMatchFor('Kotlin', null)).toBeNull();
    expect(profileMatchFor('Kotlin', criteria([]))).toBeNull();
    expect(profileMatchFor('   ', criteria(['Java']))).toBeNull();
  });
});
