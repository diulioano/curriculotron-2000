import type { Opportunity, SalaryObservation } from '../api/panel';

const money = (n: number) =>
  n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 2 });
export function SalaryRange({ estimate }: { estimate: SalaryObservation }) {
  const e = estimate;
  return (
    <div className="salary-reference mono">
      <p>
        Estimativa:{' '}
        {e.monthlyMin != null && e.monthlyMax != null
          ? `${money(e.monthlyMin)} – ${money(e.monthlyMax)}/mês`
          : 'unidade não mensal — confira na fonte'}
      </p>
      <p className="muted small">
        {e.scope === 'market' ? 'Média de mercado' : e.criteria.company} · {e.source} · consulta{' '}
        {new Date(e.consultedAt).toLocaleDateString('pt-BR', {
          month: '2-digit',
          year: 'numeric',
          timeZone: 'America/Sao_Paulo',
        })}
      </p>
      {e.convertedFromAnnual && <p className="muted small">Convertido de valor anual</p>}
      {e.stale && <p className="muted small">Referência antiga</p>}
      {e.sampleSize != null && e.sampleSize < 5 && <p className="muted small">Poucos dados</p>}
    </div>
  );
}
export function SalaryEstimate({ item, detail = false }: { item: Opportunity; detail?: boolean }) {
  if (item.salary) return null;
  const e = item.salaryReference?.estimate;
  return (
    <div>
      {e ? <SalaryRange estimate={e} /> : <p className="muted small">Sem referência de mercado</p>}
      {detail && e && (
        <p className="muted small">
          Comparado: {e.criteria.role} · {e.criteria.seniority} · {e.criteria.location} ·{' '}
          {e.criteria.contractType.toUpperCase()}
          {e.sampleSize != null && ` · ${e.sampleSize} salários`}
        </p>
      )}
    </div>
  );
}
