import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../src/app/App';
import { createQueryClient } from '../../src/app/queryClient';

const opportunity = {
  id: 'job-1',
  title: 'Desenvolvedor Java',
  companyName: 'SAP',
  location: 'São Leopoldo',
  workMode: 'hybrid',
  contractType: 'clt',
  salary: null,
  fitReasons: ['Java e Spring'],
  userDecision: 'none',
  availability: 'unconfirmed',
  facts: ['Java obrigatório'],
  inferred: ['Angular pode ser desejável'],
  missing: ['Salário'],
  sources: [
    {
      source: 'SAP Careers',
      url: 'https://careers.sap.com/job/1',
      fetchedAt: '2026-11-14T10:00:00Z',
    },
  ],
};

afterEach(() => {
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', '/');
});

function mount(path: string, fail = false) {
  window.history.replaceState({}, '', path);
  const fetcher = vi.fn(async (input: string, init?: RequestInit) => {
    let body: unknown = opportunity;
    let status = 200;
    if (input === '/api/session') body = { authenticated: true, csrfToken: 'csrf' };
    else if (fail) {
      status = 404;
      body = { error: { code: 'not_ready', message: 'Serviço indisponível.' } };
    } else if (input.startsWith('/api/opportunities?'))
      body = { items: [opportunity], dataAsOf: null };
    else if (init?.method === 'POST') body = {};
    return new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    });
  });
  vi.stubGlobal('fetch', fetcher);
  render(<App queryClient={createQueryClient()} />);
  return fetcher;
}

it('separa fatos, inferências e informação ausente; abrir fonte não registra candidatura', async () => {
  const fetcher = mount('/vagas/job-1');
  expect(await screen.findByRole('heading', { name: 'Desenvolvedor Java' })).toBeInTheDocument();
  expect(screen.getByText('Salário a confirmar')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Fatos da vaga' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Inferências' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: /SAP Careers/ })).toHaveAttribute(
    'href',
    opportunity.sources[0].url,
  );
  expect(fetcher.mock.calls.every(([, init]) => init?.method === 'GET')).toBe(true);
  await userEvent.click(screen.getByRole('button', { name: 'Me candidatei' }));
  expect(await screen.findByRole('status')).toHaveTextContent('Candidatura registrada');
  expect(fetcher).toHaveBeenCalledWith(
    '/api/opportunities/job-1/applied',
    expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ 'X-CSRF-Token': 'csrf' }),
    }),
  );
});

it('troca listas e permite recuperar uma vaga descartada', async () => {
  const fetcher = mount('/vagas?list=discarded');
  expect(await screen.findByRole('link', { name: 'Desenvolvedor Java' })).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Restaurar' }));
  expect(fetcher).toHaveBeenCalledWith(
    '/api/opportunities/job-1/restore',
    expect.objectContaining({ method: 'POST' }),
  );
  await userEvent.click(screen.getByRole('link', { name: 'Lista secundária' }));
  expect(await screen.findByRole('link', { name: 'Lista secundária' })).toHaveAttribute(
    'aria-current',
    'page',
  );
});

it('falha da API nunca aparece como lista vazia', async () => {
  mount('/vagas', true);
  expect(await screen.findByRole('alert')).toHaveTextContent('Esta área ainda não está disponível');
  expect(screen.queryByText('Nenhuma vaga nesta lista.')).not.toBeInTheDocument();
});
