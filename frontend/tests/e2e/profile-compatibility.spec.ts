import { expect, test } from '@playwright/test';
import {
  e2eDb,
  expectNoHorizontalOverflow,
  seedOpportunities,
  signIn,
  type SeedJob,
} from './support/seed.ts';

const FETCHED_AT = '2026-11-15T09:00:00Z';

test.skip(!e2eDb, 'Requer o app isolado com dados controlados (sem CURRICULOTRON_URL).');

test('US1: filtra seleção e secundária por perfil, rotula pendências e separa consumidas de visíveis', async ({
  page,
}) => {
  const jobs: SeedJob[] = [];
  for (let i = 1; i <= 5; i++) {
    jobs.push({
      id: `compat-visible-${i}`,
      title: `Desenvolvedor Backend Pleno ${i}`,
      company: i % 2 === 0 ? 'CI&T' : 'PicPay',
      location: 'Remoto',
      workMode: 'remote',
      contractType: 'clt',
      fit: 'strong',
      fitReasons: ['Cargo e stack Java compatíveis com o perfil.'],
      selectedRank: i,
      source: {
        source: 'lever:ciandt',
        url: `https://vagas.example.test/ciandt/compat-${i}`,
        fetchedAt: FETCHED_AT,
      },
    });
  }
  for (let i = 6; i <= 10; i++) {
    jobs.push({
      id: `compat-consumed-discarded-${i}`,
      title: `Vaga Selecionada Descartada ${i}`,
      company: 'BV',
      fit: 'strong',
      decision: 'discarded',
      selectedRank: i,
    });
  }
  jobs.push(
    {
      id: 'sec-partial-tech-unknown',
      title: 'Desenvolvedor Java Resumo Parcial',
      company: 'DB',
      fit: 'medium',
      fitReasons: ['Compatibilidade tecnológica não confirmada.'],
      descriptionCompleteness: 'partial',
      compatibility: {
        roleStatus: 'matched',
        technologyStatus: 'unknown',
        reasonCodes: ['role_matched', 'technology_unknown_partial'],
      },
      source: {
        source: 'gupy:db',
        url: 'https://vagas.example.test/db/partial-1',
        fetchedAt: FETCHED_AT,
      },
    },
    {
      id: 'sec-role-unknown',
      title: 'Especialista de Plataforma Java',
      company: 'Itaú',
      fit: 'medium',
      fitReasons: ['Função compatível ainda não confirmada.'],
      descriptionCompleteness: 'partial',
      compatibility: {
        roleStatus: 'unknown',
        technologyStatus: 'matched',
        reasonCodes: ['role_unknown', 'technology_matched'],
      },
    },
    {
      id: 'mismatch-role-cwi',
      title: 'Service Designer Sênior',
      company: 'CWI',
      fit: 'strong',
      compatibility: {
        roleStatus: 'mismatched',
        technologyStatus: 'matched',
        reasonCodes: ['role_mismatched'],
      },
    },
    {
      id: 'mismatch-tech-salesforce',
      title: 'Desenvolvedor Backend Salesforce',
      company: 'CI&T',
      fit: 'medium',
      compatibility: {
        roleStatus: 'matched',
        technologyStatus: 'mismatched',
        reasonCodes: ['technology_mismatched'],
      },
    },
    {
      id: 'legacy-discarded',
      title: 'Vaga Legada Sem Avaliação',
      company: 'SAP',
      fit: 'medium',
      fitReasons: ['Java no perfil'],
      decision: 'discarded',
      compatibility: null,
    },
  );

  seedOpportunities(jobs);
  await signIn(page);

  const requests: string[] = [];
  page.on('request', (req) => requests.push(req.url()));

  await page.goto('/');
  await expect(page.getByRole('heading', { name: /Vagas do dia/ })).toContainText('5/10');
  await expect(page.getByText(/consumidas 10 de 10/i)).toBeVisible();
  await expect(page.getByText('Desenvolvedor Backend Pleno 1')).toBeVisible();
  await expect(page.getByText('Service Designer Sênior')).toHaveCount(0);
  await expect(page.getByText('Desenvolvedor Backend Salesforce')).toHaveCount(0);
  await expectNoHorizontalOverflow(page);

  await page.goto('/vagas?list=selection');
  await expect(page.locator('.job-card')).toHaveCount(5);
  await expect(page.getByText('Compatível com o perfil').first()).toBeVisible();
  await expect(page.getByText('Desenvolvedor Java Resumo Parcial')).toHaveCount(0);
  await expectNoHorizontalOverflow(page);

  await page.goto('/vagas?list=secondary');
  await expect(page.locator('.job-card')).toHaveCount(2);
  await expect(page.getByText('Pendência: tecnologia não confirmada')).toBeVisible();
  await expect(page.getByText('Pendência: função não confirmada')).toBeVisible();
  await expect(page.getByText('Service Designer Sênior')).toHaveCount(0);
  await expect(page.getByText('Desenvolvedor Backend Salesforce')).toHaveCount(0);
  await expectNoHorizontalOverflow(page);

  await page.goto('/vagas?list=discarded');
  const legacyCard = page
    .locator('.job-card')
    .filter({ has: page.getByRole('link', { name: 'Vaga Legada Sem Avaliação' }) });
  await expect(legacyCard.getByText('Compatibilidade: ainda não avaliada')).toBeVisible();
  await expect(legacyCard.getByText('Java no perfil')).toHaveCount(0);
  await expectNoHorizontalOverflow(page);

  const origin = new URL(page.url()).origin;
  expect(requests.every((url) => new URL(url).origin === origin)).toBe(true);
});

test('US1: aviso de critério desatualizado oculta vagas antigas mantendo consumo e parciais atuais', async ({
  page,
}) => {
  seedOpportunities(
    [
      {
        id: 'stale-v1-selected',
        title: 'Desenvolvedor Java Antigo',
        company: 'CI&T',
        fit: 'strong',
        fitReasons: ['Java e Spring Boot'],
        selectedRank: 1,
        compatibility: {
          criteriaVersion: 1,
          roleStatus: 'matched',
          technologyStatus: 'matched',
          reasonCodes: ['role_matched', 'technology_matched'],
        },
      },
      {
        id: 'stale-v1-discarded',
        title: 'Vaga Java Descartada v1',
        company: 'PicPay',
        fit: 'strong',
        fitReasons: ['Java legado'],
        decision: 'discarded',
        compatibility: {
          criteriaVersion: 1,
          roleStatus: 'matched',
          technologyStatus: 'matched',
          reasonCodes: ['role_matched', 'technology_matched'],
        },
      },
      {
        id: 'current-v2-python',
        title: 'Desenvolvedor Backend Python',
        company: 'DB',
        fit: 'strong',
        fitReasons: ['Python citado na vaga e no perfil.'],
        compatibility: {
          criteriaVersion: 2,
          roleStatus: 'matched',
          technologyStatus: 'matched',
          reasonCodes: ['role_matched', 'technology_matched'],
        },
      },
    ],
    {
      criteria: {
        version: 2,
        targetRoles: ['Desenvolvedor Backend Python'],
        stackKeywords: ['Python', 'Django'],
      },
      refresh: { state: 'pending' },
    },
  );

  await signIn(page);

  await page.goto('/');
  await expect(page.getByText(/consumidas 1 de 10/i)).toBeVisible();
  await expect(page.getByText(/critério v2 \(2 vaga\(s\) pendente\(s\)\)/i)).toBeVisible();
  await expect(page.getByText('Desenvolvedor Java Antigo')).toHaveCount(0);
  await expectNoHorizontalOverflow(page);

  await page.goto('/vagas?list=secondary');
  await expect(page.getByText(/critério v2 \(2 vaga\(s\) pendente\(s\)\)/i)).toBeVisible();
  await expect(page.getByRole('link', { name: 'Desenvolvedor Backend Python' })).toBeVisible();
  await expect(page.getByText('Desenvolvedor Java Antigo')).toHaveCount(0);
  await expectNoHorizontalOverflow(page);

  await page.goto('/vagas?list=discarded');
  const staleDiscardedCard = page
    .locator('.job-card')
    .filter({ has: page.getByRole('link', { name: 'Vaga Java Descartada v1' }) });
  await expect(staleDiscardedCard.getByText('Avaliação desatualizada (critério v1)')).toBeVisible();
  await expect(staleDiscardedCard.getByText('Java legado')).toHaveCount(0);
  await expectNoHorizontalOverflow(page);

  await page.goto('/vagas?list=secondary');
  await page.getByRole('button', { name: '+ Adicionar vaga' }).click();
  await page
    .getByLabel('Descrição da vaga')
    .fill('Engenheiro Backend Python Manual\nRequisitos: Python e Django');
  await page.getByRole('button', { name: 'Adicionar à lista' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Vaga adicionada' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Engenheiro Backend Python Manual' })).toBeVisible();

  await page.getByLabel('Descrição da vaga').fill('Service Designer Manual\nRequisitos: jornada');
  await page.getByRole('button', { name: 'Adicionar à lista' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Vaga adicionada' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Service Designer Manual' })).toHaveCount(0);
});
