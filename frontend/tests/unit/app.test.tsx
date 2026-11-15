import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../src/app/App';
import { createQueryClient } from '../../src/app/queryClient';

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

afterEach(() => vi.unstubAllGlobals());

describe('App', () => {
  it('modo local entra sem senha e oculta sair', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (path: string) =>
        json(
          200,
          path === '/api/session'
            ? { authenticated: true, setupRequired: false, csrfToken: 't', authMode: 'local' }
            : {
                dataAsOf: null,
                selectionToday: { count: 0, limit: 10, items: [] },
                integrations: [],
                applicationsWithNextActions: [],
                mailNeedingAttention: [],
                reviewQueueCount: 0,
                missedRoutines: [],
              },
        ),
      ),
    );
    render(<App queryClient={createQueryClient()} />);
    expect(await screen.findByRole('navigation', { name: 'Principal' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Entrar' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sair' })).not.toBeInTheDocument();
  });
  it('sem sessão mostra a tela de entrada', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          json(200, { authenticated: false, setupRequired: false, csrfToken: 't' }),
        ),
    );
    render(<App queryClient={createQueryClient()} />);
    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Principal' })).not.toBeInTheDocument();
  });

  it('primeiro uso pede definição de senha e valida confirmação', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          json(200, { authenticated: false, setupRequired: true, csrfToken: 't' }),
        ),
    );
    render(<App queryClient={createQueryClient()} />);
    expect(await screen.findByRole('heading', { name: 'Primeiro uso' })).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Senha'), 'senha-longa-123');
    await userEvent.type(screen.getByLabelText('Repita a senha'), 'outra-senha-123');
    await userEvent.click(screen.getByRole('button', { name: 'Definir senha e entrar' }));
    expect(screen.getByRole('alert')).toHaveTextContent('As senhas não conferem.');
  });

  it('com sessão mostra o painel com navegação', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (path: string) =>
        json(
          200,
          path === '/api/session'
            ? { authenticated: true, setupRequired: false, csrfToken: 't' }
            : {
                dataAsOf: null,
                selectionToday: { count: 0, limit: 10, items: [] },
                integrations: [],
                applicationsWithNextActions: [],
                mailNeedingAttention: [],
                reviewQueueCount: 0,
                missedRoutines: [],
              },
        ),
      ),
    );
    render(<App queryClient={createQueryClient()} />);
    const nav = await screen.findByRole('navigation', { name: 'Principal' });
    expect(within(nav).getByRole('link', { name: 'Hoje' })).toHaveAttribute('aria-current', 'page');
  });

  it('serviço local fora do ar mostra aviso', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    render(<App queryClient={createQueryClient()} />);
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(
      'não respondeu',
    );
  });
});
