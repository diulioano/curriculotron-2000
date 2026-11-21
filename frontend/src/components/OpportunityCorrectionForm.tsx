import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { useCompatibilityRefresh } from '../api/useCompatibilityRefresh';
import {
  correctOpportunity,
  type Opportunity,
  type OpportunityCorrectionPayload,
  type OpportunityDetail,
} from '../api/panel';
import { MutationFeedback } from './Panel';
import { Select, type SelectOption } from './Select';

type SalaryUnit = 'monthly' | 'hourly' | 'yearly' | 'unknown';

const WORK_MODE_OPTIONS: readonly SelectOption<Opportunity['workMode']>[] = [
  { value: 'remote', label: 'Remoto' },
  { value: 'hybrid', label: 'Híbrido' },
  { value: 'onsite', label: 'Presencial' },
  { value: 'unknown', label: 'A confirmar' },
];
const CONTRACT_OPTIONS: readonly SelectOption<Opportunity['contractType']>[] = [
  { value: 'clt', label: 'CLT' },
  { value: 'pj', label: 'PJ' },
  { value: 'other', label: 'Outro' },
  { value: 'unknown', label: 'A confirmar' },
];
const SALARY_UNIT_OPTIONS: readonly SelectOption<SalaryUnit>[] = [
  { value: 'monthly', label: 'Mensal' },
  { value: 'hourly', label: 'Por hora' },
  { value: 'yearly', label: 'Anual' },
  { value: 'unknown', label: 'A confirmar' },
];
const AVAILABILITY_OPTIONS: readonly SelectOption<Opportunity['availability']>[] = [
  { value: 'open', label: 'Aberta' },
  { value: 'closed', label: 'Encerrada' },
  { value: 'unconfirmed', label: 'A confirmar' },
];

export function OpportunityCorrectionForm({
  item,
  onClose,
  onCorrected,
}: {
  item: OpportunityDetail;
  onClose: () => void;
  onCorrected?: (updated: OpportunityDetail) => void;
}) {
  const client = useQueryClient();
  const { invalidateAfterOpportunityChange } = useCompatibilityRefresh();
  const [title, setTitle] = useState(item.title);
  const [companyName, setCompanyName] = useState(item.companyName);
  const [location, setLocation] = useState(item.location);
  const [workMode, setWorkMode] = useState<Opportunity['workMode']>(item.workMode);
  const [contractType, setContractType] = useState<Opportunity['contractType']>(item.contractType);
  const [salaryMin, setSalaryMin] = useState(
    item.salary?.min != null ? String(item.salary.min) : '',
  );
  const [salaryMax, setSalaryMax] = useState(
    item.salary?.max != null ? String(item.salary.max) : '',
  );
  const [salaryUnit, setSalaryUnit] = useState<SalaryUnit>(item.salary?.unit ?? 'monthly');
  const [clearSalary, setClearSalary] = useState(false);
  const [availability, setAvailability] = useState<Opportunity['availability']>(item.availability);
  const [deadlineAt, setDeadlineAt] = useState(item.deadlineAt?.slice(0, 10) ?? '');
  const [publishedAt, setPublishedAt] = useState(item.publishedAt?.slice(0, 10) ?? '');
  const [reason, setReason] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (payload: OpportunityCorrectionPayload) => correctOpportunity(item.id, payload),
    onSuccess: (updated) => {
      client.setQueryData(['opportunity', item.id], updated);
      void invalidateAfterOpportunityChange();
      onCorrected?.(updated);
    },
  });

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setValidationError(null);

    const payload: OpportunityCorrectionPayload = {};
    if (title.trim() !== item.title) payload.title = title.trim();
    if (companyName.trim() !== item.companyName) payload.companyName = companyName.trim();
    if (location.trim() !== item.location) payload.location = location.trim();
    if (workMode !== item.workMode) payload.workMode = workMode;
    if (contractType !== item.contractType) payload.contractType = contractType;
    if (availability !== item.availability) payload.availability = availability;

    const initialDeadline = item.deadlineAt?.slice(0, 10) ?? '';
    if (deadlineAt !== initialDeadline) payload.deadlineAt = deadlineAt || null;

    const initialPublished = item.publishedAt?.slice(0, 10) ?? '';
    if (publishedAt !== initialPublished) payload.publishedAt = publishedAt || null;

    if (clearSalary) {
      payload.clearSalary = true;
    } else {
      const minVal = salaryMin.trim() === '' ? null : Number(salaryMin);
      const maxVal = salaryMax.trim() === '' ? null : Number(salaryMax);
      if (
        (minVal !== null && (!Number.isFinite(minVal) || minVal < 0)) ||
        (maxVal !== null && (!Number.isFinite(maxVal) || maxVal < 0))
      ) {
        setValidationError('Informe valores numéricos maiores ou iguais a zero para salário.');
        return;
      }
      if (minVal !== null && maxVal !== null && minVal > maxVal) {
        setValidationError('O salário mínimo não pode ser maior que o máximo.');
        return;
      }
      const prevMin = item.salary?.min ?? null;
      const prevMax = item.salary?.max ?? null;
      const prevUnit = item.salary?.unit ?? 'monthly';
      if (minVal === null && maxVal === null && item.salary) {
        payload.clearSalary = true;
      } else if (
        (minVal !== null || maxVal !== null) &&
        (minVal !== prevMin || maxVal !== prevMax || salaryUnit !== prevUnit)
      ) {
        payload.salary = { min: minVal, max: maxVal, unit: salaryUnit };
      }
    }

    if (Object.keys(payload).length === 0) {
      setValidationError('Altere pelo menos um campo antes de salvar a correção.');
      return;
    }
    if (reason.trim()) payload.reason = reason.trim();

    mutation.mutate(payload);
  }

  const displayedError =
    validationError !== null
      ? new Error(validationError)
      : mutation.error instanceof ApiError && mutation.error.status === 404
        ? new Error('Vaga ou serviço de correção indisponível. Nenhuma alteração foi salva.')
        : mutation.error;

  return (
    <form
      className="correction-form dsh stack"
      aria-label="Corrigir dados da vaga"
      onSubmit={handleSubmit}
    >
      <div className="section-heading">
        <h3 style={{ fontFamily: 'var(--font-hand)', fontWeight: 700, fontSize: 18, margin: 0 }}>
          Corrigir dado observado
        </h3>
        <button type="button" className="btn bt bt-sec dsh" onClick={onClose}>
          Fechar
        </button>
      </div>
      <p className="muted small">
        As fontes originais continuam preservadas; sua edição cria um registro de correção.
      </p>

      <div className="field">
        <label htmlFor={`corr-title-${item.id}`}>Título</label>
        <input
          id={`corr-title-${item.id}`}
          className="input"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
          maxLength={240}
        />
      </div>

      <div className="field">
        <label htmlFor={`corr-company-${item.id}`}>Empresa</label>
        <input
          id={`corr-company-${item.id}`}
          className="input"
          value={companyName}
          onChange={(e) => setCompanyName(e.target.value)}
          required={item.companyName.trim() !== ''}
          maxLength={160}
        />
      </div>

      <div className="field">
        <label htmlFor={`corr-location-${item.id}`}>Local</label>
        <input
          id={`corr-location-${item.id}`}
          className="input"
          value={location}
          onChange={(e) => setLocation(e.target.value)}
          maxLength={160}
        />
      </div>

      <div className="detail-grid">
        <div className="field">
          <label htmlFor={`corr-mode-${item.id}`}>Modalidade</label>
          <Select
            id={`corr-mode-${item.id}`}
            value={workMode}
            options={WORK_MODE_OPTIONS}
            onChange={setWorkMode}
          />
        </div>

        <div className="field">
          <label htmlFor={`corr-contract-${item.id}`}>Contrato</label>
          <Select
            id={`corr-contract-${item.id}`}
            value={contractType}
            options={CONTRACT_OPTIONS}
            onChange={setContractType}
          />
        </div>
      </div>

      <div className="detail-grid">
        <div className="field">
          <label htmlFor={`corr-sal-min-${item.id}`}>Salário mínimo (R$)</label>
          <input
            id={`corr-sal-min-${item.id}`}
            className="input"
            type="number"
            min="0"
            step="any"
            disabled={clearSalary}
            value={salaryMin}
            onChange={(e) => setSalaryMin(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor={`corr-sal-max-${item.id}`}>Salário máximo (R$)</label>
          <input
            id={`corr-sal-max-${item.id}`}
            className="input"
            type="number"
            min="0"
            step="any"
            disabled={clearSalary}
            value={salaryMax}
            onChange={(e) => setSalaryMax(e.target.value)}
          />
        </div>
      </div>

      <div className="detail-grid">
        <div className="field">
          <label htmlFor={`corr-sal-unit-${item.id}`}>Unidade do salário</label>
          <Select
            id={`corr-sal-unit-${item.id}`}
            disabled={clearSalary}
            value={salaryUnit}
            options={SALARY_UNIT_OPTIONS}
            onChange={setSalaryUnit}
          />
        </div>
        <div className="field">
          <label htmlFor={`corr-avail-${item.id}`}>Disponibilidade</label>
          <Select
            id={`corr-avail-${item.id}`}
            value={availability}
            options={AVAILABILITY_OPTIONS}
            onChange={setAvailability}
          />
        </div>
      </div>

      <label className="check-field">
        <input
          type="checkbox"
          checked={clearSalary}
          onChange={(e) => setClearSalary(e.target.checked)}
        />
        <span>Remover salário declarado (marcar como a confirmar)</span>
      </label>

      <div className="detail-grid">
        <div className="field">
          <label htmlFor={`corr-deadline-${item.id}`}>Prazo de inscrição</label>
          <input
            id={`corr-deadline-${item.id}`}
            className="input"
            type="date"
            value={deadlineAt}
            onChange={(e) => setDeadlineAt(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor={`corr-published-${item.id}`}>Data de publicação</label>
          <input
            id={`corr-published-${item.id}`}
            className="input"
            type="date"
            value={publishedAt}
            onChange={(e) => setPublishedAt(e.target.value)}
          />
        </div>
      </div>

      <div className="field">
        <label htmlFor={`corr-reason-${item.id}`}>Motivo ou origem da correção</label>
        <textarea
          id={`corr-reason-${item.id}`}
          className="input"
          rows={2}
          maxLength={500}
          placeholder="Ex.: confirmado no edital oficial da empresa"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </div>

      <div className="actions">
        <button type="submit" className="btn bt bt-pri" disabled={mutation.isPending}>
          {mutation.isPending ? 'Salvando…' : 'Salvar correção'}
        </button>
        <button type="button" className="btn bt bt-sec dsh" onClick={onClose}>
          Cancelar
        </button>
      </div>

      <MutationFeedback
        error={displayedError}
        success={mutation.isSuccess && 'Correção registrada — fonte original preservada.'}
      />
    </form>
  );
}
