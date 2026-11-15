import { api, ApiError, onUnauthorized, setCsrfToken } from '../../src/api/client';

function mockFetch(status: number, body?: unknown) {
  const fn = vi.fn().mockImplementation(
    async () =>
      new Response(body === undefined ? null : JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
      }),
  );
  vi.stubGlobal('fetch', fn);
  return fn;
}

afterEach(() => {
  vi.unstubAllGlobals();
  setCsrfToken(null);
});

describe('cliente da API', () => {
  it('envia X-CSRF-Token só em mutações', async () => {
    setCsrfToken('tok-123');
    const fetchMock = mockFetch(200, { ok: true });

    await api('/api/home');
    await api('/api/searches', { method: 'POST', body: {} });

    const getHeaders = fetchMock.mock.calls[0][1].headers as Record<string, string>;
    const postHeaders = fetchMock.mock.calls[1][1].headers as Record<string, string>;
    expect(getHeaders['X-CSRF-Token']).toBeUndefined();
    expect(postHeaders['X-CSRF-Token']).toBe('tok-123');
    expect(fetchMock.mock.calls[1][1].credentials).toBe('same-origin');
  });

  it('envia Idempotency-Key quando informada', async () => {
    const fetchMock = mockFetch(200, {});
    await api('/api/searches', { method: 'POST', body: {}, idempotencyKey: 'k1' });
    expect((fetchMock.mock.calls[0][1].headers as Record<string, string>)['Idempotency-Key']).toBe(
      'k1',
    );
  });

  it('converte {error:{code,message}} em ApiError', async () => {
    mockFetch(409, { error: { code: 'already_configured', message: 'A senha já foi definida.' } });
    await expect(api('/api/setup', { method: 'POST', body: {} })).rejects.toMatchObject({
      status: 409,
      code: 'already_configured',
      message: 'A senha já foi definida.',
    });
  });

  it('401 avisa os ouvintes (volta à tela de entrada)', async () => {
    mockFetch(401, { error: { code: 'unauthorized', message: 'x' } });
    const listener = vi.fn();
    const off = onUnauthorized(listener);
    await expect(api('/api/home')).rejects.toBeInstanceOf(ApiError);
    expect(listener).toHaveBeenCalledTimes(1);
    off();
  });

  it('204 devolve undefined', async () => {
    mockFetch(204);
    await expect(api('/api/session', { method: 'DELETE' })).resolves.toBeUndefined();
  });
});
