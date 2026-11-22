import { fileURLToPath } from 'node:url';
import { expect, test, type Page } from '@playwright/test';
import { e2eDb, expectNoHorizontalOverflow, seedOpportunities, signIn } from './support/seed.ts';
import {
  ALL_JOBS,
  CRITERIA_SEED,
  EXPECTED_PROFILE,
  JOBS,
  importJuniorEstimate,
  readCareerProfile,
  recommendationSnapshot,
  resetCareerProfile,
  waitForAssessmentReady,
} from './support/us006.ts';
import {
  field,
  fillProfileForm,
  importMarkdownFile,
  openProfileEditor,
  saveProfile,
  startManualProfile,
} from './support/us006-ui.ts';

// US006 (specs/006-perfil-descoberta-estudo): perfil por importação revisada e manual, descoberta
// com exceção Júnior e preparação no detalhe, no app real isolado com SQLite descartável.
// As vagas são semeadas sem avaliação; quem avalia é o backend real ao salvar o perfil.

test.skip(!e2eDb, 'Requer o app isolado com dados controlados (sem CURRICULOTRON_URL).');
test.describe.configure({ mode: 'serial' });

const RESUME_PATH = fileURLToPath(
  new URL('./fixtures/us006/curriculo-anonimo.md', import.meta.url),
);

function freshData() {
  resetCareerProfile();
  seedOpportunities(ALL_JOBS, { criteria: CRITERIA_SEED, refresh: { state: 'pending' } });
}

async function evaluate(page: Page) {
  await waitForAssessmentReady(page);
  return recommendationSnapshot(page);
}

type Snapshot = Awaited<ReturnType<typeof evaluate>>;
const STRENGTH: Record<string, number> = { strong: 2, medium: 1, weak: 0 };
function better(s: Snapshot, a: string, b: string) {
  const [x, y] = [s.assessments[a], s.assessments[b]];
  return STRENGTH[x.fit] > STRENGTH[y.fit] || (x.fit === y.fit && x.fitScore > y.fitScore);
}

async function importReviewedProfile(page: Page) {
  await openProfileEditor(page);
  await importMarkdownFile(page, RESUME_PATH);
  // Rascunho editável com o que o Markdown traz; nada gravado antes de confirmar.
  await expect(field(page, 'role')).toHaveValue(EXPECTED_PROFILE.role);
  await expect(field(page, 'primaryTechnology')).toHaveValue(EXPECTED_PROFILE.primaryTechnology);
  await expect(field(page, 'secondaryTechnologies')).toHaveValue(
    EXPECTED_PROFILE.secondaryTechnologies.join('\n'),
  );
  await expect(field(page, 'juniorFloorCltMonthlyBrl')).toHaveValue('');
  expect(readCareerProfile()).toBeUndefined();
  // Revisão: o currículo não traz pisos Júnior; a pessoa completa antes de salvar.
  await fillProfileForm(page, {
    juniorFloorCltMonthlyBrl: EXPECTED_PROFILE.juniorFloorCltMonthlyBrl,
    juniorFloorPjMonthlyBrl: EXPECTED_PROFILE.juniorFloorPjMonthlyBrl,
  });
  await expect(page.getByText('Informado por você').first()).toBeVisible();
  await saveProfile(page);
}

async function createManualProfile(page: Page) {
  await openProfileEditor(page);
  await startManualProfile(page);
  await fillProfileForm(page, EXPECTED_PROFILE);
  await saveProfile(page);
}

async function profileFromApi(page: Page) {
  const body = await (await page.request.get('/api/profile')).json();
  const fields = { ...body.profile } as Record<string, unknown>;
  const origin = fields.origin;
  for (const key of ['version', 'updatedAt', 'origin', 'provenance']) delete fields[key];
  return { origin, fields };
}

test('US006: importado revisado e manual equivalente geram as mesmas recomendações', async ({
  page,
}, testInfo) => {
  freshData();
  await signIn(page);
  await importJuniorEstimate(page);

  await importReviewedProfile(page);
  const imported = await profileFromApi(page);
  expect(imported.origin).toBe('markdown');
  expect(imported.fields).toEqual(EXPECTED_PROFILE);
  expect(readCareerProfile()?.origin).toBe('markdown');
  await page.reload();
  await expect(page.getByRole('region', { name: 'Perfil salvo' })).toContainText(
    'Importado de Markdown e revisado',
  );
  const fromImport = await evaluate(page);
  await page.screenshot({ path: testInfo.outputPath('import-hoje.png'), fullPage: true });

  freshData();
  await createManualProfile(page);
  const manual = await profileFromApi(page);
  expect(manual.origin).toBe('manual');
  expect(manual.fields).toEqual(imported.fields);
  const fromManual = await evaluate(page);
  await page.screenshot({ path: testInfo.outputPath('manual-hoje.png'), fullPage: true });

  await testInfo.attach('snapshots.json', {
    body: JSON.stringify({ fromImport, fromManual }, null, 2),
    contentType: 'application/json',
  });
  expect(fromImport.recommended.length).toBeGreaterThan(0);
  expect(fromManual).toEqual(fromImport);
});

test('US006: Full Stack Java Angular, backend Java e frontend React em favoritas e outras', async ({
  page,
}, testInfo) => {
  freshData();
  await signIn(page);
  await createManualProfile(page);
  const s = await evaluate(page);
  await testInfo.attach('snapshot.json', {
    body: JSON.stringify(s, null, 2),
    contentType: 'application/json',
  });

  // Full Stack Java+Angular: boa aderência mesmo com React (e não Angular) no perfil.
  for (const job of [JOBS.fsFav, JOBS.fsOther]) {
    expect(s.assessments[job.id].fit, job.id).toBe('strong');
    expect(s.recommended, job.id).toContain(job.id);
  }
  // Backend Java faz sentido; frontend com só React em comum fica abaixo dos dois.
  expect(s.assessments[JOBS.beFav.id].fit).not.toBe('weak');
  for (const fe of [JOBS.feFav, JOBS.feOther]) {
    expect(better(s, JOBS.fsFav.id, fe.id), `fullstack acima de ${fe.id}`).toBe(true);
    expect(better(s, JOBS.beFav.id, fe.id), `backend acima de ${fe.id}`).toBe(true);
    expect(s.recommended, fe.id).not.toContain(fe.id);
  }
  // Favorita só desempata: mesma nota, favorita antes (o desempate por id favoreceria a outra).
  expect(s.assessments[JOBS.fsFav.id].fitScore).toBe(s.assessments[JOBS.fsOther.id].fitScore);
  expect(s.recommended.indexOf(JOBS.fsFav.id)).toBeLessThan(s.recommended.indexOf(JOBS.fsOther.id));
  expect(s.assessments[JOBS.beFav.id].fitScore).toBe(s.assessments[JOBS.beOther.id].fitScore);
  const beList = s.recommended.includes(JOBS.beFav.id) ? s.recommended : s.secondary;
  expect(beList).toContain(JOBS.beOther.id);
  expect(beList.indexOf(JOBS.beFav.id)).toBeLessThan(beList.indexOf(JOBS.beOther.id));

  await page.goto('/vagas?list=recommended');
  const fav = page.getByRole('link', { name: JOBS.fsFav.title, exact: true });
  const other = page.getByRole('link', { name: JOBS.fsOther.title, exact: true });
  await expect(fav).toBeVisible();
  await expect(other).toBeVisible();
  const titles = await page.getByRole('main').getByRole('link').allTextContents();
  expect(titles.indexOf(JOBS.fsFav.title)).toBeLessThan(titles.indexOf(JOBS.fsOther.title));
  await expectNoHorizontalOverflow(page);
  await page.screenshot({ path: testInfo.outputPath('hoje-favoritas.png'), fullPage: true });

  // Nível ausente no anúncio: incerteza explícita.
  await page.goto(`/vagas/${JOBS.levelUnknown.id}`);
  const level = page
    .locator('div')
    .filter({ has: page.getByText('NÍVEL', { exact: true }) })
    .last();
  await expect(level).toContainText('a confirmar');
  await expectNoHorizontalOverflow(page);
  await page.screenshot({ path: testInfo.outputPath('nivel-ausente.png'), fullPage: true });
});

test('US006: exceção Júnior exige salário anunciado ≥ piso próprio e empresa justificada', async ({
  page,
}, testInfo) => {
  freshData();
  await signIn(page);
  await importJuniorEstimate(page);
  await createManualProfile(page);
  const s = await evaluate(page);
  await testInfo.attach('snapshot.json', {
    body: JSON.stringify(s, null, 2),
    contentType: 'application/json',
  });

  // Ambas: SAP com justificativa (RB-004) e R$ 8.000 anunciado ≥ piso CLT de R$ 7.000.
  expect(s.assessments[JOBS.jrBoth.id].fit).toBe('strong');
  expect(s.recommended).toContain(JOBS.jrBoth.id);
  // Só salário (PicPay prioritária sem justificativa), só empresa (abaixo do piso), faixa que
  // cruza o piso, dados ausentes e só estimativa: fora das recomendações.
  for (const job of [
    JOBS.jrSalaryOnly,
    JOBS.jrCompanyOnly,
    JOBS.jrCrossing,
    JOBS.jrMissing,
    JOBS.jrEstimate,
  ]) {
    expect(s.assessments[job.id].fit, job.id).toBe('weak');
    expect(s.recommended, job.id).not.toContain(job.id);
  }

  const exception = page.getByRole('region', { name: 'Exceção Júnior' });
  const check = (label: string) => exception.getByRole('listitem').filter({ hasText: label });

  await page.goto(`/vagas/${JOBS.jrBoth.id}`);
  await expect(exception).toContainText('Atende às três condições da exceção.');
  await expect(check('Remuneração confirmada acima do piso')).toContainText(
    /Mínimo anunciado de R\$\s?8\.000,00 atinge o piso Júnior CLT de R\$\s?7\.000,00/,
  );
  await expect(check('Justificativa da empresa')).toContainText(/carga de trabalho menor/);
  await expectNoHorizontalOverflow(page);
  await page.screenshot({ path: testInfo.outputPath('junior-ambas.png'), fullPage: true });

  const cases: [{ id: string }, string, RegExp][] = [
    [JOBS.jrSalaryOnly, 'Justificativa da empresa', /Ser prioritária, sozinha, não qualifica/],
    [JOBS.jrCompanyOnly, 'Remuneração confirmada acima do piso', /abaixo do piso Júnior CLT/],
    [JOBS.jrCrossing, 'Remuneração confirmada acima do piso', /cruza o piso Júnior CLT/],
    [JOBS.jrMissing, 'Remuneração confirmada acima do piso', /Regime da vaga .* não informado/],
    [JOBS.jrEstimate, 'Remuneração confirmada acima do piso', /estimativas não confirmam/],
  ];
  for (const [job, label, reason] of cases) {
    await page.goto(`/vagas/${job.id}`);
    await expect(exception, job.id).toContainText('Não atende a todas as condições');
    await expect(check(label), job.id).toContainText('(não atendida)');
    await expect(check(label), job.id).toContainText(reason);
  }
  // A estimativa aparece como estimativa, separada do salário anunciado.
  await page.goto(`/vagas/${JOBS.jrEstimate.id}`);
  await expect(page.locator('.req-inferred').getByText(/Estimativa:/)).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('junior-estimativa.png'), fullPage: true });
});

test('US006: preparação distingue obrigatória, diferencial e menção e reflete edição do perfil', async ({
  page,
}, testInfo) => {
  freshData();
  await signIn(page);
  await createManualProfile(page);
  await waitForAssessmentReady(page);

  const prep = page.getByRole('region', { name: 'Preparar' });
  const group = (name: string) => prep.getByRole('region', { name, exact: true });
  const item = (name: string, tech: string) =>
    group(name)
      .getByRole('listitem')
      .filter({ has: page.getByText(tech, { exact: true }) });

  await page.goto(`/vagas/${JOBS.fsFav.id}`);
  await expect(prep).toContainText('Comparado com o seu perfil v1.');
  await expect(item('Exigência obrigatória', 'Angular')).toContainText(
    'não consta no perfil: estudar',
  );
  await expect(item('Exigência obrigatória', 'Angular')).toContainText(
    'Requisitos: Java, Spring Boot e Angular',
  );
  await expect(item('Exigência obrigatória', 'Java')).toContainText('sua tecnologia principal');
  await expect(item('Diferencial', 'Kafka')).toContainText('não consta no perfil');
  await expect(item('Diferencial', 'Kafka')).toContainText('Diferenciais: Kafka');
  await expect(item('Mencionada pela equipe', 'Docker')).toContainText('A equipe usa Docker');
  await expect(item('Exigência obrigatória', 'Docker')).toHaveCount(0);
  await expectNoHorizontalOverflow(page);
  await page.screenshot({ path: testInfo.outputPath('preparacao-antes.png'), fullPage: true });

  // Angular apenas usado pela equipe: menção, nunca obrigatório.
  await page.goto(`/vagas/${JOBS.beFav.id}`);
  await expect(item('Mencionada pela equipe', 'Angular')).toContainText('não consta no perfil');
  await expect(item('Exigência obrigatória', 'Angular')).toHaveCount(0);

  // Edita a competência no perfil e reabre o detalhe.
  await openProfileEditor(page);
  await fillProfileForm(page, {
    secondaryTechnologies: [...EXPECTED_PROFILE.secondaryTechnologies, 'Angular'],
  });
  await saveProfile(page);
  const saved = await (await page.request.get('/api/profile')).json();
  expect(saved.profile.secondaryTechnologies).toContain('Angular');
  expect(saved.profile.version).toBe(2);

  await page.goto(`/vagas/${JOBS.fsFav.id}`);
  await page.reload();
  await expect(prep).toContainText('Comparado com o seu perfil v2.');
  await expect(item('Exigência obrigatória', 'Angular')).toContainText('no perfil como secundária');
  await expect(item('Exigência obrigatória', 'Angular')).not.toContainText('não consta no perfil');
  await expect(item('Diferencial', 'Kafka')).toContainText('não consta no perfil');
  const detail = await (await page.request.get(`/api/opportunities/${JOBS.fsFav.id}`)).json();
  expect(detail.preparation.profileVersion).toBe(2);
  expect(
    detail.preparation.required.find((i: { technology: string }) => i.technology === 'Angular')
      ?.profileTier,
  ).toBe('secondary');
  await expectNoHorizontalOverflow(page);
  await page.screenshot({ path: testInfo.outputPath('preparacao-depois.png'), fullPage: true });

  await page.goto(`/vagas/${JOBS.beFav.id}`);
  await expect(item('Mencionada pela equipe', 'Angular')).toContainText(
    'no perfil como secundária',
  );
});

test('US006: uma falha real de importação permite concluir o cadastro manual', async ({ page }) => {
  freshData();
  await signIn(page);
  await openProfileEditor(page);
  const form = page.getByRole('form', { name: 'Importar currículo Markdown' });
  await form.getByLabel('Ou cole o Markdown').fill('x'.repeat(100001));
  await form.getByRole('button', { name: 'Ler e revisar' }).click();
  await expect(form.getByRole('alert')).toContainText('preencher o perfil manualmente');
  expect(readCareerProfile()).toBeUndefined();
  await startManualProfile(page);
  await fillProfileForm(page, EXPECTED_PROFILE);
  await saveProfile(page);
  expect((await profileFromApi(page)).origin).toBe('manual');
});
