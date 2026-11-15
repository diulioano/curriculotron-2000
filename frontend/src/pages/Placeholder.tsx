import { Link } from 'react-router';

/** Página ainda não implementada (próximas fases do tasks.md). */
export function Placeholder({ title }: { title: string }) {
  return (
    <section style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <h1 style={{ fontSize: 40 }}>{title}</h1>
      <p className="muted" style={{ margin: 0 }}>
        Esta tela ainda está em construção.
      </p>
    </section>
  );
}

export function NotFound() {
  return (
    <section style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <h1 style={{ fontSize: 40 }}>Página não encontrada</h1>
      <Link to="/">Voltar para Hoje →</Link>
    </section>
  );
}
