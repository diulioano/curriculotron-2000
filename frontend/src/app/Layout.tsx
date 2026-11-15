import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, NavLink, Outlet } from 'react-router';
import { logout } from '../api/client';
import { panel } from '../api/panel';
import { Landscape } from '../components/Landscape';
import { Logo } from '../components/Logo';
import './layout.css';

const NAV = [
  { to: '/', label: 'Hoje', end: true },
  { to: '/vagas', label: 'Vagas' },
  { to: '/candidaturas', label: 'Candidaturas' },
  { to: '/revisao', label: 'Revisão', badge: true },
  { to: '/bots', label: 'Bots' },
  { to: '/atividades', label: 'Atividades' },
  { to: '/perfil', label: 'Perfil' },
];

const FOOTER = [
  {
    title: 'Revisão',
    links: [
      { to: '/', label: 'Hoje' },
      { to: '/vagas', label: 'Vagas' },
      { to: '/candidaturas', label: 'Candidaturas' },
      { to: '/revisao', label: 'Fila de revisão' },
    ],
  },
  {
    title: 'Bots',
    links: [
      { to: '/bots', label: 'Coordenador' },
      { to: '/bots', label: 'Busca de vagas' },
      { to: '/bots', label: 'Acompanhamento' },
      { to: '/bots', label: 'Preparação' },
    ],
  },
  {
    title: 'Sistema',
    links: [
      { to: '/atividades', label: 'Atividades' },
      { to: '/integracoes', label: 'Integrações' },
      { to: '/perfil', label: 'Perfil' },
    ],
  },
];

export function Layout() {
  const queryClient = useQueryClient();
  const exit = useMutation({
    mutationFn: logout,
    onSettled: () => queryClient.clear(),
  });
  // Mesma consulta da página Hoje (cache compartilhado): só para o número ao lado de "Revisão".
  const home = useQuery({ queryKey: ['home'], queryFn: ({ signal }) => panel.home(signal) });
  const reviewCount = home.data?.reviewQueueCount ?? 0;

  return (
    <div className="page">
      <a className="skip-link" href="#conteudo">
        Pular para o conteúdo
      </a>
      <div className="container">
        <header className="topbar">
          <NavLink to="/" className="brand" aria-label="Curriculotron 2000 — início">
            <Logo />
          </NavLink>
          <nav aria-label="Principal" className="mainnav">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) => (isActive ? 'navlink hl active' : 'navlink')}
              >
                {item.label}
                {item.badge && reviewCount > 0 && (
                  <span className="nav-badge mono">
                    <span className="visually-hidden">: </span>
                    {reviewCount}
                    <span className="visually-hidden"> pendentes</span>
                  </span>
                )}
              </NavLink>
            ))}
            <button
              type="button"
              className="navlink navbutton"
              onClick={() => exit.mutate()}
              disabled={exit.isPending}
            >
              Sair
            </button>
          </nav>
          {/* Celular (design mobile): só a marca e o atalho para a fila de revisão; o resto fica no rodapé. */}
          <NavLink
            to="/revisao"
            className="mobile-review only-narrow"
            aria-label={`Fila de revisão, ${reviewCount} ${reviewCount === 1 ? 'pendente' : 'pendentes'}`}
          >
            <svg width="26" height="24" viewBox="0 0 26 24" aria-hidden="true">
              <path d="M2 4 L24 3 L23 21 L3 22 Z" />
              <polyline points="3,5 13,13 23,4" />
            </svg>
            {reviewCount > 0 && (
              <span className="mobile-review-count mono" aria-hidden="true">
                {reviewCount}
              </span>
            )}
          </NavLink>
        </header>

        <main id="conteudo" tabIndex={-1} className="content">
          <Outlet />
        </main>
      </div>

      <footer className="footer">
        <div className="footer-landscape footer-landscape-wide">
          <Landscape />
        </div>
        <div className="footer-landscape footer-landscape-narrow">
          <Landscape compact />
        </div>
        <div className="footer-fade" aria-hidden="true" />
        <div className="container footer-top">
          <div className="footer-about">
            <span className="footer-brand">
              Curriculotron<span className="mono">2000</span>
            </span>
            <p>
              Assistente pessoal de busca de emprego — os bots buscam e organizam, quem decide é
              você.
            </p>
            <ul className="footer-contact">
              <li>
                <a href="https://github.com/diulioano" target="_blank" rel="noreferrer">
                  <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
                    <polyline points="8,6 2,12 8,18" />
                    <polyline points="16,6 22,12 16,18" />
                    <line x1="14" y1="4" x2="10" y2="20" />
                  </svg>
                  github.com/diulioano
                </a>
              </li>
              <li>
                <a href="mailto:diulianovargas@gmail.com">
                  <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M2 5 L22 4 L21 20 L3 20 Z" />
                    <polyline points="3,6 12,13 21,5" />
                  </svg>
                  diulianovargas@gmail.com
                </a>
              </li>
            </ul>
          </div>
          <nav aria-label="Rodapé" className="footer-columns">
            {FOOTER.map((column) => (
              <div key={column.title} className="footer-column">
                <h2 className="mono">{column.title}</h2>
                <svg width="34" height="6" viewBox="0 0 34 6" aria-hidden="true">
                  <path d="M1 4 L33 2" />
                </svg>
                <ul>
                  {column.links.map((link) => (
                    <li key={link.label}>
                      <Link to={link.to}>{link.label}</Link>
                    </li>
                  ))}
                  {column.title === 'Sistema' && (
                    <li>
                      <button
                        type="button"
                        className="footer-exit"
                        onClick={() => exit.mutate()}
                        disabled={exit.isPending}
                      >
                        Sair
                      </button>
                    </li>
                  )}
                </ul>
              </div>
            ))}
          </nav>
        </div>
        <div className="container footer-inner">
          <span>© 2026 Diuliano Vargas · uso pessoal</span>
          <span>rodando localmente · seus dados ficam no seu computador</span>
        </div>
      </footer>
    </div>
  );
}
