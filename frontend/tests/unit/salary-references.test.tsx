import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SalaryObservation } from '../../src/api/panel';
import { SalaryReferences } from '../../src/pages/SalaryReferences';

const staleEstimate: SalaryObservation = {
  id: 'e1',
  criteria: {
    company: 'Acme',
    role: 'Java',
    seniority: 'pleno',
    location: 'Brasil',
    workMode: 'remote',
    contractType: 'clt',
  },
  monthlyMin: 6000,
  monthlyMax: 8000,
  originalMin: 6000,
  originalMax: 8000,
  originalUnit: 'monthly',
  currency: 'BRL',
  convertedFromAnnual: false,
  sampleSize: 4,
  source: 'Glassdoor',
  sourceUrl: 'https://www.glassdoor.com.br/',
  consultedAt: '2026-01-01T12:00:00Z',
  expiresAt: '2026-04-01T12:00:00Z',
  stale: true,
  scope: 'company',
};

afterEach(() => vi.unstubAllGlobals());

describe('SalaryReferences', () => {
  it('em referência desatualizada, leva às vagas em vez de refazer a mesma consulta', async () => {
    const fetcher = vi.fn(
      async () =>
        new Response(JSON.stringify({ items: [staleEstimate] }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
    );
    vi.stubGlobal('fetch', fetcher);
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <SalaryReferences />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(await screen.findByText('DESATUALIZADO')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Atualizar a partir de uma vaga →' })).toHaveAttribute(
      'href',
      '/vagas',
    );
    expect(screen.queryByText('Atualizar →')).not.toBeInTheDocument();
  });
});
