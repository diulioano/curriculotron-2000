import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { SalaryEstimate } from '../../src/components/SalaryEstimate';
import type { Opportunity, SalaryObservation } from '../../src/api/panel';
const estimate: SalaryObservation = {
  id: 'e',
  criteria: {
    company: 'Company',
    role: 'Java',
    seniority: 'pleno',
    location: 'Brasil',
    workMode: 'unknown',
    contractType: 'clt',
  },
  monthlyMin: 6000,
  monthlyMax: 8000,
  originalMin: 72000,
  originalMax: 96000,
  originalUnit: 'yearly',
  currency: 'BRL',
  convertedFromAnnual: true,
  sampleSize: 4,
  source: 'Glassdoor',
  sourceUrl: 'https://www.glassdoor.com.br/',
  consultedAt: '2026-01-01T12:00:00Z',
  expiresAt: '2026-04-01T12:00:00Z',
  stale: true,
  scope: 'company',
};
const job = { salary: null, salaryReference: { status: 'available', estimate } } as Opportunity;
describe('Salary estimate', () => {
  it('separates evidence and shows warnings as epistemic inference', () => {
    const { container } = render(<SalaryEstimate item={job} detail />);
    const inference = container.querySelector('.ep-inference.dsh');
    expect(inference).not.toBeNull();
    expect(inference).toHaveTextContent(/^~/);
    expect(screen.getByText(/Estimativa:/)).toHaveTextContent('6.000');
    expect(screen.getByText(/Poucos dados/)).toBeInTheDocument();
    expect(screen.getByText(/Referência antiga/)).toBeInTheDocument();
    expect(screen.getByText(/Convertido de valor anual/)).toBeInTheDocument();
    expect(screen.getByText(/Comparado:/)).toHaveTextContent('pleno');
  });
  it('does not replace declared salary', () => {
    const { container } = render(
      <SalaryEstimate item={{ ...job, salary: { min: 9000, max: 10000, unit: 'monthly' } }} />,
    );
    expect(container).toBeEmptyDOMElement();
  });
  it('shows missing reference using hatched AbsentValue without a made-up number', () => {
    const { container } = render(<SalaryEstimate item={{ ...job, salaryReference: null }} />);
    const absent = screen.getByText('Sem referência de mercado');
    expect(absent.closest('.ep-absent.hatch')).not.toBeNull();
    expect(container.querySelector('.ep-inference')).toBeNull();
  });
  it('does not convert hourly amounts and marks as inference/absent', () => {
    render(
      <SalaryEstimate
        item={{
          ...job,
          salaryReference: {
            status: 'non_monthly',
            estimate: { ...estimate, monthlyMin: null, monthlyMax: null },
          },
        }}
      />,
    );
    expect(screen.getByText(/unidade não mensal/)).toBeInTheDocument();
  });
});
