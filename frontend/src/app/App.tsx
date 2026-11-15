import {
  QueryClientProvider,
  useQuery,
  useQueryClient,
  type QueryClient,
} from '@tanstack/react-query';
import { useEffect } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router';
import { fetchSession, onUnauthorized } from '../api/client';
import { RoughFilter } from '../components/Logo';
import { Home } from '../pages/Home';
import { Opportunities } from '../pages/Opportunities';
import { OpportunityDetail } from '../pages/OpportunityDetail';
import { DedupeReview } from '../pages/DedupeReview';
import { Applications } from '../pages/Applications';
import { ApplicationDetail } from '../pages/ApplicationDetail';
import { ReviewQueue } from '../pages/ReviewQueue';
import { Integrations } from '../pages/Integrations';
import { Activities } from '../pages/Activities';
import { Profile, Criteria, Companies, DataPrivacy } from '../pages/Profile';
import './panel.css';
import { Login } from '../pages/Login';
import { SalaryReferences } from '../pages/SalaryReferences';
import { NotFound, Placeholder } from '../pages/Placeholder';
import { Layout } from './Layout';
import { createQueryClient, SESSION_KEY } from './queryClient';

/** Mostra a tela de entrada até haver sessão; 401 em qualquer chamada volta para ela. */
function SessionGate() {
  const queryClient = useQueryClient();
  const session = useQuery({ queryKey: SESSION_KEY, queryFn: fetchSession, staleTime: 60_000 });

  useEffect(
    () =>
      onUnauthorized(() => {
        void queryClient.invalidateQueries({ queryKey: SESSION_KEY });
      }),
    [queryClient],
  );

  if (session.isPending) {
    return (
      <p className="mono muted" style={{ padding: 32 }} role="status">
        Carregando…
      </p>
    );
  }
  if (session.isError) {
    return (
      <main style={{ padding: '48px 16px', maxWidth: 560, margin: '0 auto' }}>
        <div className="alert dsh" role="alert">
          O serviço local do Curriculotron não respondeu. Verifique se ele está rodando neste
          computador e se você está na Wi-Fi de casa.
        </div>
        <button
          className="btn bt bt-sec dsh"
          type="button"
          onClick={() => void session.refetch()}
          style={{ marginTop: 16 }}
        >
          Tentar de novo
        </button>
      </main>
    );
  }
  if (!session.data.authenticated) {
    return <Login setupRequired={session.data.setupRequired} />;
  }

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="vagas" element={<Opportunities />} />
        <Route path="referencias-salariais" element={<SalaryReferences />} />
        <Route path="vagas/:id" element={<OpportunityDetail />} />
        <Route path="candidaturas" element={<Applications />} />
        <Route path="candidaturas/:id" element={<ApplicationDetail />} />
        <Route path="revisao" element={<ReviewQueue />} />
        <Route path="revisao/duplicatas" element={<DedupeReview />} />
        <Route path="bots" element={<Placeholder title="Bots" />} />
        <Route path="atividades" element={<Activities />} />
        <Route path="perfil" element={<Profile />} />
        <Route path="criterios" element={<Criteria />} />
        <Route path="empresas" element={<Companies />} />
        <Route path="dados" element={<DataPrivacy />} />
        <Route path="integracoes" element={<Integrations />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}

export function App({ queryClient = createQueryClient() }: { queryClient?: QueryClient }) {
  return (
    <QueryClientProvider client={queryClient}>
      <RoughFilter />
      <BrowserRouter>
        <SessionGate />
      </BrowserRouter>
    </QueryClientProvider>
  );
}
