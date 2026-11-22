/** Contrato da US006 (specs/006-perfil-descoberta-estudo/api.md): perfil de carreira e preparação. */
import { api } from './client';
import { parseRefreshStatus, type RefreshStatus } from './compatibility';

export const SENIORITIES = ['junior', 'pleno', 'senior'] as const;
export type Seniority = (typeof SENIORITIES)[number];
export const SENIORITY_LABELS: Record<Seniority, string> = {
  junior: 'Júnior',
  pleno: 'Pleno',
  senior: 'Sênior',
};

const PROFILE_ORIGINS = ['markdown', 'manual'] as const;
export type ProfileOrigin = (typeof PROFILE_ORIGINS)[number];

/** Campos editáveis comuns ao rascunho importado e ao perfil salvo. */
export interface ProfileFields {
  role: string;
  primaryTechnology: string;
  secondaryTechnologies: string[];
  flexibleTechnologies: string[];
  currentSeniority: Seniority;
  targetLevels: Seniority[];
  priorities: string[];
  juniorFloorCltMonthlyBrl: number | null;
  juniorFloorPjMonthlyBrl: number | null;
}

export interface CareerProfile extends ProfileFields {
  version: number;
  origin: ProfileOrigin;
  /** Origem por campo; vazio quando o perfil foi salvo sem esses metadados. */
  provenance: ProfileProvenance;
  updatedAt: string;
}

export interface ProfileView {
  priorityCompanies?: string[];
  profile: CareerProfile | null;
  assessment: RefreshStatus;
}

export type DraftField = keyof ProfileFields;
export const DRAFT_FIELDS: readonly DraftField[] = [
  'role',
  'primaryTechnology',
  'secondaryTechnologies',
  'flexibleTechnologies',
  'currentSeniority',
  'targetLevels',
  'priorities',
  'juniorFloorCltMonthlyBrl',
  'juniorFloorPjMonthlyBrl',
];

/** Rascunho: o Markdown pode não trazer cargo, tecnologia principal ou nível atual. */
export type ProfileDraft = Omit<
  ProfileFields,
  'role' | 'primaryTechnology' | 'currentSeniority'
> & {
  role: string | null;
  primaryTechnology: string | null;
  currentSeniority: Seniority | null;
};

const FIELD_SOURCES = ['imported', 'inferred', 'edited', 'declared'] as const;
/** imported = lido do Markdown; inferred = deduzido; edited = corrigido; declared = informado. */
export type FieldSource = (typeof FIELD_SOURCES)[number];
/** `line`/`excerpt` apontam a linha do Markdown usada como evidência (null sem evidência). */
export interface FieldProvenance {
  source: FieldSource;
  line: number | null;
  excerpt: string | null;
}
export type ProfileProvenance = Partial<Record<DraftField, FieldProvenance>>;

const WARNING_CODES = ['missing', 'inferred', 'invalid'] as const;
export type DraftWarningCode = (typeof WARNING_CODES)[number];
export interface DraftWarning {
  field: DraftField;
  code: DraftWarningCode;
  message: string;
}
export interface ProfilePreview {
  draft: ProfileDraft;
  warnings: DraftWarning[];
  provenance: ProfileProvenance;
}

export type SavePayload = Omit<CareerProfile, 'updatedAt'>;

const PREPARATION_TIERS = ['primary', 'secondary', 'flexible', 'none'] as const;
export type PreparationTier = (typeof PREPARATION_TIERS)[number];
export interface PreparationItem {
  technology: string;
  profileTier: PreparationTier;
  quote: string;
}
export interface Preparation {
  profileVersion: number | null;
  required: PreparationItem[];
  optional: PreparationItem[];
  mentioned: PreparationItem[];
}

function asRecord(raw: unknown, name: string): Record<string, unknown> {
  if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) {
    throw new Error(`Formato inválido para ${name}.`);
  }
  return raw as Record<string, unknown>;
}

function asString(raw: unknown, field: string): string {
  if (typeof raw !== 'string') throw new Error(`Campo ${field} inválido.`);
  return raw;
}

function asNullableString(raw: unknown, field: string): string | null {
  return raw === null || raw === undefined ? null : asString(raw, field);
}

function asStringArray(raw: unknown, field: string): string[] {
  if (!Array.isArray(raw)) throw new Error(`Lista ${field} inválida.`);
  return raw.map((item, i) => asString(item, `${field}[${i}]`));
}

function asEnum<T extends string>(raw: unknown, allowed: readonly T[], field: string): T {
  if (typeof raw !== 'string' || !allowed.includes(raw as T)) {
    throw new Error(`Valor inesperado para ${field}.`);
  }
  return raw as T;
}

function asSeniorities(raw: unknown, field: string): Seniority[] {
  if (!Array.isArray(raw)) throw new Error(`Lista ${field} inválida.`);
  return raw.map((item, i) => asEnum(item, SENIORITIES, `${field}[${i}]`));
}

function asInt(raw: unknown, field: string, min: number): number {
  if (typeof raw !== 'number' || !Number.isInteger(raw) || raw < min) {
    throw new Error(`Campo ${field} inválido.`);
  }
  return raw;
}

/** O contrato declara o piso como decimal BRL; aceita número JSON ou texto decimal. */
function asMoney(raw: unknown, field: string): number | null {
  if (raw === null || raw === undefined) return null;
  const value = typeof raw === 'string' && raw.trim() !== '' ? Number(raw) : raw;
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) {
    throw new Error(`Campo ${field} inválido.`);
  }
  return value;
}

function parseLists(obj: Record<string, unknown>) {
  return {
    secondaryTechnologies: asStringArray(obj.secondaryTechnologies, 'secondaryTechnologies'),
    flexibleTechnologies: asStringArray(obj.flexibleTechnologies, 'flexibleTechnologies'),
    targetLevels: asSeniorities(obj.targetLevels, 'targetLevels'),
    priorities: asStringArray(obj.priorities, 'priorities'),
    juniorFloorCltMonthlyBrl: asMoney(obj.juniorFloorCltMonthlyBrl, 'juniorFloorCltMonthlyBrl'),
    juniorFloorPjMonthlyBrl: asMoney(obj.juniorFloorPjMonthlyBrl, 'juniorFloorPjMonthlyBrl'),
  };
}

/** Servidores anteriores não enviam `provenance`; ausência vira objeto vazio. */
export function parseProvenance(raw: unknown, name: string): ProfileProvenance {
  if (raw === undefined || raw === null) return {};
  const obj = asRecord(raw, name);
  const result: ProfileProvenance = {};
  for (const [key, value] of Object.entries(obj)) {
    const field = asEnum(key, DRAFT_FIELDS, `${name}.${key}`);
    const item = asRecord(value, `${name}.${key}`);
    const line =
      item.line === null || item.line === undefined
        ? null
        : asInt(item.line, `${name}.${key}.line`, 1);
    result[field] = {
      source: asEnum(item.source, FIELD_SOURCES, `${name}.${key}.source`),
      line,
      excerpt: line === null ? null : asString(item.excerpt, `${name}.${key}.excerpt`),
    };
  }
  return result;
}

export function parseCareerProfile(raw: unknown): CareerProfile {
  const obj = asRecord(raw, 'CareerProfile');
  return {
    version: asInt(obj.version, 'version', 1),
    role: asString(obj.role, 'role'),
    primaryTechnology: asString(obj.primaryTechnology, 'primaryTechnology'),
    currentSeniority: asEnum(obj.currentSeniority, SENIORITIES, 'currentSeniority'),
    ...parseLists(obj),
    origin: asEnum(obj.origin, PROFILE_ORIGINS, 'origin'),
    provenance: parseProvenance(obj.provenance, 'provenance'),
    updatedAt: asString(obj.updatedAt, 'updatedAt'),
  };
}

export function parseProfileView(raw: unknown): ProfileView {
  const obj = asRecord(raw, 'ProfileView');
  if (!('profile' in obj)) throw new Error('Campo profile ausente.');
  return {
    priorityCompanies: asStringArray(obj.priorityCompanies ?? [], 'priorityCompanies'),
    profile: obj.profile === null ? null : parseCareerProfile(obj.profile),
    assessment: parseRefreshStatus(obj.assessment),
  };
}

export function parseProfilePreview(raw: unknown): ProfilePreview {
  const obj = asRecord(raw, 'ProfilePreview');
  const draft = asRecord(obj.draft, 'draft');
  if (!Array.isArray(obj.warnings)) throw new Error('Lista warnings inválida.');
  return {
    draft: {
      role: asNullableString(draft.role, 'draft.role'),
      primaryTechnology: asNullableString(draft.primaryTechnology, 'draft.primaryTechnology'),
      currentSeniority:
        draft.currentSeniority === null || draft.currentSeniority === undefined
          ? null
          : asEnum(draft.currentSeniority, SENIORITIES, 'draft.currentSeniority'),
      ...parseLists(draft),
    },
    warnings: obj.warnings.map((item, i) => {
      const w = asRecord(item, `warnings[${i}]`);
      return {
        field: asEnum(w.field, DRAFT_FIELDS, `warnings[${i}].field`),
        code: asEnum(w.code, WARNING_CODES, `warnings[${i}].code`),
        message: asString(w.message, `warnings[${i}].message`),
      };
    }),
    provenance: parseProvenance(obj.provenance, 'provenance'),
  };
}

function parsePreparationItems(raw: unknown, field: string): PreparationItem[] {
  if (!Array.isArray(raw)) throw new Error(`Lista ${field} inválida.`);
  return raw.map((item, i) => {
    const obj = asRecord(item, `${field}[${i}]`);
    return {
      technology: asString(obj.technology, `${field}[${i}].technology`),
      profileTier: asEnum(obj.profileTier, PREPARATION_TIERS, `${field}[${i}].profileTier`),
      quote: asString(obj.quote, `${field}[${i}].quote`),
    };
  });
}

/** Respostas anteriores à US006 não trazem `preparation`; ausência vira `null`. */
export function parsePreparation(raw: unknown): Preparation | null {
  if (raw === undefined || raw === null) return null;
  const obj = asRecord(raw, 'preparation');
  return {
    profileVersion:
      obj.profileVersion === null ? null : asInt(obj.profileVersion, 'profileVersion', 1),
    required: parsePreparationItems(obj.required, 'preparation.required'),
    optional: parsePreparationItems(obj.optional, 'preparation.optional'),
    mentioned: parsePreparationItems(obj.mentioned, 'preparation.mentioned'),
  };
}

export async function getProfile(signal?: AbortSignal): Promise<ProfileView> {
  return parseProfileView(await api<unknown>('/api/profile', { signal }));
}

export async function previewProfileImport(markdown: string): Promise<ProfilePreview> {
  return parseProfilePreview(
    await api<unknown>('/api/profile/import/preview', {
      method: 'POST',
      body: { markdown },
    }),
  );
}

export async function saveProfile(payload: SavePayload): Promise<ProfileView> {
  return parseProfileView(await api<unknown>('/api/profile', { method: 'PUT', body: payload }));
}
