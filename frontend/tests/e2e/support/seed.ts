import { DatabaseSync } from 'node:sqlite';
import { expect, type Page } from '@playwright/test';

/** Banco do app isolado (playwright.config.ts). Ausente ⇒ app externo, sem semeadura. */
export const e2eDb = process.env.CURRICULOTRON_E2E_DB;
const password = process.env.CURRICULOTRON_E2E_PASSWORD;
if (!password) throw new Error('CURRICULOTRON_E2E_PASSWORD ausente');
export const PASSWORD: string = password;

export const saoPauloDate = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date());

export interface SeedCompatibility {
  criteriaVersion?: number;
  evaluatorVersion?: number;
  evidenceRevision?: number;
  assessedAt?: string;
  roleStatus: 'matched' | 'mismatched' | 'unknown';
  technologyStatus: 'matched' | 'mismatched' | 'unknown' | 'unrestricted';
  reasonCodes?: string[];
  fitReasons?: string[];
}

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
  evidenceRevision?: number;
  descriptionText?: string;
  descriptionCompleteness?: 'complete' | 'partial' | 'unknown';
  /** Omitir semeia compatibilidade atual padrão; `null` deixa a vaga sem avaliação (legado). */
  compatibility?: SeedCompatibility | null;
}

export interface SeedOptions {
  criteria?: {
    version: number;
    targetRoles: string[];
    stackKeywords: string[];
  };
  refresh?: {
    state?: 'pending' | 'running' | 'ready' | 'failed';
    assessedCount?: number;
    remainingCount?: number;
    errorCode?: string | null;
  };
}

/** Substitui todas as vagas por um conjunto controlado (sem portais reais). */
export function seedOpportunities(jobs: SeedJob[], options: SeedOptions = {}) {
  if (!e2eDb) throw new Error('CURRICULOTRON_E2E_DB ausente');
  const db = new DatabaseSync(e2eDb, { timeout: 10_000 });
  try {
    db.exec('PRAGMA foreign_keys = ON');
    db.exec('BEGIN IMMEDIATE');
    // Reset only the disposable e2e database, including append-only history.
    db.exec('DROP TRIGGER status_change_no_delete');
    db.exec('DELETE FROM status_change');
    db.exec(
      "CREATE TRIGGER status_change_no_delete BEFORE DELETE ON status_change BEGIN SELECT RAISE(ABORT, 'status_change is append-only'); END",
    );
    db.exec('DELETE FROM application');
    db.exec('DELETE FROM evidence');
    db.exec('DELETE FROM review_item');
    for (const table of [
      'daily_selection',
      'opportunity_compatibility',
      'opportunity_evidence',
      'opportunity_match_candidate',
      'opportunity_url_identity',
      'opportunity_external_identity',
      'opportunity_source',
    ])
      db.exec(`DELETE FROM ${table}`);
    db.exec('UPDATE opportunity SET merged_into = NULL');
    db.exec('DELETE FROM opportunity');

    const criteriaVersion = options.criteria?.version ?? 1;
    const targetRoles = options.criteria?.targetRoles ?? [
      'Desenvolvedor Fullstack Pleno Java',
      'Desenvolvedor Backend Pleno Java',
      'Software Engineer Java',
    ];
    const stackKeywords = options.criteria?.stackKeywords ?? [
      'Java',
      'Java 17',
      'Spring Boot',
      'Angular',
      'React',
      'REST',
      'Kafka',
      'Microsserviços',
      'JUnit',
      'Mockito',
    ];
    db.prepare(
      'UPDATE search_criteria SET version = ?, target_roles = ?, stack_keywords = ? WHERE id = 1',
    ).run(criteriaVersion, JSON.stringify(targetRoles), JSON.stringify(stackKeywords));

    const at = new Date().toISOString();
    const insert = db.prepare(
      `INSERT INTO opportunity(id,title,company_name,location,work_mode,contract_type,salary_min,salary_max,salary_unit,
        requirements_facts,requirements_inferred,availability,fit,fit_score,fit_reasons,user_decision,evidence_revision,created_at,updated_at)
       VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`,
    );
    const source = db.prepare(
      `INSERT INTO opportunity_source(id,opportunity_id,source,canonical_url,fetched_at,created_at,updated_at)
       VALUES (?,?,?,?,?,?,?)`,
    );
    const identity = db.prepare(
      'INSERT INTO opportunity_url_identity(canonical_url,opportunity_id) VALUES (?,?)',
    );
    const evidence = db.prepare(
      `INSERT INTO opportunity_evidence(id,opportunity_id,source_id,source_kind,title,location,description_text,description_completeness,completeness_reason,content_revision,fetched_at)
       VALUES (?,?,?,?,?,?,?,?,?,?,?)`,
    );
    const compatibility = db.prepare(
      `INSERT INTO opportunity_compatibility(opportunity_id,criteria_version,evaluator_version,evidence_revision,assessed_at,role_status,technology_status,reason_codes,criteria_snapshot,evidence_items,fit,fit_score,fit_reasons)
       VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)`,
    );
    const select = db.prepare(
      'INSERT INTO daily_selection(local_date,opportunity_id,rank,reason) VALUES (?,?,?,?)',
    );

    let pendingCount = 0;
    let currentAssessedCount = 0;

    for (const job of jobs) {
      const fit = job.fit ?? 'strong';
      const evidenceRevision = job.evidenceRevision ?? 1;
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
        evidenceRevision,
        at,
        at,
      );
      let sourceId: string | null = null;
      if (job.source) {
        sourceId = `${job.id}-src`;
        source.run(
          sourceId,
          job.id,
          job.source.source,
          job.source.url,
          job.source.fetchedAt,
          at,
          at,
        );
        identity.run(job.source.url, job.id);
      }
      evidence.run(
        `${job.id}-ev`,
        job.id,
        sourceId,
        job.source?.source ?? 'manual',
        job.title,
        job.location ?? null,
        job.descriptionText ?? (job.facts ?? []).join('\n'),
        job.descriptionCompleteness ?? 'complete',
        null,
        evidenceRevision,
        job.source?.fetchedAt ?? at,
      );
      if (job.compatibility !== null) {
        const comp = job.compatibility ?? {
          roleStatus: fit === 'strong' ? ('matched' as const) : ('unknown' as const),
          technologyStatus: fit === 'strong' ? ('matched' as const) : ('unknown' as const),
          reasonCodes:
            fit === 'strong'
              ? ['role_matched', 'technology_matched']
              : ['role_unknown', 'technology_unknown'],
        };
        const compCriteriaVersion = comp.criteriaVersion ?? criteriaVersion;
        const compEvaluatorVersion = comp.evaluatorVersion ?? 1;
        const compEvidenceRevision = comp.evidenceRevision ?? evidenceRevision;
        const criteriaSnapshot = {
          criteria: {
            roles: targetRoles,
            stack: comp.technologyStatus === 'unrestricted' ? [] : stackKeywords,
            hybridRegions: ['Porto Alegre'],
            priorityCompanies: [],
            remotePreferred: true,
            acceptJunior: false,
          },
          cltMinimum: 6000,
          pjMinimum: 9000,
        };
        compatibility.run(
          job.id,
          compCriteriaVersion,
          compEvaluatorVersion,
          compEvidenceRevision,
          comp.assessedAt ?? at,
          comp.roleStatus,
          comp.technologyStatus,
          JSON.stringify(comp.reasonCodes ?? ['role_matched', 'technology_matched']),
          JSON.stringify(criteriaSnapshot),
          '[]',
          fit,
          fit === 'strong' ? 90 : fit === 'medium' ? 50 : 10,
          JSON.stringify(comp.fitReasons ?? job.fitReasons ?? []),
        );
        if (
          compCriteriaVersion === criteriaVersion &&
          compEvaluatorVersion === 1 &&
          compEvidenceRevision === evidenceRevision
        ) {
          currentAssessedCount++;
        } else {
          pendingCount++;
        }
      } else {
        pendingCount++;
      }
      if (job.selectedRank)
        select.run(saoPauloDate(), job.id, job.selectedRank, 'Seleção controlada do teste');
    }

    const refreshState = options.refresh?.state ?? (pendingCount > 0 ? 'pending' : 'ready');
    db.prepare(
      `UPDATE compatibility_refresh
       SET target_criteria_version = ?,
           evaluator_version = 1,
           run_id = NULL,
           state = ?,
           started_at = NULL,
           finished_at = ?,
           error_code = ?,
           assessed_count = ?,
           remaining_count = ?
       WHERE id = 1`,
    ).run(
      criteriaVersion,
      refreshState,
      refreshState === 'ready' ? at : null,
      options.refresh?.errorCode ?? null,
      options.refresh?.assessedCount ?? currentAssessedCount,
      options.refresh?.remainingCount ?? pendingCount,
    );

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
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    .toBe(true);
}
