import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useId, useState, type FormEvent } from 'react';
import { ApiError, fetchSession, login, setupPassword } from '../api/client';
import { SESSION_KEY } from '../app/queryClient';
import { Logo } from '../components/Logo';

const MIN_LENGTH = 10;

interface Props {
  setupRequired: boolean;
}

/** Primeiro uso: define a senha. Depois: entra com ela (D-06). */
export function Login({ setupRequired }: Props) {
  const queryClient = useQueryClient();
  const passwordId = useId();
  const confirmId = useId();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);

  const submit = useMutation({
    mutationFn: async () => {
      if (setupRequired) {
        await setupPassword(password);
        await fetchSession(); // novo token CSRF para o login
      }
      return login(password);
    },
    onSuccess: (session) => {
      setPassword('');
      setConfirm('');
      queryClient.setQueryData(SESSION_KEY, session);
    },
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    setLocalError(null);
    if (setupRequired) {
      if (password.length < MIN_LENGTH) {
        setLocalError(`A senha precisa ter pelo menos ${MIN_LENGTH} caracteres.`);
        return;
      }
      if (password !== confirm) {
        setLocalError('As senhas não conferem.');
        return;
      }
    }
    submit.mutate();
  }

  const error =
    localError ??
    (submit.error instanceof ApiError
      ? submit.error.message
      : submit.error
        ? 'Não foi possível entrar.'
        : null);

  return (
    <main
      id="conteudo"
      style={{
        maxWidth: 480,
        margin: '0 auto',
        padding: '48px 16px',
        display: 'flex',
        flexDirection: 'column',
        gap: 28,
      }}
    >
      <Logo />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <h1 style={{ fontSize: 36 }}>{setupRequired ? 'Primeiro uso' : 'Entrar'}</h1>
        <p style={{ margin: 0 }}>
          {setupRequired
            ? 'Defina a senha do painel. Ela protege seus dados no computador e no celular.'
            : 'Digite a senha do painel.'}
        </p>
      </div>

      <form
        onSubmit={onSubmit}
        className="sk"
        style={{
          background: 'var(--card)',
          padding: 20,
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
        noValidate
      >
        <div className="field">
          <label htmlFor={passwordId}>Senha</label>
          <input
            id={passwordId}
            className="input"
            type="password"
            autoComplete={setupRequired ? 'new-password' : 'current-password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={setupRequired ? MIN_LENGTH : undefined}
            aria-describedby={setupRequired ? `${passwordId}-hint` : undefined}
          />
          {setupRequired && (
            <span id={`${passwordId}-hint`} className="mono muted" style={{ fontSize: 13 }}>
              mínimo de {MIN_LENGTH} caracteres
            </span>
          )}
        </div>

        {setupRequired && (
          <div className="field">
            <label htmlFor={confirmId}>Repita a senha</label>
            <input
              id={confirmId}
              className="input"
              type="password"
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              required
            />
          </div>
        )}

        {error && (
          <div className="alert dsh" role="alert">
            {error}
          </div>
        )}

        <button
          className="btn bt bt-pri"
          type="submit"
          disabled={submit.isPending}
          style={{ alignSelf: 'flex-start' }}
        >
          {submit.isPending ? 'Entrando…' : setupRequired ? 'Definir senha e entrar' : 'Entrar'}
        </button>
      </form>

      <p className="mono muted" style={{ fontSize: 13, margin: 0 }}>
        O painel só funciona na Wi-Fi de casa, com este computador ligado.
      </p>
    </main>
  );
}
