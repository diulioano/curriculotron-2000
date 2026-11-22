import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import {
  SENIORITIES,
  SENIORITY_LABELS,
  type DraftField,
  type FieldProvenance,
  type FieldSource,
  type ProfileView,
  type SavePayload,
  type Seniority,
} from '../api/profile';
import {
  fieldProvenance,
  isBlank,
  provenanceFor,
  validate,
  type EditorSession,
  type ProfileForm,
} from '../lib/profile-form';
import { useSaveProfileMutation } from '../api/useCompatibilityRefresh';

const WARNING_LABELS = {
  missing: 'Não encontrado no currículo — preencha',
  inferred: 'Deduzido do currículo — confira',
  invalid: 'Valor do currículo não reconhecido — corrija',
} as const;

const SOURCE_LABELS: Record<FieldSource, string> = {
  imported: 'Lido do currículo',
  inferred: 'Deduzido do currículo — confira',
  edited: 'Corrigido por você',
  declared: 'Informado por você',
};

/** Rótulo da origem e, quando há, a linha do Markdown usada como evidência. */
export function FieldOrigin({
  provenance,
  title,
}: {
  provenance: FieldProvenance;
  title?: string;
}) {
  const corrected = provenance.source === 'edited' || provenance.source === 'declared';
  return (
    <>
      <span className={corrected ? 'meta provenance corrected' : 'meta provenance'} title={title}>
        {SOURCE_LABELS[provenance.source]}
      </span>
      {provenance.line !== null && (
        <span className="meta provenance-ref">
          {provenance.source === 'edited' ? 'Original na linha' : 'Linha'} {provenance.line}: “
          {provenance.excerpt}”
        </span>
      )}
    </>
  );
}

/** Origem visível de cada campo: lido, deduzido, ausente, corrigido ou informado pela pessoa. */
function Provenance({
  session,
  field,
  form,
}: {
  session: EditorSession;
  field: DraftField;
  form: ProfileForm;
}) {
  const warning = session.warnings.find((w) => w.field === field);
  const current = fieldProvenance(session, field, form);
  if (current) return <FieldOrigin provenance={current} title={warning?.message} />;
  if (!session.imported) return null;
  // Prévia sem origem por campo (servidor anterior): mantém a leitura pelos avisos.
  if (warning) {
    return (
      <span className="meta provenance" title={warning.message}>
        {WARNING_LABELS[warning.code]}
      </span>
    );
  }
  if (Object.keys(session.provenance).length > 0 || isBlank(form[field])) return null;
  return <span className="meta provenance">Lido do currículo</span>;
}

export function ProfileEditor({
  session,
  version,
  onSaved,
  onCancel,
  onReload,
}: {
  session: EditorSession;
  /** Versão atual do perfil (0 quando ainda não existe). */
  version: number;
  onSaved: (saved: ProfileView) => void;
  onCancel: () => void;
  onReload: () => Promise<unknown>;
}) {
  const [reloadFailed, setReloadFailed] = useState(false);
  const [form, setForm] = useState<ProfileForm>(() => ({
    ...session.initial,
    ...session.prefilled,
  }));
  const [errors, setErrors] = useState<string[]>([]);
  const save = useSaveProfileMutation({ onSuccess: onSaved });
  const conflict = save.error instanceof ApiError && save.error.code === 'profile_version_conflict';

  function submit(e: FormEvent) {
    e.preventDefault();
    const result = validate(form);
    if ('errors' in result) {
      setErrors(result.errors);
      return;
    }
    setErrors([]);
    const payload: SavePayload = {
      ...result.fields,
      version,
      origin: session.origin,
      provenance: provenanceFor(session, form),
    };
    save.mutate(payload);
  }

  function text(
    field: Exclude<DraftField, 'currentSeniority' | 'targetLevels'>,
    label: string,
    multiline = false,
  ) {
    const id = `profile-${field}`;
    const props = {
      className: 'input',
      id,
      value: form[field],
      onChange: (e: { target: { value: string } }) => setForm({ ...form, [field]: e.target.value }),
    };
    return (
      <div className="field">
        <label htmlFor={id}>{label}</label>
        <Provenance session={session} field={field} form={form} />
        {multiline ? <textarea rows={3} {...props} /> : <input {...props} />}
      </div>
    );
  }

  return (
    <form
      className="note-card dsh stack"
      onSubmit={submit}
      aria-label="Revisão do perfil"
      noValidate
    >
      <div className="section-heading">
        <strong>
          {session.imported ? 'Revise o rascunho importado' : 'Seu perfil profissional'}
        </strong>
        <span className="meta">
          {session.origin === 'markdown'
            ? 'Origem: importação de currículo'
            : 'Origem: entrada manual'}
        </span>
      </div>
      {session.imported && (
        <p className="muted">
          Nada foi salvo ainda. Confira cada campo; o que você mudar fica identificado como correção
          sua.
        </p>
      )}
      <div className="detail-grid">
        {text('role', 'Cargo desejado')}
        {text('primaryTechnology', 'Tecnologia principal')}
        {text('secondaryTechnologies', 'Tecnologias secundárias (uma por linha)', true)}
        {text(
          'flexibleTechnologies',
          'Preferências flexíveis — aceito trabalhar com (uma por linha)',
          true,
        )}
        <div className="field">
          <label htmlFor="profile-currentSeniority">Senioridade atual</label>
          <Provenance session={session} field="currentSeniority" form={form} />
          <select
            className="input"
            id="profile-currentSeniority"
            value={form.currentSeniority}
            onChange={(e) =>
              setForm({ ...form, currentSeniority: e.target.value as Seniority | '' })
            }
          >
            <option value="">Escolha…</option>
            {SENIORITIES.map((level) => (
              <option key={level} value={level}>
                {SENIORITY_LABELS[level]}
              </option>
            ))}
          </select>
        </div>
        <fieldset className="field">
          <legend>Níveis desejados</legend>
          <Provenance session={session} field="targetLevels" form={form} />
          {SENIORITIES.map((level) => (
            <label className="check-field" key={level}>
              <input
                type="checkbox"
                checked={form.targetLevels.includes(level)}
                onChange={(e) =>
                  setForm({
                    ...form,
                    targetLevels: e.target.checked
                      ? SENIORITIES.filter((l) => l === level || form.targetLevels.includes(l))
                      : form.targetLevels.filter((l) => l !== level),
                  })
                }
              />{' '}
              {SENIORITY_LABELS[level]}
            </label>
          ))}
        </fieldset>
        {text('priorities', 'Empresas prioritárias, em ordem (uma por linha)', true)}
      </div>
      <fieldset className="stack compact">
        <legend>Piso próprio para vagas Júnior (R$ por mês)</legend>
        <p className="meta">
          Se você não busca Júnior, uma vaga Júnior só entra com salário anunciado no regime, acima
          deste piso, e justificativa registrada da empresa. Estimativas não confirmam o piso.
        </p>
        <div className="detail-grid">
          {text('juniorFloorCltMonthlyBrl', 'Piso Júnior CLT')}
          {text('juniorFloorPjMonthlyBrl', 'Piso Júnior PJ')}
        </div>
      </fieldset>
      {errors.length > 0 && (
        <div className="alert dsh" role="alert">
          <ul>
            {errors.map((error) => (
              <li key={error}>{error}</li>
            ))}
          </ul>
        </div>
      )}
      {reloadFailed && <p role="alert">Não foi possível recarregar o perfil. Tente novamente.</p>}
      {save.error && (
        <p className="alert dsh" role="alert">
          {save.error.message}
          {conflict && ' Suas edições permanecem no formulário.'}
        </p>
      )}
      <div className="actions">
        <button type="submit" className="btn bt bt-pri" disabled={save.isPending}>
          {save.isPending ? 'Salvando…' : 'Salvar perfil'}
        </button>
        {conflict && (
          <button
            type="button"
            className="btn bt bt-sec dsh"
            onClick={() => {
              setReloadFailed(false);
              void onReload().catch(() => setReloadFailed(true));
            }}
          >
            Recarregar versão atual
          </button>
        )}
        <button type="button" className="btn bt bt-sec dsh" onClick={onCancel}>
          Cancelar
        </button>
      </div>
    </form>
  );
}
