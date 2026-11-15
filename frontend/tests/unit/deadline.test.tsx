import { render, screen } from '@testing-library/react';
import { Deadline } from '../../src/components/Deadline';

afterEach(() => vi.useRealTimers());

it('destaca vencidos e urgência em texto', () => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-11-14T15:00:00Z'));
  const { rerender } = render(<Deadline at="2026-11-13T12:00:00-03:00" />);
  expect(screen.getByText(/Prazo vencido/)).toHaveClass('overdue');
  rerender(<Deadline at="2026-11-15T12:00:00-03:00" />);
  expect(screen.getByText(/Urgente/)).toHaveClass('urgent');
  rerender(<Deadline at="2026-11-20T12:00:00-03:00" />);
  expect(screen.getByText(/Prazo:/)).toHaveClass('soon');
});

it('prazo só com data usa o fim do dia de São Paulo', () => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-11-15T02:00:00Z'));
  render(<Deadline at="2026-11-14" precision="date_only" />);
  expect(screen.getByText(/Urgente/)).toHaveTextContent('14/11/2026');
  expect(screen.queryByText(/vencido/)).not.toBeInTheDocument();
});

it('prazo ambíguo não é tratado como data exata', () => {
  render(<Deadline at="2026-11-14T12:00:00Z" precision="ambiguous" />);
  expect(screen.getByText('Prazo a confirmar')).toBeInTheDocument();
});
