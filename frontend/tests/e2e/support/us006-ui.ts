import { expect, type Page } from '@playwright/test';
import type { ProfileInput } from './us006.ts';

// Rótulos de frontend/src/components/ProfileEditor.tsx e pages/Profile.tsx (US006).
const SENIORITY = { junior: 'Júnior', pleno: 'Pleno', senior: 'Sênior' } as const;

const editor = (page: Page) => page.getByRole('form', { name: 'Revisão do perfil' });

/** Abre /perfil e, se já houver perfil, entra em "Editar perfil". */
export async function openProfileEditor(page: Page) {
  await page.goto('/perfil');
  await expect(page.getByRole('heading', { name: 'Seu perfil' })).toBeVisible();
  const edit = page.getByRole('button', { name: 'Editar perfil' });
  const importForm = page.getByRole('form', { name: 'Importar currículo' });
  await expect(edit.or(importForm)).toBeVisible();
  if (await edit.isVisible()) await edit.click();
}

/** Carrega o currículo pelo campo de arquivo real e pede o rascunho. */
export async function importMarkdownFile(page: Page, filePath: string) {
  const form = page.getByRole('form', { name: 'Importar currículo' });
  await form.getByLabel('Arquivo do currículo (.pdf, .md ou .txt)').setInputFiles(filePath);
  await expect(form.getByLabel('Ou cole o texto do currículo')).not.toHaveValue('');
  await form.getByRole('button', { name: 'Ler e revisar' }).click();
  await expect(editor(page)).toBeVisible();
}

export async function startManualProfile(page: Page) {
  await page.getByRole('button', { name: 'Preencher manualmente' }).click();
  await expect(editor(page)).toBeVisible();
}

const LABELS = {
  role: 'Cargo desejado',
  primaryTechnology: 'Tecnologia principal',
  secondaryTechnologies: 'Tecnologias secundárias (uma por linha)',
  flexibleTechnologies: 'Preferências flexíveis — aceito trabalhar com (uma por linha)',
  priorities: 'Empresas prioritárias, em ordem (uma por linha)',
  juniorFloorCltMonthlyBrl: 'Piso Júnior CLT',
  juniorFloorPjMonthlyBrl: 'Piso Júnior PJ',
} as const;

export const field = (page: Page, key: keyof typeof LABELS) =>
  editor(page).getByLabel(LABELS[key], { exact: true });

/** Preenche só os campos informados, como a pessoa faria. */
export async function fillProfileForm(page: Page, values: Partial<ProfileInput>) {
  for (const key of Object.keys(LABELS) as (keyof typeof LABELS)[]) {
    const value = values[key];
    if (value === undefined) continue;
    const text = Array.isArray(value) ? value.join('\n') : value === null ? '' : String(value);
    await field(page, key).fill(text);
  }
  if (values.currentSeniority)
    await editor(page)
      .getByLabel('Senioridade atual')
      .selectOption({ label: SENIORITY[values.currentSeniority] });
  if (values.targetLevels) {
    const group = editor(page).getByRole('group', { name: 'Níveis desejados' });
    for (const [level, label] of Object.entries(SENIORITY))
      await group
        .getByLabel(label, { exact: true })
        .setChecked(values.targetLevels.includes(level as keyof typeof SENIORITY));
  }
}

export async function saveProfile(page: Page) {
  await editor(page).getByRole('button', { name: 'Salvar perfil' }).click();
  await expect(page.getByRole('status').filter({ hasText: /Perfil salvo/ })).toBeVisible();
}
