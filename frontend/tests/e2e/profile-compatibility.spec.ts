import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, rmSync } from 'node:fs';
import { createServer } from 'node:net';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';
import {
  e2eDb,
  expectNoHorizontalOverflow,
  seedOpportunities,
  signIn,
  simulateInterruptedRefresh,
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

test('US2: detalhe explica obrigatoriedade, diferencial, versões, texto externo seguro e lacunas de anúncio parcial', async ({
  page,
}) => {
  const xssQuote =
    'Outra equipe mantém legado em Java <img src=x onerror="window.__xss=1"><script>window.__xss=2</script>';
  seedOpportunities(
    [
      {
        id: 'us2-full-detail',
        title: 'Desenvolvedor Backend Pleno',
        company: 'PicPay',
        location: 'Remoto',
        workMode: 'remote',
        contractType: 'clt',
        fit: 'strong',
        fitReasons: [
          'Função compatível com Desenvolvedor Backend Pleno.',
          'Diferencial explícito citado: Spring Boot.',
        ],
        selectedRank: 1,
        source: {
          source: 'lever:picpay',
          url: 'https://vagas.example.test/picpay/us2-full',
          fetchedAt: FETCHED_AT,
        },
        compatibility: {
          criteriaVersion: 2,
          roleStatus: 'matched',
          technologyStatus: 'matched',
          reasonCodes: ['role_matched', 'technology_matched'],
          evidenceItems: [
            {
              field: 'title',
              quote: 'Desenvolvedor Backend Pleno',
              matchedCriterion: 'Desenvolvedor Backend Pleno',
              observedTerm: 'Desenvolvedor Backend Pleno',
              mentionKind: 'usage',
            },
            {
              field: 'description',
              section: 'Requisitos',
              quote: 'Experiência sólida com Java 21 LTS.',
              matchedCriterion: 'Java 17',
              observedTerm: 'Java 21',
              mentionKind: 'required',
              observedVersion: '21',
              configuredVersion: '17',
            },
            {
              field: 'description',
              section: 'Diferenciais',
              quote: 'Diferencial: SPRING BOOT em microsserviços.',
              matchedCriterion: 'Spring Boot',
              observedTerm: 'spring boot',
              mentionKind: 'optional',
            },
            {
              field: 'description',
              section: 'Observações',
              quote: 'Não utilizamos Java 8 nem servlets antigos.',
              matchedCriterion: 'Java 17',
              observedTerm: 'Java',
              mentionKind: 'negated',
            },
            {
              source: 'gupy:externa',
              sourceUrl: 'javascript:window.__xss=3',
              field: 'description',
              section: 'Sobre a empresa',
              quote: xssQuote,
              matchedCriterion: 'Java 17',
              observedTerm: 'Java',
              mentionKind: 'incidental',
            },
          ],
        },
      },
      {
        id: 'us2-partial-detail',
        title: 'Desenvolvedor Backend Java Parcial',
        company: 'CWI',
        fit: 'medium',
        fitReasons: ['Função compatível, aguardando descrição completa.'],
        descriptionCompleteness: 'partial',
        descriptionText: 'Resumo inicial de oportunidade para backend.',
        source: {
          source: 'gupy:cwi',
          url: 'https://vagas.example.test/cwi/us2-partial',
          fetchedAt: FETCHED_AT,
        },
        compatibility: {
          criteriaVersion: 2,
          roleStatus: 'matched',
          technologyStatus: 'unknown',
          reasonCodes: ['role_matched', 'technology_not_confirmed'],
          evidenceItems: [
            {
              field: 'title',
              quote: 'Desenvolvedor Backend Java Parcial',
              matchedCriterion: 'Desenvolvedor Backend Pleno',
              observedTerm: 'Desenvolvedor Backend',
              mentionKind: 'usage',
            },
          ],
        },
      },
      {
        id: 'us2-stale-discarded',
        title: 'Desenvolvedor Java Legado v1',
        company: 'BV',
        fit: 'strong',
        fitReasons: ['Critério v1 desatualizado'],
        decision: 'discarded',
        compatibility: {
          criteriaVersion: 1,
          roleStatus: 'matched',
          technologyStatus: 'matched',
          reasonCodes: ['role_matched', 'technology_matched'],
          criteriaRoles: ['Desenvolvedor Java Legado'],
          criteriaStack: ['Java 11'],
        },
      },
    ],
    {
      criteria: {
        version: 2,
        targetRoles: ['Desenvolvedor Backend Pleno'],
        stackKeywords: ['Java 17', 'Spring Boot'],
      },
      refresh: { state: 'pending' },
      studyFacts: ['Estudar Rust e Kotlin para projetos futuros'],
    },
  );

  await signIn(page);

  await page.goto('/vagas?list=selection');
  await page.getByRole('link', { name: 'Desenvolvedor Backend Pleno' }).click();
  await expect(page).toHaveURL(/\/vagas\/us2-full-detail$/);

  const compatRegion = page.getByRole('region', { name: 'Compatibilidade com o perfil' });
  await expect(compatRegion).toBeVisible();
  await expect(compatRegion.getByText('Compatível com o perfil')).toBeVisible();
  await expect(compatRegion.getByText('Compatível com cargo configurado')).toBeVisible();
  await expect(compatRegion.getByText('Tecnologia compatível confirmada')).toBeVisible();
  await expect(compatRegion.getByText('Desenvolvedor Backend Pleno').first()).toBeVisible();
  await expect(compatRegion.getByText('Java 17, Spring Boot')).toBeVisible();

  const javaRequiredItem = compatRegion
    .locator('.compat-evidence-item')
    .filter({ hasText: 'Requisito obrigatório' });
  await expect(javaRequiredItem.getByText('Critério: Java 17')).toBeVisible();
  await expect(javaRequiredItem.getByText('Observado na vaga: Java 21')).toBeVisible();
  await expect(
    javaRequiredItem.getByText(
      'Versão na vaga: 21 · configurada: 17 — Diferença de versão explícita (sem presumir experiência)',
    ),
  ).toBeVisible();
  await expect(javaRequiredItem.getByText('“Experiência sólida com Java 21 LTS.”')).toBeVisible();

  const springOptionalItem = compatRegion
    .locator('.compat-evidence-item')
    .filter({ hasText: 'Diferencial / desejável (não obrigatório)' });
  await expect(springOptionalItem.getByText('Seção: Diferenciais')).toBeVisible();
  await expect(springOptionalItem.getByText('Critério: Spring Boot')).toBeVisible();
  await expect(springOptionalItem.getByText('Observado na vaga: spring boot')).toBeVisible();
  await expect(
    springOptionalItem.getByText('“Diferencial: SPRING BOOT em microsserviços.”'),
  ).toBeVisible();

  await expect(compatRegion.getByText('Negação na vaga (não conta como aderência)')).toBeVisible();
  await expect(
    compatRegion.getByText('Menção incidental / outra equipe (não conta como aderência)'),
  ).toBeVisible();
  await expect(compatRegion.getByText(`“${xssQuote}”`)).toBeVisible();
  await expect(compatRegion.locator('.compat-quote script, .compat-quote img')).toHaveCount(0);
  const picpayLinks = compatRegion.getByRole('link', { name: /fonte: lever:picpay/ });
  await expect(picpayLinks).toHaveCount(4);
  await expect(picpayLinks.first()).toHaveAttribute(
    'href',
    'https://vagas.example.test/picpay/us2-full',
  );
  await expect(compatRegion.getByText('fonte: gupy:externa · link indisponível')).toBeVisible();
  await expect(compatRegion.locator('a[href^="javascript:"]')).toHaveCount(0);
  expect(await page.evaluate(() => (window as { __xss?: unknown }).__xss)).toBeUndefined();

  const detailBodyText = await page.locator('main').innerText();
  expect(detailBodyText).not.toContain('Estudar Rust');
  expect(detailBodyText).not.toContain('study_recommended');
  await expectNoHorizontalOverflow(page);

  await page.goto('/vagas?list=secondary');
  await page.getByRole('link', { name: 'Desenvolvedor Backend Java Parcial' }).click();
  await expect(page).toHaveURL(/\/vagas\/us2-partial-detail$/);

  const partialRegion = page.getByRole('region', { name: 'Compatibilidade com o perfil' });
  await expect(partialRegion.getByText('Pendência: tecnologia não confirmada')).toBeVisible();
  await expect(
    partialRegion.getByText(
      'Tecnologia não confirmada (descrição insuficiente ou contexto incerto)',
    ),
  ).toBeVisible();
  await expect(
    partialRegion.getByText(
      'Descrição parcial ou ausente: lacunas não foram preenchidas por suposição.',
    ),
  ).toBeVisible();
  await expect(partialRegion.getByRole('link', { name: 'A confirmar' })).toHaveAttribute(
    'href',
    '#a-confirmar',
  );
  const missingRegion = page.getByRole('region', { name: 'A confirmar' });
  await expect(
    missingRegion.getByText(
      'Descrição completa da vaga a confirmar (conteúdo parcial ou resumido)',
    ),
  ).toBeVisible();
  await expect(
    missingRegion.getByText('Tecnologias exigidas não confirmadas na vaga'),
  ).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.goto('/vagas?list=discarded');
  await page.getByRole('link', { name: 'Desenvolvedor Java Legado v1' }).click();
  await expect(page).toHaveURL(/\/vagas\/us2-stale-discarded$/);

  const staleRegion = page.getByRole('region', { name: 'Compatibilidade com o perfil' });
  await expect(
    staleRegion.getByRole('status').filter({ hasText: 'Avaliação desatualizada (critério v1)' }),
  ).toBeVisible();
  await expect(page.getByText('Critério v1 desatualizado')).toHaveCount(0);
  await expect(staleRegion.getByText('Desenvolvedor Java Legado')).toBeVisible();
  await expect(staleRegion.getByText('Java 11')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('US2.1–3 e 5 na ingestão real: nomes próximos, caixa/espaços, negação/incidental, diferencial e estudo isolado', async ({
  page,
}) => {
  seedOpportunities([], {
    criteria: {
      version: 1,
      targetRoles: ['Desenvolvedor Backend Pleno'],
      stackKeywords: ['Java 17', 'Spring Boot', 'Angular', 'React'],
    },
    refresh: { state: 'ready' },
    studyFacts: ['Estudar Rust para projetos futuros'],
  });

  await signIn(page);
  await page.goto('/vagas?list=secondary');
  await page.getByRole('button', { name: '+ Adicionar vaga' }).click();

  await page
    .getByLabel('Descrição da vaga')
    .fill('  Desenvolvedor Backend JavaScript  \nRequisitos: JavaScript e TypeScript');
  await page.getByRole('button', { name: 'Adicionar à lista' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Vaga adicionada' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Desenvolvedor Backend JavaScript' })).toBeVisible();

  await page
    .getByLabel('Descrição da vaga')
    .fill(
      '  Desenvolvedor Backend Só Negação  \nRequisitos:\nNão utilizamos Angular\noutra equipe mantém projeto em React',
    );
  await page.getByRole('button', { name: 'Adicionar à lista' }).click();
  await expect(page.getByRole('link', { name: 'Desenvolvedor Backend Só Negação' })).toBeVisible();

  await page
    .getByLabel('Descrição da vaga')
    .fill(
      '  Desenvolvedor Backend Pleno  \nRequisitos:   JAVA 21   em APIs e Rust\nDiferenciais:   spring boot  \nObservações:\nNão utilizamos Angular; outra equipe mantém projeto em React',
    );
  await page.getByRole('button', { name: 'Adicionar à lista' }).click();
  await expect(page.getByRole('link', { name: 'Desenvolvedor Backend Pleno' })).toBeVisible();

  await page.getByRole('link', { name: 'Desenvolvedor Backend JavaScript' }).click();
  const jsCompat = page.getByRole('region', { name: 'Compatibilidade com o perfil' });
  await expect(jsCompat.getByText('Pendência: tecnologia não confirmada')).toBeVisible();
  await expect(jsCompat.getByText('Critério: Java 17')).toHaveCount(0);
  await expectNoHorizontalOverflow(page);

  await page.goto('/vagas?list=secondary');
  await page.getByRole('link', { name: 'Desenvolvedor Backend Só Negação' }).click();
  const negOnlyCompat = page.getByRole('region', { name: 'Compatibilidade com o perfil' });
  await expect(negOnlyCompat.getByText('Pendência: tecnologia não confirmada')).toBeVisible();
  await expect(negOnlyCompat.getByText('Compatível com o perfil')).toHaveCount(0);
  const negItem = negOnlyCompat
    .locator('.compat-evidence-item')
    .filter({ hasText: 'Negação na vaga (não conta como aderência)' });
  await expect(negItem.getByText('Critério: Angular')).toBeVisible();
  await expect(negItem.getByText('Observado na vaga: angular')).toBeVisible();
  await expect(negItem.getByText('“Não utilizamos Angular”')).toBeVisible();
  const incItem = negOnlyCompat
    .locator('.compat-evidence-item')
    .filter({ hasText: 'Menção incidental / outra equipe (não conta como aderência)' });
  await expect(incItem.getByText('Critério: React')).toBeVisible();
  await expect(incItem.getByText('Observado na vaga: react')).toBeVisible();
  await expect(incItem.getByText('“outra equipe mantém projeto em React”')).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.goto('/vagas?list=secondary');
  await page.getByRole('link', { name: 'Desenvolvedor Backend Pleno' }).click();
  const compatRegion = page.getByRole('region', { name: 'Compatibilidade com o perfil' });
  await expect(compatRegion.getByText('Compatível com o perfil')).toBeVisible();
  await expect(compatRegion.getByText('Compatível com cargo configurado')).toBeVisible();
  await expect(compatRegion.getByText('Tecnologia compatível confirmada')).toBeVisible();

  const manualJavaItem = compatRegion
    .locator('.compat-evidence-item')
    .filter({ hasText: 'Requisito obrigatório' });
  await expect(manualJavaItem.getByText('Critério: Java 17')).toBeVisible();
  await expect(manualJavaItem.getByText('Observado na vaga: java 21')).toBeVisible();
  await expect(
    manualJavaItem.getByText(
      'Versão na vaga: 21 · configurada: 17 — Diferença de versão explícita (sem presumir experiência)',
    ),
  ).toBeVisible();
  await expect(manualJavaItem.getByText('“Requisitos:   JAVA 21   em APIs e Rust”')).toBeVisible();

  const manualSpringItem = compatRegion
    .locator('.compat-evidence-item')
    .filter({ hasText: 'Diferencial / desejável (não obrigatório)' });
  await expect(manualSpringItem.getByText('Critério: Spring Boot')).toBeVisible();
  await expect(manualSpringItem.getByText('Observado na vaga: spring boot')).toBeVisible();
  await expect(manualSpringItem.getByText('“Diferenciais:   spring boot  ”')).toBeVisible();

  await expect(compatRegion.getByText('Negação na vaga (não conta como aderência)')).toBeVisible();
  await expect(
    compatRegion.getByText('Menção incidental / outra equipe (não conta como aderência)'),
  ).toBeVisible();
  const fullText = await page.locator('main').innerText();
  expect(fullText).not.toContain('Estudar Rust');
  expect(fullText).not.toContain('Critério: Rust');
  await expectNoHorizontalOverflow(page);
});

test('US3: troca Java para Python, falha e retry, legado/parcial, 10 consumidas com 5 visíveis e histórico preservado', async ({
  page,
}) => {
  const jobs: SeedJob[] = [];
  for (let i = 1; i <= 5; i++) {
    jobs.push({
      id: `us3-sel-py-java-${i}`,
      title: `Desenvolvedor Backend Pleno Python e Java ${i}`,
      company: i % 2 === 0 ? 'CI&T' : 'PicPay',
      workMode: 'remote',
      fit: 'strong',
      fitReasons: ['Java 17 compatível no critério v1.'],
      descriptionText: 'Requisitos: Java 17, Spring Boot, Python 3 e FastAPI.',
      selectedRank: i,
    });
  }
  for (let i = 6; i <= 10; i++) {
    jobs.push({
      id: `us3-sel-java-only-${i}`,
      title: `Desenvolvedor Backend Pleno Java Puro ${i}`,
      company: 'BV',
      workMode: 'remote',
      fit: 'strong',
      fitReasons: ['Java 17 compatível no critério v1.'],
      descriptionText: 'Requisitos completos: Java 17 e Spring Boot em APIs.',
      selectedRank: i,
    });
  }
  jobs.push(
    {
      id: 'us3-legacy-python',
      title: 'Desenvolvedor Backend Pleno Python Legado',
      company: 'DB',
      fit: 'medium',
      descriptionText: 'Requisitos: Python 3 e FastAPI para microsserviços.',
      compatibility: null,
    },
    {
      id: 'us3-partial-python',
      title: 'Desenvolvedor Backend Pleno Parcial',
      company: 'CWI',
      fit: 'medium',
      descriptionCompleteness: 'partial',
      descriptionText: 'Atuação em arquitetura distribuída de pagamentos.',
      compatibility: {
        criteriaVersion: 1,
        roleStatus: 'matched',
        technologyStatus: 'unknown',
        reasonCodes: ['role_matched', 'technology_unknown_partial'],
      },
    },
    {
      id: 'us3-applied-java',
      title: 'Engenheiro Java Candidatado',
      company: 'CI&T',
      fit: 'strong',
      decision: 'applied',
      descriptionText: 'Requisitos: Java 17 e Spring Boot.',
    },
    {
      id: 'us3-discarded-java',
      title: 'Desenvolvedor Java Descartado',
      company: 'PicPay',
      fit: 'strong',
      decision: 'discarded',
      descriptionText: 'Requisitos: Java 17.',
    },
  );

  seedOpportunities(jobs, {
    criteria: {
      version: 1,
      targetRoles: ['Desenvolvedor Backend Pleno'],
      stackKeywords: ['Java', 'Spring Boot'],
    },
    refresh: { state: 'pending', assessedCount: 13, remainingCount: 1 },
  });

  await signIn(page);
  const requests: string[] = [];
  page.on('request', (req) => requests.push(req.url()));

  await page.goto('/');
  await expect(page.getByRole('heading', { name: /Vagas do dia/ })).toContainText('10/10');
  await expect(page.getByText(/consumidas 10 de 10/i)).toBeVisible();
  await expect(
    page.getByRole('link', { name: 'Desenvolvedor Backend Pleno Python e Java 1' }),
  ).toBeVisible();

  await page.goto('/criterios');
  const stackField = page.getByLabel('Tecnologias (um por linha)');
  await stackField.fill('Python\nFastAPI');
  await page.getByRole('button', { name: 'Salvar critérios' }).click();
  await expect(page.getByText('Critérios salvos.')).toBeVisible();
  await expect(page.getByText('Critério v2', { exact: true })).toBeVisible();
  await expect(
    page.getByRole('status').filter({ hasText: 'Avaliação pendente para o critério v2' }),
  ).toBeVisible();

  await page.goto('/');
  await expect(page.getByText(/critério v2/i)).toBeVisible();
  await expect(page.getByText(/consumidas 10 de 10/i)).toBeVisible();
  await expect(page.getByText('Desenvolvedor Backend Pleno Java Puro 6')).toHaveCount(0);

  await page.goto('/criterios');
  let failOnce = true;
  await page.route('**/api/opportunities/reassess', async (route) => {
    if (failOnce) {
      failOnce = false;
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          error: {
            code: 'reassessment_failed',
            message: 'Falha simulada ao reavaliar vagas.',
          },
        }),
      });
      return;
    }
    await route.fallback();
  });

  await page.getByRole('button', { name: 'Atualizar avaliação' }).click();
  await expect(page.getByRole('alert')).toContainText('Falha simulada ao reavaliar vagas.');
  await expect(page.getByText('Compatibilidade atualizada para o critério v2.')).toHaveCount(0);

  await page.getByRole('button', { name: 'Atualizar avaliação' }).click();
  await expect(
    page.getByRole('status').filter({ hasText: 'Compatibilidade atualizada para o critério v2.' }),
  ).toBeVisible();

  await page.goto('/');
  await expect(page.getByRole('heading', { name: /Vagas do dia/ })).toContainText('5/10');
  await expect(page.getByText(/consumidas 10 de 10/i)).toBeVisible();
  await expect(
    page.getByRole('link', { name: 'Desenvolvedor Backend Pleno Python e Java 1' }),
  ).toBeVisible();
  await expect(page.getByText('Desenvolvedor Backend Pleno Java Puro 6')).toHaveCount(0);
  await expectNoHorizontalOverflow(page);

  await page.goto('/vagas?list=selection');
  await expect(page.locator('.job-card')).toHaveCount(5);
  await expect(page.getByText('Desenvolvedor Backend Pleno Java Puro 6')).toHaveCount(0);
  await expectNoHorizontalOverflow(page);

  await page.goto('/vagas?list=secondary');
  await expect(
    page.getByRole('link', { name: 'Desenvolvedor Backend Pleno Python Legado' }),
  ).toBeVisible();
  const partialCard = page
    .locator('.job-card')
    .filter({ has: page.getByRole('link', { name: 'Desenvolvedor Backend Pleno Parcial' }) });
  await expect(partialCard.getByText('Pendência: tecnologia não confirmada')).toBeVisible();
  await expect(page.getByText('Desenvolvedor Backend Pleno Java Puro 6')).toHaveCount(0);
  await expectNoHorizontalOverflow(page);

  await page.goto('/candidaturas');
  await expect(page.getByRole('link', { name: 'Engenheiro Java Candidatado' })).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.goto('/vagas?list=discarded');
  await expect(page.getByRole('link', { name: 'Desenvolvedor Java Descartado' })).toBeVisible();
  await expectNoHorizontalOverflow(page);

  const origin = new URL(page.url()).origin;
  expect(requests.every((url) => new URL(url).origin === origin)).toBe(true);
});

test('US3: persistência dos critérios, recuperação de interrupção e integridade do histórico após reinício isolado', async ({
  page,
}) => {
  test.setTimeout(90_000);
  const { home, dbPath, url, start, stop } = await prepareIsolatedBackend();
  const requests: string[] = [];
  page.on('request', (req) => requests.push(req.url()));

  try {
    await start();
    await signIn(page, url);

    seedOpportunities(
      [
        {
          id: 'restart-applied-java',
          title: 'Engenheiro Java Candidatado',
          company: 'CI&T',
          fit: 'strong',
          decision: 'applied',
          descriptionText: 'Requisitos: Java 17 e Spring Boot.',
        },
        {
          id: 'restart-discarded-java',
          title: 'Desenvolvedor Java Descartado',
          company: 'PicPay',
          fit: 'strong',
          decision: 'discarded',
          descriptionText: 'Requisitos: Java 17.',
        },
        {
          id: 'restart-python-legacy',
          title: 'Desenvolvedor Backend Pleno Python',
          company: 'DB',
          fit: 'medium',
          descriptionText: 'Requisitos: Python 3 e FastAPI.',
          compatibility: null,
        },
      ],
      {
        dbPath,
        criteria: {
          version: 1,
          targetRoles: ['Desenvolvedor Backend Pleno'],
          stackKeywords: ['Java', 'Spring Boot'],
        },
        refresh: { state: 'pending', assessedCount: 2, remainingCount: 1 },
      },
    );

    await page.goto(`${url}/criterios`);
    await page.getByLabel('Tecnologias (um por linha)').fill('Python\nFastAPI');
    await page.getByRole('button', { name: 'Salvar critérios' }).click();
    await expect(page.getByText('Critérios salvos.')).toBeVisible();
    await page.getByRole('button', { name: 'Atualizar avaliação' }).click();
    await expect(
      page
        .getByRole('status')
        .filter({ hasText: 'Compatibilidade atualizada para o critério v2.' }),
    ).toBeVisible();

    await stop();
    simulateInterruptedRefresh(dbPath, 'restart-python-legacy');
    await start();

    requests.length = 0;
    await signIn(page, url);

    await page.goto(`${url}/criterios`);
    await expect(page.getByLabel('Tecnologias (um por linha)')).toHaveValue('Python\nFastAPI');
    await expect(page.getByText('Critério v2', { exact: true })).toBeVisible();
    await expect(
      page
        .getByRole('status')
        .filter({ hasText: 'A atualização anterior foi interrompida antes de concluir' }),
    ).toBeVisible();
    await expectNoHorizontalOverflow(page);

    await page.goto(`${url}/vagas?list=secondary`);
    await expect(
      page.getByText(/A atualização da compatibilidade falhou \(1 vaga\(s\) pendente\(s\)\)/i),
    ).toBeVisible();
    await expect(
      page.getByRole('link', { name: 'Desenvolvedor Backend Pleno Python' }),
    ).toHaveCount(0);

    await page.goto(`${url}/criterios`);
    await page.getByRole('button', { name: 'Atualizar avaliação' }).click();
    await expect(
      page
        .getByRole('status')
        .filter({ hasText: 'Compatibilidade atualizada para o critério v2.' }),
    ).toBeVisible();

    await page.goto(`${url}/vagas?list=secondary`);
    await expect(
      page.getByRole('link', { name: 'Desenvolvedor Backend Pleno Python' }),
    ).toBeVisible();
    await expectNoHorizontalOverflow(page);

    await page.goto(`${url}/candidaturas`);
    await expect(page.getByRole('link', { name: 'Engenheiro Java Candidatado' })).toBeVisible();
    await expectNoHorizontalOverflow(page);

    await page.goto(`${url}/vagas?list=discarded`);
    await expect(page.getByRole('link', { name: 'Desenvolvedor Java Descartado' })).toBeVisible();
    await expectNoHorizontalOverflow(page);

    const origin = new URL(url).origin;
    expect(requests.every((reqUrl) => new URL(reqUrl).origin === origin)).toBe(true);
  } finally {
    await stop();
    rmSync(home, { recursive: true, force: true });
  }
});

async function prepareIsolatedBackend() {
  const e2eDir = resolve(fileURLToPath(new URL('.', import.meta.url)));
  const backendDir = resolve(e2eDir, '../../../backend');
  const buildDir = resolve(backendDir, 'build');
  const libsDir = join(buildDir, 'libs');
  const jar =
    existsSync(libsDir) &&
    readdirSync(libsDir).find((f) => f.endsWith('.jar') && !f.includes('-plain'));
  if (!jar) {
    throw new Error('Jar do backend não encontrado em backend/build/libs.');
  }
  const home = join(
    buildDir,
    `curriculotron-e2e-restart-${process.pid}-${Date.now()}-${Math.floor(Math.random() * 10000)}`,
  );
  mkdirSync(home, { recursive: true });
  const port = await new Promise<number>((resolvePort, rejectPort) => {
    const srv = createServer();
    srv.unref();
    srv.on('error', rejectPort);
    srv.listen(0, '127.0.0.1', () => {
      const addr = srv.address();
      if (!addr || typeof addr === 'string') {
        srv.close(() => rejectPort(new Error('Falha ao reservar porta efêmera.')));
        return;
      }
      srv.close(() => resolvePort(addr.port));
    });
  });

  const url = `http://127.0.0.1:${port}`;
  const dbPath = join(home, 'data', 'curriculotron.db');
  const javaBin = process.env.JAVA_HOME
    ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    : 'java';

  let proc: ReturnType<typeof spawn> | null = null;

  async function start() {
    if (proc) {
      throw new Error('Instância isolada já está em execução.');
    }
    const child = spawn(
      javaBin,
      [
        '--enable-native-access=ALL-UNNAMED',
        '-jar',
        join(libsDir, jar as string),
        '--curriculotron.scheduler.enabled=false',
        '--curriculotron.resume-file=',
      ],
      {
        cwd: backendDir,
        stdio: 'ignore',
        env: {
          ...process.env,
          CURRICULOTRON_HOME: home,
          CURRICULOTRON_PORT: String(port),
          CURRICULOTRON_BIND: '127.0.0.1',
          CURRICULOTRON_TLS_ENABLED: 'false',
        },
      },
    );
    proc = child;
    const deadline = Date.now() + 45_000;
    while (Date.now() < deadline) {
      if (child.exitCode !== null) {
        throw new Error(`Instância isolada encerrou prematuramente com código ${child.exitCode}.`);
      }
      try {
        const res = await fetch(`${url}/api/health`);
        if (res.ok) return;
      } catch {
        // Aguardando porta subir.
      }
      await new Promise((r) => setTimeout(r, 250));
    }
    throw new Error(`Timeout aguardando ${url}/api/health.`);
  }

  async function stop() {
    const child = proc;
    if (!child) return;
    proc = null;
    if (child.exitCode !== null) return;
    await new Promise<void>((resolveExit) => {
      const timer = setTimeout(() => {
        child.kill('SIGKILL');
      }, 8_000);
      child.once('exit', () => {
        clearTimeout(timer);
        resolveExit();
      });
      child.kill();
    });
  }

  return { home, dbPath, url, start, stop };
}
