import { DatabaseSync } from 'node:sqlite';
import { expect, type Page } from '@playwright/test';
import { e2eDb, type SeedJob } from './seed.ts';

// US006: vagas fixas avaliadas pelo backend real (compatibility: null → reavaliação pendente).

const FETCHED_AT = '2026-11-15T09:00:00Z';
const src = (slug: string) => ({
  source: 'manual',
  url: `https://vagas.example.test/us006/${slug}`,
  fetchedAt: FETCHED_AT,
});

const FULL_STACK_DESCRIPTION = [
  'Desenvolvimento de funcionalidades de ponta a ponta em APIs e telas web.',
  'Requisitos: Java, Spring Boot e Angular',
  'Diferenciais: Kafka',
  'A equipe usa Docker no dia a dia.',
].join('\n');

const BACKEND_DESCRIPTION = [
  'Desenvolvimento de APIs REST e microsserviços.',
  'Requisitos: Java e Spring Boot',
  'A equipe de produto usa Angular no frontend.',
].join('\n');

const FRONTEND_DESCRIPTION = [
  'Desenvolvimento de interfaces web acessíveis.',
  'Requisitos: React, TypeScript e CSS',
].join('\n');

const base = (
  id: string,
  title: string,
  company: string,
  descriptionText: string,
  extra: Partial<SeedJob> = {},
): SeedJob => ({
  id,
  title,
  company,
  location: 'Remoto',
  workMode: 'remote',
  contractType: 'clt',
  salary: { min: 9000, max: 11000, unit: 'monthly' },
  facts: descriptionText.split('\n'),
  descriptionText,
  fit: 'weak',
  compatibility: null,
  source: src(id),
  ...extra,
});

// ids em ordem alfabética "outra" antes de "favorita": o desempate por id favoreceria a outra,
// então a favorita só vem antes se a prioridade da empresa desempatar.
export const JOBS = {
  fsOther: base(
    'fs-a-outra',
    'Desenvolvedor Full Stack Java Pleno',
    'Empresa Exemplo Gama',
    FULL_STACK_DESCRIPTION,
  ),
  fsFav: base(
    'fs-b-favorita',
    'Desenvolvedor Full Stack Java Pleno II',
    'PicPay',
    FULL_STACK_DESCRIPTION,
  ),
  beOther: base(
    'be-a-outra',
    'Desenvolvedor Backend Java Pleno',
    'Empresa Exemplo Delta',
    BACKEND_DESCRIPTION,
  ),
  beFav: base('be-b-favorita', 'Desenvolvedor Backend Java Pleno II', 'CI&T', BACKEND_DESCRIPTION),
  feOther: base(
    'fe-a-outra',
    'Desenvolvedor Frontend React Pleno',
    'Empresa Exemplo Épsilon',
    FRONTEND_DESCRIPTION,
  ),
  feFav: base('fe-b-favorita', 'Desenvolvedor Frontend React Pleno II', 'BV', FRONTEND_DESCRIPTION),
  levelUnknown: base(
    'nivel-ausente',
    'Desenvolvedor Full Stack Java',
    'DB',
    FULL_STACK_DESCRIPTION,
  ),
  // Exceção Júnior (piso CLT do perfil: 7000).
  jrBoth: base(
    'jr-ambas',
    'Desenvolvedor Full Stack Java Júnior Plataforma',
    'SAP',
    FULL_STACK_DESCRIPTION,
    {
      salary: { min: 8000, max: 9000, unit: 'monthly' },
    },
  ),
  jrSalaryOnly: base(
    'jr-so-salario',
    'Desenvolvedor Full Stack Java Júnior Pagamentos',
    'PicPay',
    FULL_STACK_DESCRIPTION,
    {
      salary: { min: 8000, max: 9000, unit: 'monthly' },
    },
  ),
  jrCompanyOnly: base(
    'jr-so-empresa',
    'Desenvolvedor Full Stack Java Júnior Faturamento',
    'SAP',
    FULL_STACK_DESCRIPTION,
    {
      salary: { min: 5000, max: 6000, unit: 'monthly' },
    },
  ),
  jrCrossing: base(
    'jr-faixa-cruza',
    'Desenvolvedor Full Stack Java Júnior Integrações',
    'SAP',
    FULL_STACK_DESCRIPTION,
    {
      salary: { min: 6000, max: 8000, unit: 'monthly' },
    },
  ),
  jrMissing: base(
    'jr-sem-dados',
    'Desenvolvedor Full Stack Java Júnior Cadastro',
    'SAP',
    FULL_STACK_DESCRIPTION,
    {
      salary: null,
      contractType: 'unknown',
      workMode: 'unknown',
      location: null,
    },
  ),
  // Só estimativa (importada abaixo) acima do piso: não confirma salário.
  jrEstimate: base('jr-estimativa', 'Desenvolvedor Java Júnior', 'SAP', FULL_STACK_DESCRIPTION, {
    salary: null,
  }),
} satisfies Record<string, SeedJob>;

export const ALL_JOBS: SeedJob[] = Object.values(JOBS);

export const CRITERIA_SEED = {
  version: 1,
  targetRoles: ['Desenvolvedor Backend Pleno Java'],
  stackKeywords: ['Java'],
};

export interface ProfileInput {
  role: string;
  primaryTechnology: string;
  secondaryTechnologies: string[];
  flexibleTechnologies: string[];
  currentSeniority: 'junior' | 'pleno' | 'senior';
  targetLevels: ('junior' | 'pleno' | 'senior')[];
  priorities: string[];
  juniorFloorCltMonthlyBrl: number | null;
  juniorFloorPjMonthlyBrl: number | null;
}

/** Perfil equivalente ao currículo anônimo depois da revisão (pisos preenchidos à mão). */
export const EXPECTED_PROFILE: ProfileInput = {
  role: 'Desenvolvedor Full Stack',
  primaryTechnology: 'Java',
  secondaryTechnologies: ['Spring Boot', 'React', 'TypeScript'],
  flexibleTechnologies: ['Kotlin'],
  currentSeniority: 'pleno',
  targetLevels: ['pleno', 'senior'],
  priorities: ['PicPay', 'CI&T', 'BV', 'SAP'],
  juniorFloorCltMonthlyBrl: 7000,
  juniorFloorPjMonthlyBrl: 10000,
};

/** Remove o perfil de carreira do banco descartável (seedOpportunities não conhece a tabela). */
export function resetCareerProfile() {
  if (!e2eDb) throw new Error('CURRICULOTRON_E2E_DB ausente');
  const db = new DatabaseSync(e2eDb, { timeout: 10_000 });
  try {
    const exists = db
      .prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name='career_profile'")
      .get();
    if (exists) db.exec('DELETE FROM career_profile');
  } finally {
    db.close();
  }
}

export function readCareerProfile(): Record<string, unknown> | undefined {
  const db = new DatabaseSync(e2eDb!, { readOnly: true });
  try {
    return db.prepare('SELECT * FROM career_profile WHERE id = 1').get() as
      Record<string, unknown> | undefined;
  } finally {
    db.close();
  }
}

export interface Assessed {
  fit: string;
  fitScore: number;
  roleStatus: string | null;
  technologyStatus: string | null;
}

export function readAssessments(): Record<string, Assessed> {
  const db = new DatabaseSync(e2eDb!, { readOnly: true });
  try {
    const rows = db
      .prepare(
        `SELECT o.id, o.fit, o.fit_score, c.role_status, c.technology_status
         FROM opportunity o LEFT JOIN opportunity_compatibility c ON c.opportunity_id = o.id
         ORDER BY o.id`,
      )
      .all() as {
      id: string;
      fit: string;
      fit_score: number;
      role_status: string | null;
      technology_status: string | null;
    }[];
    return Object.fromEntries(
      rows.map((r) => [
        r.id,
        {
          fit: r.fit,
          fitScore: r.fit_score,
          roleStatus: r.role_status,
          technologyStatus: r.technology_status,
        },
      ]),
    );
  } finally {
    db.close();
  }
}

/** Importa a estimativa salarial (fonte simulada) que casa com JOBS.jrEstimate. */
export async function importJuniorEstimate(page: Page) {
  const session = await (await page.request.get('/api/session')).json();
  const response = await page.request.post('/api/salary-estimates/import', {
    data: [
      {
        id: null,
        criteria: {
          contractType: 'clt',
          company: 'SAP',
          seniority: 'junior',
          role: 'desenvolvedor java',
          location: 'remote',
          workMode: 'remote',
        },
        originalMin: 9000,
        originalMax: 11000,
        originalUnit: 'monthly',
        currency: 'BRL',
        sampleSize: 10,
        source: 'Glassdoor',
        sourceUrl:
          'https://www.glassdoor.com.br/Sal%C3%A1rio/SAP-Desenvolvedor-Java-Junior-Sal%C3%A1rios-E10471_D_KO4,30.htm',
        consultedAt: '2026-11-15T09:00:00Z',
        sourceUpdatedOn: '2026-11-01',
        evidence: 'TEST FIXTURE US006: estimativa acima do piso, não é salário anunciado.',
      },
    ],
    headers: {
      'X-CSRF-Token': session.csrfToken,
      'Idempotency-Key': `us006-estimate-${Date.now()}`,
    },
  });
  expect(response.status()).toBe(201);
}

/** Espera a reavaliação da versão atual dos critérios terminar (dispara pela tela se preciso). */
export async function waitForAssessmentReady(page: Page) {
  await expect
    .poll(
      async () => {
        const criteria = await (await page.request.get('/api/criteria')).json();
        return criteria.assessment?.state;
      },
      { timeout: 60_000, message: 'reavaliação da compatibilidade deve ficar ready' },
    )
    .toBe('ready');
}

export async function listIds(page: Page, list: 'recommended' | 'secondary'): Promise<string[]> {
  const body = await (await page.request.get(`/api/opportunities?list=${list}`)).json();
  return (body.items as { id: string }[]).map((i) => i.id);
}

export async function recommendationSnapshot(page: Page) {
  await page.goto('/vagas?list=recommended');
  await expect(page.getByRole('heading', { name: /Vagas/i })).toBeVisible();
  return {
    assessments: readAssessments(),
    recommended: await listIds(page, 'recommended'),
    secondary: await listIds(page, 'secondary'),
  };
}
