import {
  DRAFT_FIELDS,
  SENIORITIES,
  type DraftField,
  type DraftWarning,
  type FieldProvenance,
  type ProfileDraft,
  type ProfileFields,
  type ProfileOrigin,
  type ProfileProvenance,
  type Seniority,
} from '../api/profile';

/** Estado do formulário: listas como texto (um item por linha) e pisos como digitados. */
export interface ProfileForm {
  role: string;
  primaryTechnology: string;
  secondaryTechnologies: string;
  flexibleTechnologies: string;
  currentSeniority: Seniority | '';
  targetLevels: Seniority[];
  priorities: string;
  juniorFloorCltMonthlyBrl: string;
  juniorFloorPjMonthlyBrl: string;
}

/** Uma sessão de edição: rascunho importado, entrada manual ou edição do perfil salvo. */
export interface EditorSession {
  origin: ProfileOrigin;
  initial: ProfileForm;
  /** Sugestões de abertura (ex. empresas prioritárias atuais) sem evidência documental. */
  prefilled?: Partial<ProfileForm>;
  /** Avisos da importação; vazio para entrada manual ou edição. */
  warnings: DraftWarning[];
  /** Origem de cada campo ao abrir a sessão (prévia importada ou perfil salvo). */
  provenance: ProfileProvenance;
  imported: boolean;
}

const EMPTY_FORM: ProfileForm = {
  role: '',
  primaryTechnology: '',
  secondaryTechnologies: '',
  flexibleTechnologies: '',
  currentSeniority: '',
  targetLevels: [],
  priorities: '',
  juniorFloorCltMonthlyBrl: '',
  juniorFloorPjMonthlyBrl: '',
};

function floorText(value: number | null): string {
  return value === null ? '' : String(value).replace('.', ',');
}

export function toForm(fields: ProfileFields | ProfileDraft): ProfileForm {
  return {
    role: fields.role ?? '',
    primaryTechnology: fields.primaryTechnology ?? '',
    secondaryTechnologies: fields.secondaryTechnologies.join('\n'),
    flexibleTechnologies: fields.flexibleTechnologies.join('\n'),
    currentSeniority: fields.currentSeniority ?? '',
    targetLevels: SENIORITIES.filter((level) => fields.targetLevels.includes(level)),
    priorities: fields.priorities.join('\n'),
    juniorFloorCltMonthlyBrl: floorText(fields.juniorFloorCltMonthlyBrl),
    juniorFloorPjMonthlyBrl: floorText(fields.juniorFloorPjMonthlyBrl),
  };
}

export function manualSession(): EditorSession {
  return { origin: 'manual', initial: EMPTY_FORM, warnings: [], provenance: {}, imported: false };
}

export function isBlank(value: ProfileForm[DraftField]): boolean {
  return Array.isArray(value) ? value.length === 0 : value.trim() === '';
}

/**
 * Origem atual de um campo: a recebida enquanto não é alterado; ao alterar, valor com evidência
 * (lido, deduzido ou já corrigido) vira `edited` mantendo a linha, e o resto vira `declared`.
 */
export function fieldProvenance(
  session: EditorSession,
  field: DraftField,
  form: ProfileForm,
): FieldProvenance | null {
  const initial = session.provenance[field] ?? null;
  const changed = JSON.stringify(form[field]) !== JSON.stringify(session.initial[field]);
  if (!changed) return initial;
  if (initial && initial.source !== 'declared') {
    return { source: 'edited', line: initial.line, excerpt: initial.excerpt };
  }
  // Valor importado sem origem registrada (servidor anterior) também conta como correção.
  if (!initial && session.imported && !isBlank(session.initial[field])) {
    return { source: 'edited', line: null, excerpt: null };
  }
  return isBlank(form[field]) ? null : { source: 'declared', line: null, excerpt: null };
}

export function provenanceFor(session: EditorSession, form: ProfileForm): ProfileProvenance {
  const result: ProfileProvenance = {};
  for (const field of DRAFT_FIELDS) {
    const value = fieldProvenance(session, field, form);
    if (value) result[field] = value;
  }
  return result;
}

function lines(text: string): string[] {
  const seen = new Set<string>();
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => {
      const key = line.toLowerCase();
      if (!line || seen.has(key)) return false;
      seen.add(key);
      return true;
    });
}

/** Aceita "12000,50", "12.000,50" e "12000.50"; vazio = sem piso. */
export function parseFloor(text: string): number | null | 'invalid' {
  const trimmed = text.trim();
  if (!trimmed) return null;
  const normalized = trimmed.includes(',') ? trimmed.replace(/\./g, '').replace(',', '.') : trimmed;
  if (!/^\d{1,12}(\.\d{1,2})?$/.test(normalized)) return 'invalid';
  const value = Number(normalized);
  return value > 0 ? value : 'invalid';
}

type Validation = { fields: ProfileFields } | { errors: string[] };

export function validate(form: ProfileForm): Validation {
  const errors: string[] = [];
  const role = form.role.trim();
  const primary = form.primaryTechnology.trim();
  const secondary = lines(form.secondaryTechnologies);
  const flexible = lines(form.flexibleTechnologies);
  const priorityList = lines(form.priorities);
  const clt = parseFloor(form.juniorFloorCltMonthlyBrl);
  const pj = parseFloor(form.juniorFloorPjMonthlyBrl);
  if (!role) errors.push('Informe o cargo desejado.');
  else if (role.length > 200) errors.push('Cargo desejado deve ter no máximo 200 caracteres.');
  if (!primary) errors.push('Informe a tecnologia principal.');
  else if (primary.length > 100)
    errors.push('Tecnologia principal deve ter no máximo 100 caracteres.');
  if (secondary.length > 30) errors.push('Informe no máximo 30 tecnologias secundárias.');
  if (secondary.some((t) => t.length > 100))
    errors.push('Cada tecnologia secundária deve ter no máximo 100 caracteres.');
  if (flexible.length > 30) errors.push('Informe no máximo 30 tecnologias flexíveis.');
  if (flexible.some((t) => t.length > 100))
    errors.push('Cada tecnologia flexível deve ter no máximo 100 caracteres.');
  if (priorityList.length > 20) errors.push('Informe no máximo 20 empresas prioritárias.');
  if (priorityList.some((p) => p.length > 200))
    errors.push('Cada empresa prioritária deve ter no máximo 200 caracteres.');
  if (!form.currentSeniority) errors.push('Escolha a senioridade atual.');
  if (form.targetLevels.length === 0) errors.push('Escolha ao menos um nível desejado.');
  const primaryKey = primary.toLowerCase();
  if (secondary.some((t) => t.toLowerCase() === primaryKey))
    errors.push('A tecnologia principal não pode repetir nas secundárias.');
  const secondaryKeys = new Set(secondary.map((t) => t.toLowerCase()));
  if (flexible.some((t) => t.toLowerCase() === primaryKey || secondaryKeys.has(t.toLowerCase())))
    errors.push('Tecnologias flexíveis não podem repetir a principal nem as secundárias.');
  if (clt === 'invalid') errors.push('Piso Júnior CLT deve ser um valor positivo em reais.');
  if (pj === 'invalid') errors.push('Piso Júnior PJ deve ser um valor positivo em reais.');
  if (errors.length > 0 || !form.currentSeniority || clt === 'invalid' || pj === 'invalid') {
    return { errors };
  }
  return {
    fields: {
      role,
      primaryTechnology: primary,
      secondaryTechnologies: secondary,
      flexibleTechnologies: flexible,
      currentSeniority: form.currentSeniority,
      targetLevels: SENIORITIES.filter((level) => form.targetLevels.includes(level)),
      priorities: priorityList,
      juniorFloorCltMonthlyBrl: clt,
      juniorFloorPjMonthlyBrl: pj,
    },
  };
}
