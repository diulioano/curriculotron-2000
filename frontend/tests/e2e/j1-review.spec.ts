import { expect, test } from '@playwright/test';
import {
  e2eDb,
  expectNoHorizontalOverflow,
  seedOpportunities,
  signIn,
  type SeedJob,
} from './support/seed.ts';

// T036 — Jornada J1 (US1): revisão da seleção diária no app real, com dados controlados.
// Roda em 360 px e 1280 px (projetos do playwright.config.ts).

const FETCHED_AT = '2026-11-14T10:30:00Z';
const noSalary: SeedJob = {
  id: 'j1-sem-salario',
  title: 'Engenheiro de Software Backend',
  company: 'PicPay',
  location: 'Remoto (Brasil)',
  workMode: 'remote',
  contractType: 'clt',
  salary: null,
  facts: ['Java 21 obrigatório', 'Experiência com microsserviços'],
  inferred: ['Kubernetes pode ser desejável (inferido da descrição)'],
  fitReasons: ['Java e microsserviços citados na vaga e no seu perfil'],
  source: {
    source: 'oracle:picpay',
    url: 'https://vagas.example.test/picpay/42',
    fetchedAt: FETCHED_AT,
  },
  selectedRank: 1,
};
const jobs: SeedJob[] = [
  noSalary,
  {
    id: 'j1-com-salario',
    title: 'Desenvolvedor Java Pleno',
    company: 'CI&T',
    location: 'Porto Alegre',
    workMode: 'hybrid',
    contractType: 'clt',
    salary: { min: 9000, max: 12000, unit: 'monthly' },
    facts: ['Spring Boot'],
    fitReasons: ['Empresa prioritária'],
    source: {
      source: 'lever:ciandt',
      url: 'https://vagas.example.test/ciandt/7',
      fetchedAt: FETCHED_AT,
    },
    selectedRank: 2,
  },
  {
    id: 'j1-terceira',
    title: 'Pessoa Desenvolvedora Kotlin',
    company: 'BV',
    workMode: 'remote',
    contractType: 'pj',
    salary: { min: 12000, max: null, unit: 'monthly' },
    fitReasons: ['Remoto e stack JVM'],
    selectedRank: 3,
  },
  {
    id: 'j1-fraca',
    title: 'Analista de Suporte',
    company: 'CWI',
    fit: 'weak',
    fitReasons: ['Função diferente da buscada'],
  },
  {
    id: 'j1-descartada',
    title: 'Desenvolvedor PHP',
    company: 'DB',
    fit: 'strong',
    decision: 'discarded',
  },
];
const selectionHeading = /Vagas do dia/;

test.skip(!e2eDb, 'Requer o app isolado com dados controlados (sem CURRICULOTRON_URL).');

test.beforeEach(async ({ page }) => {
  seedOpportunities(jobs);
  await signIn(page);
});

test('J1: início mostra a seleção do dia com motivos e sem dados inventados', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: selectionHeading })).toContainText('3/10');
  for (const title of [
    'Engenheiro de Software Backend',
    'Desenvolvedor Java Pleno',
    'Pessoa Desenvolvedora Kotlin',
  ])
    await expect(page.getByRole('link', { name: title })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Analista de Suporte' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Desenvolvedor PHP' })).toHaveCount(0);
  await expect(
    page.getByText('“Java e microsserviços citados na vaga e no seu perfil”'),
  ).toBeVisible();
  // Prazos vêm de candidaturas (US3), ainda sem dados no backend: a seção não inventa itens.
  await expect(page.getByRole('heading', { name: 'Candidaturas · próximas ações' })).toBeVisible();
  await expect(page.getByText('Nenhuma próxima ação agendada.')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('J1: detalhe separa fatos e inferências, mostra salário a confirmar, fonte, link e data', async ({
  page,
}) => {
  const posts: string[] = [];
  page.on('request', (r) => {
    if (r.method() === 'POST') posts.push(new URL(r.url()).pathname);
  });
  await page.goto('/vagas');
  await page.getByRole('link', { name: noSalary.title }).click();
  await expect(page.getByRole('heading', { level: 1, name: noSalary.title })).toBeVisible();
  await expect(page.locator('.salary')).toHaveText('Salário a confirmar');

  const section = (name: string) =>
    page.locator('section', { has: page.getByRole('heading', { name, exact: true }) });
  const facts = section('Fatos da vaga');
  const inferred = section('Inferências');
  for (const fact of noSalary.facts!) await expect(facts).toContainText(fact);
  await expect(facts).not.toContainText('Kubernetes');
  await expect(inferred).toContainText(noSalary.inferred![0]);
  await expect(inferred).not.toContainText('Java 21');
  await expect(section('A confirmar')).toContainText('Salário a confirmar');

  const link = page.getByRole('link', { name: /oracle:picpay/ });
  await expect(link).toHaveAttribute('href', noSalary.source!.url);
  await expect(link).toHaveAttribute('target', '_blank');
  await expect(page.locator(`time[datetime="${FETCHED_AT}"]`)).toBeVisible();
  await expectNoHorizontalOverflow(page);
  // Consultar a vaga não registra candidatura nem muda nada.
  expect(posts).toEqual([]);
});

// T038 — Feature 005: visita externa e correção manual contra o backend real.
test('J1: abrir no site só registra a visita; correção manual persiste e preserva a fonte', async ({
  page,
  context,
}) => {
  // O popup da vaga aponta para um domínio de teste: responde localmente, sem rede externa.
  await context.route('https://vagas.example.test/**', (route) =>
    route.fulfill({ contentType: 'text/html', body: '<!doctype html><title>Vaga externa</title>' }),
  );
  const posts: string[] = [];
  page.on('request', (r) => {
    if (r.method() === 'POST') posts.push(new URL(r.url()).pathname);
  });
  const visitPath = `/api/opportunities/${noSalary.id}/external-visit`;
  const correctionPath = `/api/opportunities/${noSalary.id}/corrections`;

  await page.goto(`/vagas/${noSalary.id}`);
  const timeline = page.getByRole('region', { name: 'Linha do tempo' });
  await expect(timeline).toContainText('Encontrada em oracle:picpay.');
  const visits = timeline.getByRole('listitem').filter({ hasText: 'Abriu o link da vaga.' });
  const previousVisitCount = await visits.count();

  // 1) "Abrir vaga no site ↗" chama o backend real (204) e abre a vaga em outra aba.
  const [popup, visit] = await Promise.all([
    context.waitForEvent('page'),
    page.waitForResponse(
      (r) => new URL(r.url()).pathname === visitPath && r.request().method() === 'POST',
    ),
    page.getByRole('link', { name: 'Abrir vaga no site ↗' }).click(),
  ]);
  expect(visit.status()).toBe(204);
  await popup.close();

  await expect(visits).toHaveCount(previousVisitCount + 1);
  await expect(visits.last()).toContainText('(isso não registra candidatura)');
  await expect(page.getByRole('button', { name: 'Me candidatei' })).toBeEnabled();
  expect(posts).toEqual([visitPath]);
  const applications = await page.request.get('/api/applications');
  expect(((await applications.json()) as { items: unknown[] }).items).toEqual([]);

  // 2) "Corrigir algum dado →" grava a correção no backend real.
  await page.getByRole('button', { name: 'Corrigir algum dado →' }).click();
  const form = page.getByRole('form', { name: 'Corrigir dados da vaga' });
  await form.getByLabel('Local').fill('São Leopoldo, RS');
  await form.getByLabel('Salário mínimo (R$)').fill('9500');
  await form.getByLabel('Salário máximo (R$)').fill('12000');
  await form.getByLabel('Motivo ou origem da correção').fill('Confirmado no edital oficial');
  await expectNoHorizontalOverflow(page);
  const [correction] = await Promise.all([
    page.waitForResponse(
      (r) => new URL(r.url()).pathname === correctionPath && r.request().method() === 'POST',
    ),
    form.getByRole('button', { name: 'Salvar correção' }).click(),
  ]);
  expect(correction.status()).toBe(200);
  await expect(form).toHaveCount(0);

  // 3) Após recarregar, a correção persiste e a fonte original continua intacta.
  await page.reload();
  await expect(page.getByRole('heading', { level: 1, name: noSalary.title })).toBeVisible();
  await expect(page.locator('.salary')).toContainText(/9\.500 – 12\.000/);
  await expect(page.getByText('São Leopoldo, RS').first()).toBeVisible();
  const latest = page.getByTestId('latest-correction');
  await expect(latest).toContainText('corrigido por você');
  await expect(latest).toContainText('Confirmado no edital oficial');
  const evidence = page.locator('section[aria-labelledby="evidencia"]');
  const source = evidence.getByRole('link', { name: /oracle:picpay/ });
  await expect(source).toHaveAttribute('href', noSalary.source!.url);
  await expect(evidence.locator(`time[datetime="${FETCHED_AT}"]`)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Me candidatei' })).toBeEnabled();
  await expectNoHorizontalOverflow(page);

  await page.goto('/vagas');
  const card = page.locator('.job-card').filter({
    has: page.getByRole('link', { name: noSalary.title }),
  });
  const salary = card.locator('.job-salary');
  await expect(salary).toContainText(/9\.500 – 12\.000/);
  await expect(salary).toContainText('corrigido por você');
  await expect(salary).not.toContainText(/✓|declarado/i);
});

test('J1: descartar tira a vaga da seleção; restaurar é explícito', async ({ page }) => {
  await page.goto(`/vagas/${noSalary.id}`);
  await page.getByRole('button', { name: 'Descartar vaga' }).click();
  await expect(page.getByRole('status')).toContainText('Vaga descartada');
  await expect(page.getByRole('button', { name: 'Restaurar' })).toBeVisible();

  await page.goto('/vagas');
  await expect(page.getByRole('link', { name: 'Desenvolvedor Java Pleno' })).toBeVisible();
  await expect(page.getByRole('link', { name: noSalary.title })).toHaveCount(0);
  await page.goto('/');
  await expect(page.getByRole('heading', { name: selectionHeading })).toContainText('2/10');

  await page.goto('/vagas?list=discarded');
  await expect(page.getByRole('link', { name: noSalary.title })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Desenvolvedor PHP' })).toBeVisible();

  await page.goto(`/vagas/${noSalary.id}`);
  await page.getByRole('button', { name: 'Restaurar' }).click();
  await expect(page.getByRole('status')).toContainText('Vaga restaurada');
  await page.goto('/vagas?list=discarded');
  await expect(page.getByRole('link', { name: 'Desenvolvedor PHP' })).toBeVisible();
  await expect(page.getByRole('link', { name: noSalary.title })).toHaveCount(0);
});

test('J1: secundária guarda as demais; entrada manual não vira candidatura', async ({ page }) => {
  await page.goto('/vagas?list=secondary');
  await expect(page.getByRole('link', { name: 'Analista de Suporte' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Desenvolvedor PHP' })).toHaveCount(0);

  await page.getByRole('button', { name: '+ Adicionar vaga' }).click();
  await page
    .getByLabel('Descrição da vaga')
    .fill('Desenvolvedor Java Sênior na SAP\nHíbrido em São Leopoldo');
  await page.getByRole('button', { name: 'Adicionar à lista' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Vaga adicionada' })).toBeVisible();
  const manual = page.getByRole('link', { name: 'Desenvolvedor Java Sênior na SAP' });
  await expect(manual).toBeVisible();
  await manual.click();
  await expect(
    page.getByRole('heading', { level: 1, name: 'Desenvolvedor Java Sênior na SAP' }),
  ).toBeVisible();
  await expect(page.locator('.salary')).toHaveText('Salário a confirmar');
  await expect(page.getByText('Empresa a confirmar').first()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Me candidatei' })).toBeEnabled();
  await expectNoHorizontalOverflow(page);
});
