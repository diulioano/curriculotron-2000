import { render, screen } from '@testing-library/react';
import { DataAsOf } from '../../src/components/DataAsOf';
import { formatAsOf } from '../../src/lib/time';
import { StatusBadge, type IntegrationState } from '../../src/components/StatusBadge';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { OpportunityCard } from '../../src/components/OpportunityCard';
import { colorJob } from '../e2e/support/company-colors';

describe('identidade da empresa no card', () => {
  function cards(names: string[]) {
    return render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient()}>
          {names.map((name, index) => (
            <OpportunityCard key={index} item={colorJob(name, `unit-${index}`)} />
          ))}
        </QueryClientProvider>
      </MemoryRouter>,
    );
  }

  it('duas vagas da mesma empresa mantêm cor no próprio card e ações existentes', () => {
    cards(['SAP', 'SAP', 'BV']);
    const articles = screen.getAllByRole('article');
    expect(articles.map((card) => card.style.getPropertyValue('--c'))).toEqual([
      '#0070F2',
      '#0070F2',
      '#005CA9',
    ]);
    expect(screen.getAllByText('SAP')).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: 'Me candidatei' })).toHaveLength(3);
    expect(screen.getAllByRole('button', { name: 'Descartar' })).toHaveLength(3);
  });

  it.each(['', '   '])('empresa ausente %j tem rótulo, grafite e iniciais decorativas', (name) => {
    cards([name]);
    expect(screen.getByText('Empresa não informada')).toBeVisible();
    expect(screen.getByRole('article').style.getPropertyValue('--c')).toBe('#6B6B73');
    expect(screen.getByText('?').closest('[aria-hidden]')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByText('salário a confirmar')).toBeVisible();
  });

  it('card descartado mantém empresa por texto e ação de restauração', () => {
    render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient()}>
          <OpportunityCard item={colorJob('BV')} discarded />
        </QueryClientProvider>
      </MemoryRouter>,
    );
    expect(screen.getByText('BV')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Restaurar' })).toBeEnabled();
    expect(screen.queryByRole('button', { name: 'Descartar' })).not.toBeInTheDocument();
  });
});

describe('StatusBadge (NFR-005: não depende de cor)', () => {
  it.each<[IntegrationState, string]>([
    ['ok', 'funcionando'],
    ['degraded', 'com falhas'],
    ['disconnected', 'desconectado'],
    ['paused', 'pausado'],
  ])('estado %s tem rótulo em texto "%s"', (state, label) => {
    render(<StatusBadge state={state} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });

  it('aceita rótulo específico', () => {
    render(<StatusBadge state="paused" label="limite do provedor" />);
    expect(screen.getByText('limite do provedor')).toBeInTheDocument();
  });
});

describe('DataAsOf (US5-4)', () => {
  it('sem sucesso real não mostra a hora atual', () => {
    render(<DataAsOf at={null} />);
    expect(screen.getByText('sem atualização bem-sucedida ainda')).toBeInTheDocument();
  });

  it('formata hoje/ontem/data no fuso de São Paulo', () => {
    const now = new Date('2026-11-14T15:00:00Z'); // 12:00 em SP
    expect(formatAsOf('2026-11-14T10:40:00Z', now)).toBe('hoje 07:40');
    expect(formatAsOf('2026-11-13T22:05:00Z', now)).toBe('ontem 19:05');
    expect(formatAsOf('2026-11-10T12:00:00Z', now)).toBe('10/11 09:00');
  });

  it('mostra a data da última atualização', () => {
    render(<DataAsOf at="2026-11-14T10:40:00Z" now={new Date('2026-11-14T15:00:00Z')} />);
    expect(screen.getByText('hoje 07:40')).toBeInTheDocument();
  });
});
