import { PageHeader } from '../components/Panel';
import { SurfaceStateBlock } from '../components/SurfaceState';

/** Tela em construção (ex.: Bots) padronizada com PageHeader + SurfaceStateBlock. */
export function Placeholder({ title }: { title: string }) {
  return (
    <div className="stack">
      <PageHeader title={title} eyebrow="Coordenador e automações" />
      <SurfaceStateBlock
        kind="empty"
        title="Esta tela ainda está em construção."
        body="A configuração visual do coordenador de bots e fluxos assistidos será conectada aqui. Enquanto isso, você pode acompanhar execuções em Atividades e revisar critérios de busca."
        action={{ label: 'Ver atividades →', to: '/atividades' }}
      />
    </div>
  );
}

export function NotFound() {
  return (
    <div className="stack">
      <PageHeader title="Página não encontrada" eyebrow="Rota desconhecida" />
      <SurfaceStateBlock
        kind="empty"
        title="Este endereço não existe no painel."
        action={{ label: 'Voltar para Hoje →', to: '/' }}
      />
    </div>
  );
}
