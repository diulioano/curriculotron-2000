import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { createQueryClient } from '../../src/app/queryClient';
import { App } from '../../src/app/App';
import { CompatibilityEvidence } from '../../src/components/CompatibilityEvidence';
import type { OpportunityDetail } from '../../src/api/panel';

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function makeDetail(overrides: Partial<OpportunityDetail> = {}): OpportunityDetail {
  return {
    id: 'job-1',
    title: 'Desenvolvedor Backend Pleno Java',
    companyName: 'CI&T',
    location: 'Remoto',
    workMode: 'remote',
    contractType: 'clt',
    salary: null,
    fitReasons: ['Java e Spring Boot compatíveis com o perfil.'],
    userDecision: 'none',
    availability: 'open',
    compatibility: {
      criteriaVersion: 2,
      evaluatorVersion: 1,
      assessedAt: '2026-11-15T12:00:00Z',
      current: true,
      roleStatus: 'matched',
      technologyStatus: 'matched',
      reasonCodes: ['role_match', 'technology_match'],
    },
    compatibilityCriteria: {
      targetRoles: ['Desenvolvedor Backend Pleno Java'],
      stackKeywords: ['Java 17', 'Spring Boot', 'Angular'],
    },
    compatibilityEvidence: [
      {
        source: 'lever:ciandt',
        sourceUrl: 'https://jobs.lever.co/ciandt/1',
        fetchedAt: '2026-11-15T12:00:00Z',
        field: 'description',
        section: 'requisitos',
        quote: 'Experiência sólida em Java 21 e Spring Boot.',
        matchedCriterion: 'Java 17',
        observedTerm: 'Java 21',
        mentionKind: 'required',
        observedVersion: '21',
        configuredVersion: '17',
      },
      {
        source: 'lever:ciandt',
        sourceUrl: 'https://jobs.lever.co/ciandt/1',
        fetchedAt: '2026-11-15T12:00:00Z',
        field: 'description',
        section: 'diferenciais',
        quote: 'Angular é desejável para apoio eventual.',
        matchedCriterion: 'Angular',
        observedTerm: 'Angular',
        mentionKind: 'optional',
        observedVersion: null,
        configuredVersion: null,
      },
    ],
    facts: ['Java 21'],
    inferred: [],
    missing: ['Salário a confirmar'],
    sources: [
      {
        source: 'lever:ciandt',
        url: 'https://jobs.lever.co/ciandt/1',
        fetchedAt: '2026-11-15T12:00:00Z',
      },
    ],
    ...overrides,
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', '/');
});

describe('CompatibilityEvidence', () => {
  it('exibe resultado por eixo, critérios usados, obrigação vs diferencial, versões e fonte/data', () => {
    render(
      <MemoryRouter>
        <CompatibilityEvidence item={makeDetail()} />
      </MemoryRouter>,
    );

    const section = screen.getByRole('region', { name: /Compatibilidade com o perfil/i });
    expect(within(section).getByText(/Compatível com cargo configurado/i)).toBeVisible();
    expect(within(section).getByText(/Tecnologia compatível confirmada/i)).toBeVisible();
    expect(within(section).getByText('Desenvolvedor Backend Pleno Java')).toBeVisible();
    expect(within(section).getByText('Java 17, Spring Boot, Angular')).toBeVisible();

    const evidenceItems = within(section).getAllByRole('listitem');
    expect(within(evidenceItems[0]).getByText(/Requisito obrigatório/i)).toBeVisible();
    expect(
      within(evidenceItems[0]).getByText(/Versão na vaga: 21 · configurada: 17/i),
    ).toBeVisible();
    expect(within(evidenceItems[0]).getByText(/Diferença de versão explícita/i)).toBeVisible();
    expect(
      within(evidenceItems[0]).getByText(/Experiência sólida em Java 21 e Spring Boot\./),
    ).toBeVisible();
    expect(within(evidenceItems[0]).getByRole('link', { name: /lever:ciandt/ })).toHaveAttribute(
      'href',
      'https://jobs.lever.co/ciandt/1',
    );

    expect(
      within(evidenceItems[1]).getByText(/Diferencial \/ desejável \(não obrigatório\)/i),
    ).toBeVisible();
    expect(within(evidenceItems[1]).getByText(/Angular é desejável/i)).toBeVisible();
  });

  it('identifica menções negadas e incidentais e renderiza HTML/URL maliciosos sem execução', () => {
    const xssPayload = '<img src=x onerror="alert(1)"> não utilizamos Java nesta equipe';
    const { container } = render(
      <MemoryRouter>
        <CompatibilityEvidence
          item={makeDetail({
            compatibility: {
              criteriaVersion: 2,
              evaluatorVersion: 1,
              assessedAt: '2026-11-15T12:00:00Z',
              current: true,
              roleStatus: 'matched',
              technologyStatus: 'mismatched',
              reasonCodes: ['role_match', 'technology_mismatch'],
            },
            compatibilityEvidence: [
              {
                source: 'gupy:empresa',
                sourceUrl: 'javascript:alert(1)',
                fetchedAt: '2026-11-15T12:00:00Z',
                field: 'description',
                section: 'company',
                quote: xssPayload,
                matchedCriterion: 'Java',
                observedTerm: 'Java',
                mentionKind: 'negated',
                observedVersion: null,
                configuredVersion: null,
              },
              {
                source: 'gupy:empresa',
                sourceUrl: null,
                fetchedAt: null,
                field: 'description',
                section: 'company',
                quote: 'Outra equipe mantém legado em Java.',
                matchedCriterion: 'Java',
                observedTerm: 'Java',
                mentionKind: 'incidental',
                observedVersion: null,
                configuredVersion: null,
              },
            ],
          })}
        />
      </MemoryRouter>,
    );

    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('a[href^="javascript:"]')).toBeNull();
    expect(screen.getByText(/Negação na vaga \(não conta como aderência\)/i)).toBeVisible();
    expect(screen.getByText(/Menção incidental \/ outra equipe/i)).toBeVisible();
    expect(
      screen.getByText(new RegExp(xssPayload.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))),
    ).toBeVisible();
    expect(screen.getAllByText(/link indisponível/i)).toHaveLength(2);
  });

  it('mostra snapshot desatualizado explícito e descrição insuficiente para vaga parcial/unknown', () => {
    render(
      <MemoryRouter>
        <CompatibilityEvidence
          item={makeDetail({
            fitReasons: [],
            compatibility: {
              criteriaVersion: 1,
              evaluatorVersion: 1,
              assessedAt: '2026-11-14T10:00:00Z',
              current: false,
              roleStatus: 'unknown',
              technologyStatus: 'unknown',
              reasonCodes: ['role_not_confirmed', 'technology_not_confirmed'],
            },
            compatibilityCriteria: {
              targetRoles: ['Engenheiro Java Antigo'],
              stackKeywords: ['Java 8'],
            },
            compatibilityEvidence: [],
            missing: [
              'Descrição completa da vaga a confirmar (conteúdo parcial ou resumido)',
              'Cargo compatível não confirmado na vaga',
              'Tecnologias exigidas não confirmadas na vaga',
            ],
          })}
        />
      </MemoryRouter>,
    );

    const statusNotice = screen.getByRole('status');
    expect(
      within(statusNotice).getByText(
        /Avaliação desatualizada \(critério v1\)\. Exibindo o resultado e o retrato dos critérios salvos nessa avaliação\./i,
      ),
    ).toBeVisible();
    expect(within(statusNotice).getByRole('link', { name: /Ver critérios/i })).toHaveAttribute(
      'href',
      '/criterios',
    );
    expect(screen.getByText(/Engenheiro Java Antigo/)).toBeVisible();
    expect(screen.getByText(/Descrição parcial ou ausente/i)).toBeVisible();
    expect(screen.getByText(/Função compatível não confirmada/i)).toBeVisible();
    expect(screen.getByText(/Tecnologia não confirmada/i)).toBeVisible();
  });

  it('cobre menções usage/uncertain, tecnologia unrestricted e vaga não avaliada', () => {
    render(
      <MemoryRouter>
        <CompatibilityEvidence
          item={makeDetail({
            compatibility: {
              criteriaVersion: null,
              evaluatorVersion: null,
              assessedAt: null,
              current: false,
              roleStatus: 'unknown',
              technologyStatus: 'unrestricted',
              reasonCodes: ['not_assessed'],
            },
            compatibilityCriteria: null,
            compatibilityEvidence: [
              {
                source: 'manual',
                sourceUrl: null,
                fetchedAt: null,
                field: 'title',
                section: null,
                quote: 'Desenvolvedor Java',
                matchedCriterion: 'Java',
                observedTerm: 'Java',
                mentionKind: 'usage',
                observedVersion: null,
                configuredVersion: null,
              },
              {
                source: 'manual',
                sourceUrl: null,
                fetchedAt: null,
                field: 'description',
                section: null,
                quote: 'Contexto indefinido de Java.',
                matchedCriterion: 'Java',
                observedTerm: 'Java',
                mentionKind: 'uncertain',
                observedVersion: null,
                configuredVersion: null,
              },
            ],
            missing: [],
          })}
        />
      </MemoryRouter>,
    );

    expect(
      screen.getByText(/Esta vaga ainda não foi avaliada contra os critérios atuais\./i),
    ).toBeVisible();
    expect(screen.getByText(/Sem restrição tecnológica no perfil/i)).toBeVisible();
    expect(screen.getByText(/Nenhum snapshot de critérios armazenado/i)).toBeVisible();
    expect(screen.getByText(/Uso informado na função/i)).toBeVisible();
    expect(screen.getByText(/Contexto incerto \(exige confirmação\)/i)).toBeVisible();
  });
});

describe('OpportunityDetail com compatibilidade integrada', () => {
  it('exibe compatibilidade na página de detalhe e oculta fitReasons antigos quando avaliação é stale', async () => {
    window.history.replaceState({}, '', '/vagas/job-stale');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (path: string) => {
        if (path === '/api/session') {
          return json(200, {
            authenticated: true,
            setupRequired: false,
            csrfToken: 'csrf',
            authMode: 'local',
          });
        }
        return json(
          200,
          makeDetail({
            id: 'job-stale',
            fitReasons: ['Motivo legado de Java que deve ficar oculto'],
            compatibility: {
              criteriaVersion: 1,
              evaluatorVersion: 1,
              assessedAt: '2026-11-14T10:00:00Z',
              current: false,
              roleStatus: 'matched',
              technologyStatus: 'matched',
              reasonCodes: ['role_match', 'technology_match'],
            },
          }),
        );
      }),
    );

    render(<App queryClient={createQueryClient()} />);

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Desenvolvedor Backend Pleno Java' }),
    ).toBeVisible();
    const compatSection = screen.getByRole('region', { name: /Compatibilidade com o perfil/i });
    expect(compatSection).toBeVisible();
    expect(
      within(within(compatSection).getByRole('status')).getByText(
        /Avaliação desatualizada \(critério v1\)/i,
      ),
    ).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Por que recomendei' })).not.toBeInTheDocument();
    expect(
      screen.queryByText('Motivo legado de Java que deve ficar oculto'),
    ).not.toBeInTheDocument();
  });

  it('exibe alerta de erro quando detalhe tem campo de evidência inválido', async () => {
    window.history.replaceState({}, '', '/vagas/job-bad');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (path: string) => {
        if (path === '/api/session') {
          return json(200, {
            authenticated: true,
            setupRequired: false,
            csrfToken: 'csrf',
            authMode: 'local',
          });
        }
        return json(
          200,
          makeDetail({
            id: 'job-bad',
            compatibilityEvidence: [
              {
                source: 'lever:ciandt',
                sourceUrl: 'https://jobs.lever.co/ciandt/1',
                fetchedAt: '2026-11-15T12:00:00Z',
                field: 'description',
                section: 'requisitos',
                quote: 'Experiência sólida em Java 21.',
                matchedCriterion: 'Java 17',
                observedTerm: 'Java 21',
                mentionKind: 'invalido' as unknown as 'required',
                observedVersion: '21',
                configuredVersion: '17',
              },
            ],
          }),
        );
      }),
    );

    render(<App queryClient={createQueryClient()} />);
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toBeVisible();
  });
});
