import { expect, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { e2eDb, seedOpportunities, signIn, expectNoHorizontalOverflow } from './support/seed.ts';

test('salary references: import, display, declared precedence and no ranking changes', async ({
  page,
}) => {
  test.skip(!e2eDb, 'Requires disposable seeded database');
  seedOpportunities([
    {
      id: 'salary-pleno',
      title: 'Desenvolvedor Java Pleno',
      company: 'CWI Software',
      location: 'Brasil',
      contractType: 'clt',
      workMode: 'unknown',
      selectedRank: 1,
    },
    {
      id: 'salary-declared',
      title: 'Desenvolvedor Java Pleno',
      company: 'Act Digital',
      salary: { min: 9000, max: 11000, unit: 'monthly' },
      selectedRank: 2,
    },
    {
      id: 'salary-other-company',
      title: 'Desenvolvedor Java Pleno',
      company: 'Stefanini',
      selectedRank: 3,
    },
  ]);
  await signIn(page);
  const before = await page.request.get('/api/opportunities');
  const original = await before.json();
  const session = await (await page.request.get('/api/session')).json();
  const observations = JSON.parse(
    readFileSync(
      new URL(
        '../../../specs/002-estimativa-salarial/pleno-web-observations.json',
        import.meta.url,
      ),
      'utf8',
    ).replace(/^\uFEFF/, ''),
  );
  const imported = await page.request.post('/api/salary-estimates/import', {
    data: observations,
    headers: {
      'X-CSRF-Token': session.csrfToken,
      'Idempotency-Key': 'salary-e2e-real-observations',
    },
  });
  expect(imported.status()).toBe(201);
  // Controlled compatible fixture separately from the three real, regime-unknown observations.
  const compatible = {
    ...observations[0],
    criteria: { ...observations[0].criteria, contractType: 'clt' },
    evidence: 'TEST FIXTURE: CLT compatibility, not real source evidence.',
  };
  expect(
    (
      await page.request.post('/api/salary-estimates/import', {
        data: [compatible],
        headers: {
          'X-CSRF-Token': session.csrfToken,
          'Idempotency-Key': 'salary-e2e-compatible-fixture',
        },
      })
    ).status(),
  ).toBe(201);
  const after = await (await page.request.get('/api/opportunities')).json();
  expect(after.items.map((i: { id: string }) => i.id)).toEqual(
    original.items.map((i: { id: string }) => i.id),
  );
  await page.goto('/');
  await expect(page.getByText(/Estimativa:/)).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await page.goto('/vagas');
  await expect(page.getByText(/Estimativa:/)).toBeVisible();
  await page.goto('/vagas/salary-pleno');
  const inferred = page.locator('.req-inferred');
  await expect(inferred.getByText(/Estimativa:/)).toBeVisible();
  const link = inferred.getByRole('link', { name: /Ver no Glassdoor/ });
  await expect(link).toHaveAttribute('target', '_blank');
  await expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  await expect(link).toHaveAttribute('href', observations[0].sourceUrl);
  await expect(page.locator('.req-facts')).not.toContainText('Estimativa:');
  await expectNoHorizontalOverflow(page);
  await page.goto('/vagas/salary-declared');
  await expect(page.getByText(/Estimativa:/)).toHaveCount(0);
  await page.goto('/referencias-salariais');
  await expect(
    page.getByRole('heading', { name: /stefanini — desenvolvedor java pleno/i }),
  ).toBeVisible();
  await expect(page.getByText('Regime não informado pela fonte', { exact: false })).toHaveCount(3);
  await expectNoHorizontalOverflow(page);
});
