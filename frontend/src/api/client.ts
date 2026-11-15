/**
 * Cliente da API privada do painel (contracts/panel-api.md).
 * - mesma origem, cookie de sessão HttpOnly (não acessível ao JS);
 * - mutações levam X-CSRF-Token obtido em GET /api/session;
 * - 401 avisa a aplicação para voltar à tela de entrada.
 */

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

export interface SessionInfo {
  authenticated: boolean;
  setupRequired: boolean;
  csrfToken: string | null;
}

type Listener = () => void;

let csrfToken: string | null = null;
const unauthorizedListeners = new Set<Listener>();

export function setCsrfToken(token: string | null): void {
  csrfToken = token;
}

/** Registra callback para 401 (sessão expirada). Retorna função de remoção. */
export function onUnauthorized(listener: Listener): () => void {
  unauthorizedListeners.add(listener);
  return () => unauthorizedListeners.delete(listener);
}

const MUTATIONS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  idempotencyKey?: string;
  signal?: AbortSignal;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (MUTATIONS.has(method) && csrfToken) {
    headers['X-CSRF-Token'] = csrfToken;
  }
  if (options.idempotencyKey) {
    headers['Idempotency-Key'] = options.idempotencyKey;
  }

  const response = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  });

  if (response.status === 401 && !path.startsWith('/api/session')) {
    unauthorizedListeners.forEach((l) => l());
  }

  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const data = (await response.json()) as { error?: { code?: string; message?: string } };
    if (data?.error?.code) {
      return new ApiError(response.status, data.error.code, data.error.message ?? 'Erro.');
    }
  } catch {
    // corpo não é JSON
  }
  return new ApiError(response.status, 'http_' + response.status, mensagemPadrao(response.status));
}

function mensagemPadrao(status: number): string {
  if (status === 401) return 'Entre com sua senha para continuar.';
  if (status === 403) return 'Requisição recusada. Recarregue a página.';
  if (status === 429) return 'Muitas tentativas. Aguarde alguns minutos.';
  if (status >= 500) return 'O serviço local não respondeu como esperado.';
  return 'Não foi possível concluir a operação.';
}

// ---- Sessão ----

export async function fetchSession(): Promise<SessionInfo> {
  const s = await api<SessionInfo>('/api/session');
  setCsrfToken(s.csrfToken);
  return s;
}

export async function setupPassword(password: string): Promise<void> {
  await api<void>('/api/setup', { method: 'POST', body: { password } });
}

export async function login(password: string): Promise<SessionInfo> {
  const s = await api<SessionInfo>('/api/session', { method: 'POST', body: { password } });
  setCsrfToken(s.csrfToken);
  return s;
}

export async function logout(): Promise<void> {
  await api<void>('/api/session', { method: 'DELETE' });
  setCsrfToken(null);
}
