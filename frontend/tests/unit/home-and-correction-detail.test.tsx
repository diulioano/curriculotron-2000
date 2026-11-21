import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import type {
  OpportunityDetail as DetailData,
  OpportunityTimelineEvent,
  SeniorityAssessment,
} from '../../src/api/panel';
import { OpportunityCard } from '../../src/components/OpportunityCard';
import { OpportunityCorrectionForm } from '../../src/components/OpportunityCorrectionForm';
import { getCompatibilityGeneration } from '../../src/api/useCompatibilityRefresh';
import { Home } from '../../src/pages/Home';
import { OpportunityDetail } from '../../src/pages/OpportunityDetail';
import { colorJob } from '../e2e/support/company-colors';

afterEach(() => {
  vi.unstubAllGlobals();
});

// ---------- infraestrutura de teste ----------

interface RecordedCall {
  path: string;
  method: string;
  body: unknown;
  headers: Record<string, string>;
}

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** Substitui `fetch`, registra as chamadas e responde 404 para rotas sem resposta definida. */
function stubFetch(handler: (call: RecordedCall) => Response | undefined) {
  const calls: RecordedCall[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (path: string, init: RequestInit = {}) => {
      const call: RecordedCall = {
        path,
        method: init.method ?? 'GET',
        body: typeof init.body === 'string' ? JSON.parse(init.body) : undefined,
        headers: (init.headers ?? {}) as Record<string, string>,
      };
      calls.push(call);
      return handler(call) ?? json(404, { error: { code: 'not_found', message: 'Sem rota.' } });
    }),
  );
  return calls;
}

function renderWithProviders(ui: ReactNode, route = '/') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  );
  return client;
}

function makeDetail(overrides: Partial<DetailData> = {}): DetailData {
  return {
    ...colorJob('SAP', 'job-1'),
    title: 'Engenheiro de Software Backend',
    ...overrides,
  };
}

const PICPAY_SOURCE = {
  source: 'oracle:picpay',
  url: 'https://vagas.example.test/picpay/42',
  fetchedAt: '2026-11-14T10:30:00Z',
};

// ---------- Home ----------

const readyAssessment = {
  criteriaVersion: 1,
  state: 'ready',
  stale: false,
  assessedCount: 5,
  remainingCount: 0,
  startedAt: null,
  finishedAt: '2026-11-20T10:00:00Z',
  errorCode: null,
};

function selection(count: number) {
  const items = Array.from({ length: count }, (_, index) => colorJob('SAP', `vaga-${index + 1}`));
  return { localDate: '2026-11-20', count, consumedCount: count, limit: 10, items };
}

function homeBody(overrides: Record<string, unknown> = {}) {
  return {
    dataAsOf: null,
    assessment: readyAssessment,
    selectionToday: selection(1),
    integrations: [],
    applicationsWithNextActions: [],
    mailNeedingAttention: [],
    reviewQueueCount: 0,
    missedRoutines: [],
    pendingReviewItems: [],
    ...overrides,
  };
}

function renderHome(body: unknown) {
  stubFetch((call) => (call.path === '/api/home' ? json(200, body) : undefined));
  return renderWithProviders(<Home />);
}

describe('Home: "Precisa de você"', () => {
  it('renderiza cada pendência individual como bloco PENDENTE DE REVISÃO com link para a rota alvo', async () => {
    renderHome(
      homeBody({
        pendingReviewItems: [
          {
            id: 'cand-1',
            kind: 'dedupe_candidate',
            kindLabel: 'DUPLICATA · revisão',
            title: 'Possível vaga duplicada: Itaú Unibanco',
            summary: 'Mesmo cargo e empresa detectados em Gupy e LinkedIn.',
            targetRoute: '/revisao/duplicatas',
          },
          {
            id: 'rev-1',
            kind: 'review_item',
            kindLabel: 'REVISÃO',
            title: 'Fato do perfil aguardando aprovação',
            summary: 'Uma habilidade foi sugerida e ainda não foi confirmada.',
            targetRoute: '/revisao',
          },
          {
            id: 'mail-1',
            kind: 'email_attention',
            kindLabel: 'E-MAIL',
            title: 'Resposta de recrutador sem classificação',
            summary: 'Assunto: Próximos passos do processo.',
            targetRoute: '/revisao?mail=mail-1',
          },
          {
            id: 'routine-1',
            kind: 'missed_routine',
            kindLabel: 'ROTINA',
            title: 'Busca diária não executou',
            summary: 'A rotina agendada para 07:00 foi perdida.',
            targetRoute: '/atividades',
          },
        ],
      }),
    );

    const section = await screen.findByRole('region', { name: /Precisa de você/ });
    expect(within(section).getByRole('heading', { level: 2 })).toHaveTextContent(
      'Precisa de você (4)',
    );
    expect(within(section).getAllByText(/^PENDENTE DE REVISÃO/)).toHaveLength(4);
    expect(within(section).queryByText('VAZIO')).not.toBeInTheDocument();

    const expectations = [
      {
        label: 'PENDENTE DE REVISÃO · DUPLICATA · revisão',
        title: 'Possível vaga duplicada: Itaú Unibanco',
        summary: 'Mesmo cargo e empresa detectados em Gupy e LinkedIn.',
        action: 'Comparar anúncios →',
        href: '/revisao/duplicatas',
      },
      {
        label: 'PENDENTE DE REVISÃO · REVISÃO',
        title: 'Fato do perfil aguardando aprovação',
        summary: 'Uma habilidade foi sugerida e ainda não foi confirmada.',
        action: 'Abrir fila de revisão →',
        href: '/revisao',
      },
      {
        label: 'PENDENTE DE REVISÃO · E-MAIL',
        title: 'Resposta de recrutador sem classificação',
        summary: 'Assunto: Próximos passos do processo.',
        action: 'Ler e classificar →',
        href: '/revisao?mail=mail-1',
      },
      {
        label: 'PENDENTE DE REVISÃO · ROTINA',
        title: 'Busca diária não executou',
        summary: 'A rotina agendada para 07:00 foi perdida.',
        action: 'Ver atividades →',
        href: '/atividades',
      },
    ];
    for (const expected of expectations) {
      expect(within(section).getByText(expected.label)).toBeVisible();
      expect(within(section).getByText(expected.title)).toBeVisible();
      expect(within(section).getByText(expected.summary)).toBeVisible();
      expect(within(section).getByRole('link', { name: expected.action })).toHaveAttribute(
        'href',
        expected.href,
      );
    }
  });

  it('sem pendências mostra o estado VAZIO em vez de blocos de revisão', async () => {
    renderHome(homeBody({ pendingReviewItems: [] }));

    const section = await screen.findByRole('region', { name: /Precisa de você/ });
    expect(within(section).getByText('VAZIO')).toBeVisible();
    expect(within(section).getByText('Nada esperando por você agora.')).toBeVisible();
    expect(within(section).queryByText(/PENDENTE DE REVISÃO/)).not.toBeInTheDocument();
  });
});

describe('Home: alerta de integração', () => {
  it('integração desconectada vira FALHA com causa, último sucesso e link para resolver', async () => {
    renderHome(
      homeBody({
        integrations: [
          {
            id: 'gmail',
            state: 'disconnected',
            lastSuccessAt: '2026-11-18T12:00:00Z',
            lastAttemptAt: '2026-11-20T09:00:00Z',
            detail: 'Token expirado',
          },
        ],
      }),
    );

    const heading = await screen.findByRole('heading', { name: 'Gmail desconectado' });
    const block = heading.closest('[data-surface-state]');
    expect(block).toHaveAttribute('data-surface-state', 'error');
    expect(block).toHaveAttribute('role', 'alert');
    expect(block).toHaveTextContent('FALHA');
    expect(block).toHaveTextContent('O que aconteceu: Token expirado');
    expect(block?.querySelector('time[datetime="2026-11-18T12:00:00Z"]')).not.toBeNull();
    expect(within(block as HTMLElement).getByRole('link')).toHaveAttribute('href', '/integracoes');
  });

  it('integração com falhas vira PARCIAL e sem sucesso anterior informa "nenhum ainda"', async () => {
    renderHome(
      homeBody({
        integrations: [
          {
            id: 'source:oracle:picpay',
            state: 'degraded',
            lastSuccessAt: null,
            lastAttemptAt: '2026-11-20T09:00:00Z',
          },
        ],
      }),
    );

    const heading = await screen.findByRole('heading', { name: 'Fonte oracle:picpay com falhas' });
    const block = heading.closest('[data-surface-state]');
    expect(block).toHaveAttribute('data-surface-state', 'partial');
    expect(block).toHaveTextContent('PARCIAL');
    expect(block).toHaveTextContent('Último sucesso: nenhum ainda.');
    expect(block).not.toHaveTextContent('O que aconteceu');
  });

  it('integração pausada ou saudável não gera alerta no painel lateral', async () => {
    renderHome(
      homeBody({
        integrations: [
          {
            id: 'openbot',
            state: 'ok',
            lastSuccessAt: '2026-11-20T08:00:00Z',
            lastAttemptAt: null,
          },
          {
            id: 'source:lever:ciandt',
            state: 'paused',
            lastSuccessAt: null,
            lastAttemptAt: null,
          },
        ],
      }),
    );

    await screen.findByRole('region', { name: /Precisa de você/ });
    expect(screen.queryByRole('heading', { name: /OpenBot/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /lever:ciandt/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});

describe('Home: seleção do dia', () => {
  it('com mais de 3 vagas oferece o atalho para as demais', async () => {
    renderHome(homeBody({ selectionToday: selection(5) }));

    const shortcut = await screen.findByRole('link', { name: 'Ver as outras 2 vagas do dia →' });
    expect(shortcut).toHaveAttribute('href', '/vagas');
    expect(screen.getAllByRole('article')).toHaveLength(5);
  });

  it('com até 3 vagas não mostra o atalho', async () => {
    renderHome(homeBody({ selectionToday: selection(3) }));

    await screen.findAllByRole('article');
    expect(screen.queryByRole('link', { name: /Ver as outras/ })).not.toBeInTheDocument();
  });
});

// ---------- OpportunityCorrectionForm ----------

function renderForm(item: DetailData) {
  const onClose = vi.fn();
  const onCorrected = vi.fn();
  const client = renderWithProviders(
    <OpportunityCorrectionForm item={item} onClose={onClose} onCorrected={onCorrected} />,
  );
  return { client, onClose, onCorrected };
}

function submitCorrection() {
  return userEvent.click(screen.getByRole('button', { name: 'Salvar correção' }));
}

const SALARIED = {
  id: 'job-corr',
  salary: { min: 9000, max: 12000, unit: 'monthly' as const },
  deadlineAt: '2026-11-30',
  publishedAt: '2026-11-11',
};

describe('OpportunityCorrectionForm: envio', () => {
  it('envia apenas os campos alterados para POST /corrections e dispara o callback com a vaga atualizada', async () => {
    const item = makeDetail(SALARIED);
    const updated = {
      ...item,
      title: 'Desenvolvedor(a) Full Stack',
      workMode: 'hybrid',
      salary: { min: 9500, max: 13000, unit: 'monthly' },
    };
    const calls = stubFetch((call) =>
      call.method === 'POST' && call.path === '/api/opportunities/job-corr/corrections'
        ? json(200, updated)
        : undefined,
    );
    const { client, onClose, onCorrected } = renderForm(item);
    const generationBeforeCorrection = getCompatibilityGeneration(client);

    fireEvent.change(screen.getByLabelText('Título'), {
      target: { value: 'Desenvolvedor(a) Full Stack' },
    });
    fireEvent.click(screen.getByLabelText('Modalidade'));
    fireEvent.click(screen.getByRole('option', { name: 'Híbrido' }));
    fireEvent.change(screen.getByLabelText('Salário mínimo (R$)'), { target: { value: '9500' } });
    fireEvent.change(screen.getByLabelText('Salário máximo (R$)'), { target: { value: '13000' } });
    fireEvent.change(screen.getByLabelText('Motivo ou origem da correção'), {
      target: { value: '  Confirmado no edital oficial  ' },
    });
    await submitCorrection();

    await waitFor(() => expect(onCorrected).toHaveBeenCalledTimes(1));
    expect(calls).toHaveLength(1);
    expect(calls[0].body).toEqual({
      title: 'Desenvolvedor(a) Full Stack',
      workMode: 'hybrid',
      salary: { min: 9500, max: 13000, unit: 'monthly' },
      reason: 'Confirmado no edital oficial',
    });
    expect(calls[0].headers['Content-Type']).toBe('application/json');
    expect(calls[0].headers['Idempotency-Key']).toMatch(/^[0-9a-f-]{36}$/);
    expect(onCorrected).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'job-corr', title: 'Desenvolvedor(a) Full Stack' }),
    );
    expect(onClose).not.toHaveBeenCalled();
    await waitFor(() =>
      expect(getCompatibilityGeneration(client)).toBeGreaterThan(generationBeforeCorrection),
    );
    expect(client.getQueryData(['opportunity', 'job-corr'])).toMatchObject({
      title: 'Desenvolvedor(a) Full Stack',
    });
    expect(
      await screen.findByText(/Correção registrada — fonte original preservada\./),
    ).toBeVisible();
  });

  it('"Remover salário declarado" desabilita os campos de salário e envia somente clearSalary', async () => {
    const item = makeDetail(SALARIED);
    const calls = stubFetch((call) => (call.method === 'POST' ? json(200, item) : undefined));
    const { onCorrected } = renderForm(item);

    await userEvent.click(screen.getByLabelText(/Remover salário declarado/));
    expect(screen.getByLabelText('Salário mínimo (R$)')).toBeDisabled();
    expect(screen.getByLabelText('Salário máximo (R$)')).toBeDisabled();
    expect(screen.getByLabelText('Unidade do salário')).toBeDisabled();
    await submitCorrection();

    await waitFor(() => expect(onCorrected).toHaveBeenCalledTimes(1));
    expect(calls[0].body).toEqual({ clearSalary: true });
  });

  it('esvaziar mínimo e máximo de uma vaga com salário equivale a limpar o salário', async () => {
    const item = makeDetail(SALARIED);
    const calls = stubFetch((call) => (call.method === 'POST' ? json(200, item) : undefined));
    const { onCorrected } = renderForm(item);

    fireEvent.change(screen.getByLabelText('Salário mínimo (R$)'), { target: { value: '' } });
    fireEvent.change(screen.getByLabelText('Salário máximo (R$)'), { target: { value: '' } });
    await submitCorrection();

    await waitFor(() => expect(onCorrected).toHaveBeenCalledTimes(1));
    expect(calls[0].body).toEqual({ clearSalary: true });
  });

  it('limpar prazo e publicação envia null explícito para as duas datas', async () => {
    const item = makeDetail(SALARIED);
    const calls = stubFetch((call) => (call.method === 'POST' ? json(200, item) : undefined));
    const { onCorrected } = renderForm(item);

    expect(screen.getByLabelText('Prazo de inscrição')).toHaveValue('2026-11-30');
    expect(screen.getByLabelText('Data de publicação')).toHaveValue('2026-11-11');
    fireEvent.change(screen.getByLabelText('Prazo de inscrição'), { target: { value: '' } });
    fireEvent.change(screen.getByLabelText('Data de publicação'), { target: { value: '' } });
    await submitCorrection();

    await waitFor(() => expect(onCorrected).toHaveBeenCalledTimes(1));
    expect(calls[0].body).toEqual({ deadlineAt: null, publishedAt: null });
  });

  it('alterar apenas o prazo envia a nova data e preserva a publicação', async () => {
    const item = makeDetail(SALARIED);
    const calls = stubFetch((call) => (call.method === 'POST' ? json(200, item) : undefined));
    const { onCorrected } = renderForm(item);

    fireEvent.change(screen.getByLabelText('Prazo de inscrição'), {
      target: { value: '2026-12-15' },
    });
    await submitCorrection();

    await waitFor(() => expect(onCorrected).toHaveBeenCalledTimes(1));
    expect(calls[0].body).toEqual({ deadlineAt: '2026-12-15' });
  });
});

describe('OpportunityCorrectionForm: validação e erros', () => {
  it('sem nenhuma alteração avisa em role="alert" e não chama a API', async () => {
    const calls = stubFetch(() => undefined);
    const { onCorrected } = renderForm(makeDetail(SALARIED));

    await submitCorrection();

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Altere pelo menos um campo antes de salvar a correção.',
    );
    expect(calls).toHaveLength(0);
    expect(onCorrected).not.toHaveBeenCalled();
  });

  it('faixa salarial com mínimo maior que o máximo é recusada no cliente', async () => {
    const calls = stubFetch(() => undefined);
    renderForm(makeDetail({ id: 'job-corr' }));

    fireEvent.change(screen.getByLabelText('Salário mínimo (R$)'), { target: { value: '12000' } });
    fireEvent.change(screen.getByLabelText('Salário máximo (R$)'), { target: { value: '8000' } });
    await submitCorrection();

    expect(screen.getByRole('alert')).toHaveTextContent(
      'O salário mínimo não pode ser maior que o máximo.',
    );
    expect(calls).toHaveLength(0);
  });

  it('409 opportunity_merged mostra a mensagem do servidor em role="alert" e mantém o formulário aberto', async () => {
    stubFetch((call) =>
      call.method === 'POST'
        ? json(409, {
            error: {
              code: 'opportunity_merged',
              message: 'Esta vaga foi fundida na vaga job-2. Corrija a vaga sobrevivente.',
            },
          })
        : undefined,
    );
    const { onCorrected } = renderForm(makeDetail({ id: 'job-corr' }));

    fireEvent.change(screen.getByLabelText('Local'), { target: { value: 'São Leopoldo, RS' } });
    await submitCorrection();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(
      'Esta vaga foi fundida na vaga job-2. Corrija a vaga sobrevivente.',
    );
    expect(onCorrected).not.toHaveBeenCalled();
    expect(screen.getByRole('form', { name: 'Corrigir dados da vaga' })).toBeVisible();
    expect(screen.queryByText(/Correção registrada/)).not.toBeInTheDocument();
  });

  it('404 informa que nenhuma alteração foi salva', async () => {
    stubFetch(() => undefined);
    renderForm(makeDetail({ id: 'job-corr' }));

    fireEvent.change(screen.getByLabelText('Local'), { target: { value: 'Canoas, RS' } });
    await submitCorrection();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Vaga ou serviço de correção indisponível. Nenhuma alteração foi salva.',
    );
  });

  it('Fechar e Cancelar acionam onClose sem chamar a API', async () => {
    const calls = stubFetch(() => undefined);
    const { onClose } = renderForm(makeDetail({ id: 'job-corr' }));

    await userEvent.click(screen.getByRole('button', { name: 'Fechar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(onClose).toHaveBeenCalledTimes(2);
    expect(calls).toHaveLength(0);
  });
});

// ---------- OpportunityDetail ----------

function renderDetail(
  detail: () => DetailData,
  onPost?: (call: RecordedCall) => Response | undefined,
) {
  const calls = stubFetch((call) => {
    if (call.method === 'GET' && call.path === '/api/opportunities/job-1') {
      return json(200, detail());
    }
    return onPost?.(call);
  });
  renderWithProviders(
    <Routes>
      <Route path="/vagas/:id" element={<OpportunityDetail />} />
    </Routes>,
    '/vagas/job-1',
  );
  return calls;
}

async function findNivelBlock(detail: DetailData) {
  await screen.findByRole('heading', { level: 1, name: detail.title });
  return screen.getByText('NÍVEL').closest('div') as HTMLElement;
}

describe('OpportunityDetail: NÍVEL', () => {
  it('fato declarado aparece com ✓ e sem marcador de inferência', async () => {
    const detail = makeDetail({ seniority: { label: 'Pleno', status: 'fact', reason: null } });
    renderDetail(() => detail);

    const block = await findNivelBlock(detail);
    expect(block.querySelector('.ep-fact-inline')).toHaveTextContent('✓ Pleno');
    expect(block.querySelector('.ep-inference-inline')).toBeNull();
    expect(block.querySelector('.ep-absent')).toBeNull();
  });

  it('inferência aparece com ~ e o motivo entre parênteses', async () => {
    const detail = makeDetail({
      seniority: { label: 'Pleno', status: 'inferred', reason: 'inferido: “3+ anos”' },
    });
    renderDetail(() => detail);

    const block = await findNivelBlock(detail);
    expect(block.querySelector('.ep-inference-inline')).toHaveTextContent(
      '~ Pleno (inferido: “3+ anos”)',
    );
    expect(block.querySelector('.ep-fact-inline')).toBeNull();
  });

  it.each<[string, SeniorityAssessment]>([
    ['ausente', { label: null, status: 'absent', reason: null }],
    ['inferido sem rótulo', { label: null, status: 'inferred', reason: 'sem evidência' }],
  ])('nível %s aparece como "a confirmar" hachurado', async (_name, seniority) => {
    const detail = makeDetail({ seniority });
    renderDetail(() => detail);

    const block = await findNivelBlock(detail);
    expect(block.querySelector('.ep-absent.hatch')).toHaveTextContent('a confirmar');
    expect(block.querySelector('.ep-fact-inline')).toBeNull();
    expect(block.querySelector('.ep-inference-inline')).toBeNull();
  });
});

describe('OpportunityDetail: Linha do tempo', () => {
  const discovered: OpportunityTimelineEvent = {
    id: 'src-1',
    kind: 'discovered',
    actorLabel: 'bot Busca de vagas',
    description: 'Encontrada em careers.sap.com.',
    note: null,
    at: '2026-11-14T10:33:00Z',
  };
  const opened: OpportunityTimelineEvent = {
    id: 'act-1',
    kind: 'external_opened',
    actorLabel: 'você',
    description: 'Abriu o link da vaga.',
    note: 'isso não registra candidatura',
    at: '2026-11-14T12:02:00Z',
  };

  it('lista os eventos em ordem cronológica com autor e observação', async () => {
    const detail = makeDetail({ timeline: [opened, discovered] });
    renderDetail(() => detail);

    const section = await screen.findByRole('region', { name: 'Linha do tempo' });
    const events = within(section).getAllByRole('listitem');
    expect(events).toHaveLength(2);
    expect(events[0]).toHaveTextContent('bot Busca de vagas');
    expect(events[0]).toHaveTextContent('Encontrada em careers.sap.com.');
    expect(events[1]).toHaveTextContent('você');
    expect(events[1]).toHaveTextContent('Abriu o link da vaga.');
    expect(within(events[1]).getByText('(isso não registra candidatura)')).toBeVisible();
    expect(within(section).queryByText('VAZIO')).not.toBeInTheDocument();
  });

  it('sem eventos mostra o estado VAZIO explicativo', async () => {
    const detail = makeDetail({ timeline: [] });
    renderDetail(() => detail);

    const section = await screen.findByRole('region', { name: 'Linha do tempo' });
    expect(within(section).getByText('VAZIO')).toBeVisible();
    expect(within(section).getByText('Nenhum evento registrado ainda.')).toBeVisible();
    expect(within(section).queryByRole('listitem')).not.toBeInTheDocument();
  });
});

describe('OpportunityDetail: "Abrir vaga no site ↗"', () => {
  const withSource = () => makeDetail({ sources: [PICPAY_SOURCE], timeline: [] });

  it('registra a visita externa no backend sem criar candidatura nem desabilitar "Me candidatei"', async () => {
    let visited = false;
    const calls = renderDetail(
      () =>
        makeDetail({
          sources: [PICPAY_SOURCE],
          timeline: visited
            ? [
                {
                  id: 'act-1',
                  kind: 'external_opened',
                  actorLabel: 'você',
                  description: 'Abriu o link da vaga.',
                  note: 'isso não registra candidatura',
                  at: '2026-11-20T12:02:00Z',
                },
              ]
            : [],
        }),
      (call) => {
        if (call.method === 'POST' && call.path === '/api/opportunities/job-1/external-visit') {
          visited = true;
          return new Response(null, { status: 204 });
        }
        return undefined;
      },
    );

    const link = await screen.findByRole('link', { name: 'Abrir vaga no site ↗' });
    expect(link).toHaveAttribute('href', PICPAY_SOURCE.url);
    expect(link).toHaveAttribute('target', '_blank');
    // Evita a navegação do jsdom; o handler do React ainda recebe o clique.
    link.addEventListener('click', (event) => event.preventDefault());
    await userEvent.click(link);

    expect(await screen.findByText(/Abriu o link da vaga\./)).toBeVisible();
    expect(screen.getByText('(isso não registra candidatura)')).toBeVisible();
    const posts = calls.filter((call) => call.method === 'POST').map((call) => call.path);
    expect(posts).toEqual(['/api/opportunities/job-1/external-visit']);
    expect(screen.getByRole('button', { name: 'Me candidatei' })).toBeEnabled();
    expect(screen.queryByText('Candidatura já registrada')).not.toBeInTheDocument();
  });

  it('falha ao registrar a visita não exibe erro nem bloqueia a decisão do usuário', async () => {
    const calls = renderDetail(withSource, (call) =>
      call.method === 'POST'
        ? json(500, { error: { code: 'internal', message: 'Falha interna.' } })
        : undefined,
    );

    const link = await screen.findByRole('link', { name: 'Abrir vaga no site ↗' });
    link.addEventListener('click', (event) => event.preventDefault());
    await userEvent.click(link);

    await waitFor(() =>
      expect(calls.some((call) => call.path === '/api/opportunities/job-1/external-visit')).toBe(
        true,
      ),
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Me candidatei' })).toBeEnabled();
  });

  it('sem fonte com URL http(s) segura não oferece o atalho', async () => {
    const detail = makeDetail({
      sources: [{ ...PICPAY_SOURCE, url: 'javascript:alert(1)' }],
    });
    renderDetail(() => detail);

    await screen.findByRole('heading', { level: 1, name: detail.title });
    expect(screen.queryByRole('link', { name: 'Abrir vaga no site ↗' })).not.toBeInTheDocument();
  });
});

describe('OpportunityDetail: correção manual', () => {
  it('abre o formulário, salva a correção e mostra "corrigido por você" ao fechar', async () => {
    const original = makeDetail({ location: 'Porto Alegre', sources: [PICPAY_SOURCE] });
    const corrected = makeDetail({
      location: 'São Leopoldo, RS',
      sources: [PICPAY_SOURCE],
      corrections: [
        {
          id: 'corr-1',
          fieldName: 'location',
          previousValue: 'Porto Alegre',
          correctedValue: 'São Leopoldo, RS',
          reason: 'Confirmado no edital',
          authorLabel: 'você',
          correctedAt: '2026-11-20T12:00:00Z',
        },
      ],
    });
    let current = original;
    const calls = renderDetail(
      () => current,
      (call) => {
        if (call.method === 'POST' && call.path === '/api/opportunities/job-1/corrections') {
          current = corrected;
          return json(200, corrected);
        }
        return undefined;
      },
    );

    await userEvent.click(await screen.findByRole('button', { name: 'Corrigir algum dado →' }));
    const form = screen.getByRole('form', { name: 'Corrigir dados da vaga' });
    expect(screen.getByRole('button', { name: 'Fechar correção' })).toBeVisible();
    expect(screen.queryByTestId('latest-correction')).not.toBeInTheDocument();

    fireEvent.change(within(form).getByLabelText('Local'), {
      target: { value: 'São Leopoldo, RS' },
    });
    fireEvent.change(within(form).getByLabelText('Motivo ou origem da correção'), {
      target: { value: 'Confirmado no edital' },
    });
    await userEvent.click(within(form).getByRole('button', { name: 'Salvar correção' }));

    await waitFor(() =>
      expect(
        screen.queryByRole('form', { name: 'Corrigir dados da vaga' }),
      ).not.toBeInTheDocument(),
    );
    expect(calls.find((call) => call.method === 'POST')?.body).toEqual({
      location: 'São Leopoldo, RS',
      reason: 'Confirmado no edital',
    });
    const latest = await screen.findByTestId('latest-correction');
    expect(latest).toHaveTextContent('corrigido por você');
    expect(latest).toHaveTextContent('Confirmado no edital');
    expect(screen.getByText('São Leopoldo, RS')).toBeVisible();
    // A fonte original continua exibida.
    expect(screen.getByRole('link', { name: /fonte: oracle:picpay/ })).toHaveAttribute(
      'href',
      PICPAY_SOURCE.url,
    );
    expect(screen.getByRole('button', { name: 'Corrigir algum dado →' })).toBeVisible();
    expect(screen.getByRole('status')).toHaveTextContent('Correção registrada');
  });
});

describe('OpportunityDetail: provenance corrections', () => {
  it('mostra valores corrigidos não nulos sem check e nível derivado do título corrigido', async () => {
    const fields = ['location', 'work_mode', 'contract_type', 'availability', 'title'] as const;
    const item = makeDetail({
      title: 'Engenheira de Plataforma',
      location: 'Curitiba',
      workMode: 'remote',
      contractType: 'clt',
      availability: 'open',
      seniority: { label: 'Sênior', status: 'corrected_title', reason: null },
      corrections: fields.map((fieldName, index) => ({
        id: `non-null-${fieldName}`,
        fieldName,
        previousValue: 'valor da fonte',
        correctedValue: 'valor corrigido',
        reason: null,
        authorLabel: 'você',
        correctedAt: `2026-11-20T12:0${index}:00Z`,
      })),
    });
    renderDetail(() => item);
    await screen.findByRole('heading', { level: 1, name: /Engenheira de Plataforma/ });
    const summary = screen.getByRole('region', { name: 'Resumo' });
    for (const label of ['Curitiba', 'Remoto', 'CLT', 'aberta']) {
      const value = within(summary).getByText(label).closest('.ep-fact, .ep-fact-inline');
      expect(value).toBeVisible();
      expect(value?.querySelector('[aria-hidden="true"]')).not.toBeInTheDocument();
      expect(value?.parentElement).toHaveTextContent('corrigido por você');
    }
    expect(within(summary).getByText(/Sênior/)).toHaveTextContent('derivado do título corrigido');
  });

  it('attributes changed and cleared values to the person, preserves source originals, and shows an absent deadline', async () => {
    const correctionFields = [
      'title',
      'company_name',
      'location',
      'salary_min',
      'salary_max',
      'salary_unit',
      'deadline_at',
      'published_at',
      'availability',
    ] as const;
    const item = makeDetail({
      title: 'Título revisado',
      companyName: 'Empresa revisada',
      location: '',
      salary: null,
      deadlineAt: null,
      publishedAt: null,
      availability: 'unconfirmed',
      corrections: correctionFields.map((fieldName, index) => ({
        id: `corr-${index}`,
        fieldName,
        previousValue:
          fieldName === 'title'
            ? 'Título da fonte'
            : fieldName === 'company_name'
              ? 'Empresa da fonte'
              : fieldName.startsWith('salary')
                ? '9000'
                : '2026-11-30',
        correctedValue: null,
        reason: null,
        authorLabel: 'você',
        correctedAt: `2026-11-20T12:0${index}:00Z`,
      })),
    });
    renderDetail(() => item);

    expect(await screen.findByRole('heading', { level: 1, name: /Título revisado/ })).toBeVisible();
    expect(screen.getByText('Empresa revisada')).toBeVisible();
    expect(screen.getByText(/valor original da fonte: Título da fonte/)).toBeVisible();
    expect(screen.getByText(/valor original da fonte: Empresa da fonte/)).toBeVisible();
    expect(screen.getByText(/valor original da fonte: 9000/)).toBeVisible();
    expect(screen.getByText('Prazo não informado')).toBeVisible();
    expect(screen.getByText('Data de publicação desconhecida')).toBeVisible();
    expect(screen.getAllByText('a confirmar').length).toBeGreaterThan(0);
    expect(screen.getAllByText(/corrigido por você/).length).toBeGreaterThanOrEqual(5);
    const publishedEvidence = screen.getByText(/publicada:/).closest('.ep-corrected-field');
    expect(publishedEvidence).toHaveTextContent('corrigido por você');
  });

  it('mostra autoria por subcampo salarial e mantém o primeiro valor de origem após correções repetidas', async () => {
    const item = makeDetail({
      salary: { min: 10000, max: 14000, unit: 'monthly' },
      corrections: [
        {
          id: 'salary-min-first',
          fieldName: 'salary_min',
          previousValue: '9000',
          correctedValue: '10000',
          reason: null,
          authorLabel: 'você',
          correctedAt: '2026-11-19T10:00:00Z',
        },
        {
          id: 'salary-min-latest',
          fieldName: 'salary_min',
          previousValue: '9500',
          correctedValue: '10000',
          reason: null,
          authorLabel: 'você',
          correctedAt: '2026-11-21T10:00:00Z',
        },
        {
          id: 'salary-max',
          fieldName: 'salary_max',
          previousValue: '13000',
          correctedValue: '14000',
          reason: null,
          authorLabel: 'você',
          correctedAt: '2026-11-20T10:00:00Z',
        },
        {
          id: 'salary-unit',
          fieldName: 'salary_unit',
          previousValue: 'yearly',
          correctedValue: 'monthly',
          reason: null,
          authorLabel: 'você',
          correctedAt: '2026-11-18T10:00:00Z',
        },
      ],
    });
    renderDetail(() => item);

    const minimumCorrection = await screen.findByText(/salário mínimo: corrigido por você/);
    const maximumCorrection = screen.getByText(/salário máximo: corrigido por você/);
    const unitCorrection = screen.getByText(/unidade salarial: corrigido por você/);
    expect(minimumCorrection).toBeVisible();
    expect(maximumCorrection).toBeVisible();
    expect(unitCorrection).toBeVisible();
    expect(minimumCorrection.querySelector('time')).toHaveAttribute(
      'datetime',
      '2026-11-21T10:00:00Z',
    );
    expect(maximumCorrection.querySelector('time')).toHaveAttribute(
      'datetime',
      '2026-11-20T10:00:00Z',
    );
    expect(unitCorrection.querySelector('time')).toHaveAttribute(
      'datetime',
      '2026-11-18T10:00:00Z',
    );
    expect(screen.getByText(/valor original da fonte: 9000.*13000.*yearly/)).toBeVisible();
    const salaryFact = screen.getByText(/10\.000/).closest('.ep-fact');
    expect(salaryFact?.querySelector('[aria-hidden="true"]')).not.toBeInTheDocument();
  });
});
// ---------- OpportunityCard ----------

describe('OpportunityCard: dados ausentes', () => {
  it('empresa, local, salário e publicação ausentes mostram marcadores explícitos', () => {
    const item = {
      ...colorJob('', 'card-vazio'),
      location: '',
      workMode: 'unknown' as const,
      contractType: 'unknown' as const,
      salary: null,
    };
    renderWithProviders(<OpportunityCard item={item} />);

    const card = screen.getByRole('article');
    expect(within(card).getByText('Empresa não informada')).toBeVisible();
    expect(within(card).getByText('Local a confirmar')).toBeVisible();
    expect(within(card).getByText('salário a confirmar')).toBeVisible();
    expect(within(card).getByText('Data de publicação desconhecida')).toBeVisible();
    expect(card.style.getPropertyValue('--c')).toBe('#6B6B73');
    expect(within(card).getAllByText('Empresa não informada')[0].closest('.ep-absent')).toHaveClass(
      'hatch',
    );
  });

  it('com dados completos mostra fatos com ✓ e nenhum marcador de ausência', () => {
    const item = {
      ...colorJob('PicPay', 'card-completo'),
      location: 'Porto Alegre',
      workMode: 'hybrid' as const,
      salary: { min: 9000, max: 12000, unit: 'monthly' as const },
      publishedAt: '2026-11-11',
    };
    renderWithProviders(<OpportunityCard item={item} />);

    const card = screen.getByRole('article');
    expect(within(card).getByText('PicPay')).toBeVisible();
    expect(within(card).getByText('Porto Alegre')).toBeVisible();
    expect(within(card).getByText('híbrido')).toBeVisible();
    expect(within(card).getByText('CLT')).toBeVisible();
    expect(within(card).getByText(/9\.000 – 12\.000/)).toBeVisible();
    expect(within(card).getByText('publicada: 2026-11-11')).toBeVisible();
    expect(within(card).queryByText('Empresa não informada')).not.toBeInTheDocument();
    expect(within(card).queryByText('Local a confirmar')).not.toBeInTheDocument();
    expect(within(card).queryByText('salário a confirmar')).not.toBeInTheDocument();
    expect(within(card).queryByText('Data de publicação desconhecida')).not.toBeInTheDocument();
  });
});
