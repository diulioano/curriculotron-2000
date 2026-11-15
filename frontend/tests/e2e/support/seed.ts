import { DatabaseSync } from 'node:sqlite';
import { expect, type Page } from '@playwright/test';

/** Banco do app isolado (playwright.config.ts). Ausente ⇒ app externo, sem semeadura. */
export const e2eDb = process.env.CURRICULOTRON_E2E_DB;
export const PASSWORD = 'senha-e2e-curriculotron';

export const saoPauloDate = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date());

export interface SeedJob {
  id: string;
  title: string;
  company: string;
  location?: string | null;
  workMode?: 'remote' | 'hybrid' | 'onsite' | 'unknown';
  contractType?: 'clt' | 'pj' | 'other' | 'unknown';
  salary?: { min: number | null; max: number | null; unit: string } | null;
  facts?: string[];
  inferred?: string[];
  fit?: 'strong' | 'medium' | 'weak';
  fitReasons?: string[];
  decision?: 'none' | 'discarded' | 'applied';
  availability?: 'open' | 'closed' | 'unconfirmed';
  source?: { source: string; url: string; fetchedAt: string };
  selectedRank?: number;
}

/** Substitui todas as vagas por um conjunto controlado (sem portais reais). */
export function seedOpportunities(jobs: SeedJob[]) {
  if (!e2eDb) throw new Error('CURRICULOTRON_E2E_DB ausente');
  const db = new DatabaseSync(e2eDb, { timeout: 10_000 });
  try {
    db.exec('PRAGMA foreign_keys = ON');
    db.exec('BEGIN IMMEDIATE');
    for (const table of [
      'daily_selection',
      'opportunity_match_candidate',
      'opportunity_url_identity',
      'opportunity_external_identity',
      'opportunity_source',
    ])
      db.exec(`DELETE FROM ${table}`);
    db.exec('UPDATE opportunity SET merged_into = NULL');
    db.exec('DELETE FROM opportunity');
    const at = new Date().toISOString();
    const insert = db.prepare(
      `INSERT INTO opportunity(id,title,company_name,location,work_mode,contract_type,salary_min,salary_max,salary_unit,
        requirements_facts,requirements_inferred,availability,fit,fit_score,fit_reasons,user_decision,created_at,updated_at)
       VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`,
    );
    const source = db.prepare(
      `INSERT INTO opportunity_source(id,opportunity_id,source,canonical_url,fetched_at,created_at,updated_at)
       VALUES (?,?,?,?,?,?,?)`,
    );
    const identity = db.prepare(
      'INSERT INTO opportunity_url_identity(canonical_url,opportunity_id) VALUES (?,?)',
    );
    const select = db.prepare(
      'INSERT INTO daily_selection(local_date,opportunity_id,rank,reason) VALUES (?,?,?,?)',
    );
    for (const job of jobs) {
      const fit = job.fit ?? 'strong';
      insert.run(
        job.id,
        job.title,
        job.company,
        job.location ?? null,
        job.workMode ?? 'unknown',
        job.contractType ?? 'unknown',
        job.salary?.min ?? null,
        job.salary?.max ?? null,
        job.salary?.unit ?? 'unknown',
        JSON.stringify(job.facts ?? []),
        JSON.stringify(job.inferred ?? []),
        job.availability ?? 'open',
        fit,
        fit === 'strong' ? 90 : fit === 'medium' ? 50 : 10,
        JSON.stringify(job.fitReasons ?? []),
        job.decision ?? 'none',
        at,
        at,
      );
      if (job.source) {
        source.run(
          `${job.id}-src`,
          job.id,
          job.source.source,
          job.source.url,
          job.source.fetchedAt,
          at,
          at,
        );
        identity.run(job.source.url, job.id);
      }
      if (job.selectedRank)
        select.run(saoPauloDate(), job.id, job.selectedRank, 'Seleção controlada do teste');
    }
    db.exec('COMMIT');
  } catch (e) {
    if (db.isTransaction) db.exec('ROLLBACK');
    throw e;
  } finally {
    db.close();
  }
}

/** Primeiro uso define a senha; execuções seguintes (outro viewport) apenas entram. */
export async function signIn(page: Page) {
  await page.goto('/');
  const heading = page.getByRole('heading', { level: 1 });
  await expect(heading).toBeVisible();
  const label = (await heading.textContent()) ?? '';
  if (label.includes('Primeiro uso')) {
    await page.getByLabel('Senha', { exact: true }).fill(PASSWORD);
    await page.getByLabel('Repita a senha').fill(PASSWORD);
    await page.getByRole('button', { name: 'Definir senha e entrar' }).click();
  } else if (label.includes('Entrar')) {
    await page.getByLabel('Senha', { exact: true }).fill(PASSWORD);
    await page.getByRole('button', { name: 'Entrar' }).click();
  }
  await expect(page.getByRole('heading', { name: /Vagas do dia/ })).toBeVisible();
}

/** Nenhum elemento do conteúdo ultrapassa a largura da janela (NFR-005). */
export async function expectNoHorizontalOverflow(page: Page) {
  const overflowing = await page.locator('main *').evaluateAll((elements) =>
    elements
      .filter((el) => {
        const r = el.getBoundingClientRect();
        return r.width > 0 && r.right > innerWidth + 1;
      })
      .map((el) => `${el.tagName}.${el.className}`),
  );
  expect(overflowing).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
}
