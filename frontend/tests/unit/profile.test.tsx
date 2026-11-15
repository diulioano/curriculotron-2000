import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { createQueryClient } from '../../src/app/queryClient';
import { Profile } from '../../src/pages/Profile';

const profile = (statement: string) => ({
  id: 'same-profile-id',
  status: 'imported',
  facts: [
    {
      id: 'fact-1',
      kind: 'skill',
      statement,
      origin: 'documented',
      originRef: 'curriculo.pdf',
    },
  ],
});

function json(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

it('reimporta os fatos no formulário quando o perfil mantém o mesmo id', async () => {
  let current = profile('Java legado');
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path === '/api/profile/import') {
        current = profile('Java atualizado');
        return json({ status: 'imported' });
      }
      if (path === '/api/profile' && init?.method === 'GET') return json(current);
      return json({});
    }),
  );
  const client = createQueryClient();
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/perfil']}>
        <Routes>
          <Route path="/perfil" element={<Profile />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );

  expect(await screen.findByText('Java legado')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Reimportar currículo' }));
  expect(await screen.findByText('Java atualizado')).toBeInTheDocument();
  expect(screen.queryByText('Java legado')).not.toBeInTheDocument();
});
