import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DataAsOf } from '../../src/components/DataAsOf';
import { AbsentValue, EvidenceBlock, FactItem, InferenceBox } from '../../src/components/Epistemic';
import { OpportunityCorrectionForm } from '../../src/components/OpportunityCorrectionForm';
import { SurfaceStateBlock, type SurfaceStateKind } from '../../src/components/SurfaceState';
import { formatAsOf } from '../../src/lib/time';
import { StatusBadge, type IntegrationState } from '../../src/components/StatusBadge';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { OpportunityCard, OpportunityMeta } from '../../src/components/OpportunityCard';
import { Activities } from '../../src/pages/Activities';
import { colorJob } from '../e2e/support/company-colors';

afterEach(() => {
  vi.unstubAllGlobals();
});

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

  it('identifica proveniência de cada campo corrigido sem marcar salário como declarado', () => {
    render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient()}>
          <OpportunityCard
            item={{
              ...colorJob('PicPay', 'corrected-card'),
              location: 'Curitiba',
              publishedAt: '2026-11-20',
              deadlineAt: '2026-12-01',
              availability: 'open',
              salary: { min: 12000, max: 15000, unit: 'monthly' },
              correctedFields: [
                'title',
                'company_name',
                'location',
                'work_mode',
                'contract_type',
                'salary_min',
                'salary_max',
                'salary_unit',
                'availability',
                'deadline_at',
                'published_at',
              ],
            }}
          />
        </QueryClientProvider>
      </MemoryRouter>,
    );
    const card = screen.getByRole('article');
    expect(within(card).getAllByText('corrigido por você')).toHaveLength(9);
    expect(within(card).getByText(/Disponibilidade: aberta/)).toBeVisible();
    expect(within(card).getByText(/Prazo: 2026-12-01/)).toBeVisible();
    expect(within(card).getByText(/publicada: 2026-11-20/)).toBeVisible();
    expect(
      within(card)
        .getByText(/12\.000 – 15\.000/)
        .closest('.ep-fact'),
    ).not.toHaveTextContent('declarado');
    expect(
      within(card)
        .getByText(/12\.000 – 15\.000/)
        .closest('.ep-fact')
        ?.querySelector('[aria-hidden="true"]'),
    ).not.toBeInTheDocument();
  });

  it('atribui o salário corrigido na comparação de duplicatas e preserva a data da fonte', () => {
    render(
      <OpportunityMeta
        item={{
          ...colorJob('SAP'),
          salary: { min: 9500, max: null, unit: 'monthly' },
          correctedFields: ['salary_min'],
          publishedAt: '2026-11-15',
        }}
      />,
    );
    const salary = screen.getByText(/9\.500/).closest('p');
    expect(salary).toHaveTextContent('corrigido por você');
    expect(salary).not.toHaveTextContent('✓');
    expect(screen.getByText('Publicada em 2026-11-15').closest('p')).toHaveTextContent('✓');
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

  it('exibe estado DESATUALIZADO quando stale=true', () => {
    render(<DataAsOf at="2026-11-10T12:00:00Z" stale onRefresh={vi.fn()} />);
    expect(screen.getByText('DESATUALIZADO')).toBeInTheDocument();
    expect(screen.getByText('Atualizar →')).toBeInTheDocument();
  });
});

describe('4 Padrões Epistêmicos', () => {
  it('FactItem renderiza prefixo ✓ e correspondência com o perfil', () => {
    render(
      <>
        <FactItem value="Java 21 obrigatório" profileMatch="matched" />
        <FactItem value="Kotlin desejável" profileMatch="missing_from_profile" />
      </>,
    );
    expect(screen.getAllByText('✓')).toHaveLength(2);
    expect(screen.getByText('você tem')).toHaveClass('ep-match--ok');
    expect(screen.getByText('não está no perfil')).toHaveClass('ep-match--missing');
  });

  it('InferenceBox renderiza prefixo ~, motivo explícito ou fallback e borda tracejada', () => {
    const { container } = render(
      <>
        <InferenceBox value="Sênior" reason="citado no título" />
        <InferenceBox value="Kubernetes opcional" />
      </>,
    );
    expect(container.querySelectorAll('.ep-inference.dsh')).toHaveLength(2);
    expect(screen.getByText('(citado no título)')).toBeInTheDocument();
    expect(screen.getByText('(inferido pelo sistema)')).toBeInTheDocument();
  });

  it('AbsentValue renderiza hachura e faixa legível', () => {
    const { container } = render(<AbsentValue>Salário a confirmar</AbsentValue>);
    expect(container.querySelector('.ep-absent.hatch')).not.toBeNull();
    expect(screen.getByText('Salário a confirmar')).toBeVisible();
  });

  it('EvidenceBlock exibe fonte, datas, última correção e aciona onCorrect', async () => {
    const onCorrect = vi.fn();
    render(
      <EvidenceBlock
        sources={[
          {
            source: 'SAP Careers',
            url: 'https://careers.sap.com/job/1',
            fetchedAt: '2026-11-14T10:00:00Z',
          },
        ]}
        publishedAt="2026-11-10T00:00:00Z"
        corrections={[
          {
            id: 'c1',
            fieldName: 'location',
            previousValue: 'Presencial',
            correctedValue: 'Híbrido',
            reason: 'Recrutador confirmou híbrido',
            authorLabel: 'você',
            correctedAt: '2026-11-14T11:00:00Z',
          },
        ]}
        onCorrect={onCorrect}
      />,
    );
    expect(screen.getByText(/fonte:/)).toBeInTheDocument();
    expect(screen.getByText(/consultado:/)).toBeInTheDocument();
    expect(screen.getByText(/publicada:/)).toBeInTheDocument();
    expect(screen.getByText(/corrigido por você/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Corrigir algum dado →' }));
    expect(onCorrect).toHaveBeenCalledTimes(1);
  });
});

describe('6 Estados de Superfície (SurfaceStateBlock)', () => {
  it.each<[SurfaceStateKind, string]>([
    ['empty', 'VAZIO'],
    ['loading', 'CARREGANDO'],
    ['partial', 'PARCIAL'],
    ['error', 'FALHA'],
    ['stale', 'DESATUALIZADO'],
    ['pending_review', 'PENDENTE DE REVISÃO'],
  ])('estado %s exibe rótulo oficial "%s"', (kind, label) => {
    render(
      <MemoryRouter>
        <SurfaceStateBlock kind={kind} title={`Título ${kind}`} body={`Corpo ${kind}`} />
      </MemoryRouter>,
    );
    expect(screen.getByText(label)).toBeInTheDocument();
    expect(screen.getByText(`Título ${kind}`)).toBeInTheDocument();
  });

  it('Activities exibe execução parcial com contagem X de Y fontes consultadas', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (path: string) => {
        if (path === '/api/task-runs') {
          return new Response(
            JSON.stringify({
              dataAsOf: null,
              items: [
                {
                  id: 'run-partial-1',
                  state: 'completed',
                  pauseReason: null,
                  sourcesConsulted: ['oracle:sap', 'lever:cwi'],
                  sourcesFailed: ['gupy:banco'],
                  errorSummary: 'Tempo esgotado em 1 fonte.',
                },
              ],
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          );
        }
        return new Response(JSON.stringify({ dataAsOf: null, items: [] }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }),
    );

    render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient()}>
          <Activities />
        </QueryClientProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByText('PARCIAL')).toBeInTheDocument();
    expect(screen.getByText('2 de 3 fontes consultadas')).toBeInTheDocument();
    expect(screen.getByText(/Fontes com falha: gupy:banco\./)).toBeInTheDocument();
  });
});

describe('OpportunityCorrectionForm', () => {
  it('valida salário mínimo maior que máximo no cliente antes de enviar', async () => {
    const fetcher = vi.fn();
    vi.stubGlobal('fetch', fetcher);
    const job = {
      ...colorJob('SAP', 'job-corr'),
      ...{
        facts: [],
        inferred: [],
        missing: [],
        sources: [],
        seniority: { label: null, status: 'absent' as const, reason: null },
        corrections: [],
        timeline: [],
      },
    };
    render(
      <QueryClientProvider client={new QueryClient()}>
        <OpportunityCorrectionForm item={job} onClose={vi.fn()} />
      </QueryClientProvider>,
    );
    fireEvent.change(screen.getByLabelText('Salário mínimo (R$)'), { target: { value: '12000' } });
    fireEvent.change(screen.getByLabelText('Salário máximo (R$)'), { target: { value: '8000' } });
    await userEvent.click(screen.getByRole('button', { name: 'Salvar correção' }));
    expect(screen.getByRole('alert')).toHaveTextContent(/salário mínimo não pode ser maior/i);
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('envia correção para POST /api/opportunities/{id}/corrections', async () => {
    const onCorrected = vi.fn();
    const job = {
      ...colorJob('SAP', 'job-corr'),
      ...{
        facts: [],
        inferred: [],
        missing: [],
        sources: [],
        seniority: { label: null, status: 'absent' as const, reason: null },
        corrections: [],
        timeline: [],
      },
    };
    const fetcher = vi.fn(async () => {
      return new Response(JSON.stringify({ ...job, location: 'Porto Alegre' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });
    vi.stubGlobal('fetch', fetcher);
    render(
      <QueryClientProvider client={new QueryClient()}>
        <OpportunityCorrectionForm item={job} onClose={vi.fn()} onCorrected={onCorrected} />
      </QueryClientProvider>,
    );
    fireEvent.change(screen.getByLabelText('Local'), {
      target: { value: 'Porto Alegre (híbrido)' },
    });
    fireEvent.change(screen.getByLabelText('Motivo ou origem da correção'), {
      target: { value: 'Confirmado na descrição' },
    });
    await userEvent.click(screen.getByRole('button', { name: 'Salvar correção' }));
    expect(fetcher).toHaveBeenCalledWith(
      '/api/opportunities/job-corr/corrections',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(onCorrected).toHaveBeenCalledTimes(1);
  });
});
