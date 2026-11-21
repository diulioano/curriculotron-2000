import { expect, test } from '@playwright/test';
import {
  e2eDb,
  seedOpportunities,
  signIn,
  saoPauloDate,
  expectNoHorizontalOverflow,
} from './support/seed.ts';

test.skip(!e2eDb, 'Requer app isolado.');
test('J3: confirmação manual, histórico, próxima ação e prazo no início', async ({ page }) => {
  seedOpportunities([{ id: 'j3-java', title: 'Java Backend', company: 'SAP', selectedRank: 1 }]);
  await signIn(page);
  await page.goto('/vagas/j3-java');
  await page.getByRole('button', { name: 'Me candidatei', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Candidatura registrada');
  await expect(page.getByRole('button', { name: 'Candidatura já registrada' })).toBeDisabled();
  await page.goto('/candidaturas');
  await page.getByRole('link', { name: 'Java Backend' }).click();
  await page.getByLabel('Etapa', { exact: true }).click();
  await page.getByRole('option', { name: 'Entrevista' }).click();
  await page.getByLabel('Motivo ou correção').fill('Convite recebido por telefone');
  await page.getByRole('button', { name: 'Salvar etapa' }).click();
  await expect(page.locator('.timeline li')).toHaveCount(2);
  await expect(page.locator('.timeline')).toContainText('Convite recebido por telefone');
  await page.getByLabel('O que precisa fazer?').fill('Preparar entrevista Java');
  await page.getByLabel('Prazo (opcional, horário de São Paulo)').fill(saoPauloDate());
  await page.getByRole('button', { name: 'Salvar próximo passo' }).click();
  await expect(page.locator('.deadline.urgent')).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await page.reload();
  await expect(page.getByLabel('O que precisa fazer?')).toHaveValue('Preparar entrevista Java');
  await expect(page.locator('.timeline li')).toHaveCount(2);
  await page.goto('/');
  await expect(page.locator('.next-actions')).toContainText('Preparar entrevista Java');
  await expect(page.locator('.next-actions .next-due.hl')).toBeVisible();
  await expect(page.getByRole('heading', { name: /Vagas do dia/ })).toContainText('0/10');
  await expectNoHorizontalOverflow(page);
});
