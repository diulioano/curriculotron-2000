import type { Opportunity, SalaryObservation } from '../api/panel';
import { AbsentValue, InferenceBox } from './Epistemic';

const money = (n: number) =>
  n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 2 });

export function SalaryRange({
  estimate,
  detailText,
}: {
  estimate: SalaryObservation;
  detailText?: string | null;
}) {
  const e = estimate;
  const sourceLabel = e.scope === 'market' ? 'Média de mercado' : e.criteria.company;
  const consultedLabel = new Date(e.consultedAt).toLocaleDateString('pt-BR', {
    month: '2-digit',
    year: 'numeric',
    timeZone: 'America/Sao_Paulo',
  });
  const flags = [
    e.convertedFromAnnual ? 'Convertido de valor anual' : null,
    e.stale ? 'Referência antiga' : null,
    e.sampleSize != null && e.sampleSize < 5 ? 'Poucos dados' : null,
  ].filter(Boolean);

  return (
    <InferenceBox className="salary-reference mono">
      <div className="stack compact">
        <span>
          ~ Estimativa:{' '}
          {e.monthlyMin != null && e.monthlyMax != null
            ? `${money(e.monthlyMin)} – ${money(e.monthlyMax)}/mês`
            : 'unidade não mensal — confira na fonte'}
        </span>
        <span className="ep-inference-reason">
          ({sourceLabel} · {e.source} · consulta {consultedLabel})
        </span>
        {flags.map((flag) => (
          <span key={flag} className="ep-inference-reason">
            {flag}
          </span>
        ))}
        {detailText && <span className="ep-inference-reason">{detailText}</span>}
      </div>
    </InferenceBox>
  );
}

export function SalaryEstimate({ item, detail = false }: { item: Opportunity; detail?: boolean }) {
  if (item.salary) return null;
  const e = item.salaryReference?.estimate;
  const detailText =
    detail && e
      ? `Comparado: ${e.criteria.role} · ${e.criteria.seniority} · ${e.criteria.location} · ${e.criteria.contractType.toUpperCase()}${e.sampleSize != null ? ` · ${e.sampleSize} salários` : ''}`
      : null;

  return (
    <div>
      {e ? (
        <SalaryRange estimate={e} detailText={detailText} />
      ) : (
        <AbsentValue className="small">Sem referência de mercado</AbsentValue>
      )}
    </div>
  );
}
