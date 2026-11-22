import { DatabaseSync } from 'node:sqlite';
import { expect, type Page } from '@playwright/test';

/** Banco do app isolado (playwright.config.ts). Ausente ⇒ app externo, sem semeadura. */
export const e2eDb = process.env.CURRICULOTRON_E2E_DB;
const password = process.env.CURRICULOTRON_E2E_PASSWORD;
if (!password) throw new Error('CURRICULOTRON_E2E_PASSWORD ausente');
export const PASSWORD: string = password;

export const saoPauloDate = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date());

export interface SeedEvidenceItem {
  evidenceId?: string;
  source?: string;
  sourceUrl?: string | null;
  fetchedAt?: string | null;
  field: 'title' | 'description';
  section?: string | null;
  quote: string;
  matchedCriterion?: string | null;
  observedTerm?: string | null;
  mentionKind: 'required' | 'optional' | 'usage' | 'negated' | 'incidental' | 'uncertain';
  observedVersion?: string | null;
  configuredVersion?: string | null;
}

export interface SeedCompatibility {
  criteriaVersion?: number;
  evaluatorVersion?: number;
  evidenceRevision?: number;
  assessedAt?: string;
  roleStatus: 'matched' | 'mismatched' | 'unknown';
  technologyStatus: 'matched' | 'mismatched' | 'unknown' | 'unrestricted';
  reasonCodes?: string[];
  fitReasons?: string[];
  criteriaRoles?: string[];
  criteriaStack?: string[];
  evidenceItems?: SeedEvidenceItem[];
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
  applicationStatus?:
    | 'submitted_awaiting'
    | 'contact_received'
    | 'interview'
    | 'offer'
    | 'closed_rejected'
    | 'withdrawn';
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
  dbPath?: string;
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
  studyFacts?: string[];
}

/** Substitui todas as vagas por um conjunto controlado (sem portais reais). */
export function seedOpportunities(jobs: SeedJob[], options: SeedOptions = {}) {
  const targetDb = options.dbPath ?? e2eDb;
  if (!targetDb) throw new Error('CURRICULOTRON_E2E_DB ausente');
  const db = new DatabaseSync(targetDb, { timeout: 10_000 });
  try {
    db.exec('PRAGMA foreign_keys = ON');
    db.exec('BEGIN IMMEDIATE');
    // Reset only the disposable e2e database, including append-only history.
    db.exec('DROP TRIGGER status_change_no_delete');
    db.exec('DELETE FROM status_change');
    db.exec(
      "CREATE TRIGGER status_change_no_delete BEFORE DELETE ON status_change BEGIN SELECT RAISE(ABORT, 'status_change is append-only'); END",
    );
    // opportunity_correction (V14) também é append-only e referencia opportunity(id).
    db.exec('DROP TRIGGER IF EXISTS opportunity_correction_no_delete');
    db.exec('DELETE FROM opportunity_correction');
    db.exec(
      "CREATE TRIGGER opportunity_correction_no_delete BEFORE DELETE ON opportunity_correction BEGIN SELECT RAISE(ABORT, 'opportunity_correction e append-only'); END",
    );
    db.exec('DELETE FROM application');
    db.exec('DELETE FROM evidence');
    db.exec('DELETE FROM review_item');
    db.exec('DELETE FROM profile_fact');
    db.exec('DELETE FROM profile_version');
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
    const applicationInsert = db.prepare(
      `INSERT INTO application(id,opportunity_id,confirmed_at,confirmation_origin,status,next_action,next_action_due_at,due_precision,created_at,updated_at)
       VALUES (?,?,?,?,?,?,?,?,?,?)`,
    );
    const statusChangeInsert = db.prepare(
      `INSERT INTO status_change(id,application_id,from_status,to_status,author,evidence_id,reason,at)
       VALUES (?,?,?,?,?,?,?,?)`,
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
      if (job.decision === 'applied') {
        const appId = `app-${job.id}`;
        const appStatus = job.applicationStatus ?? 'submitted_awaiting';
        applicationInsert.run(
          appId,
          job.id,
          at,
          'manual',
          appStatus,
          null,
          null,
          'ambiguous',
          at,
          at,
        );
        statusChangeInsert.run(
          `sc-${job.id}-1`,
          appId,
          null,
          appStatus,
          'user',
          null,
          'Candidatura registrada antes da reavaliação',
          at,
        );
      }
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
        const comp: SeedCompatibility = job.compatibility ?? {
          roleStatus: fit === 'strong' ? 'matched' : 'unknown',
          technologyStatus: fit === 'strong' ? 'matched' : 'unknown',
          reasonCodes:
            fit === 'strong'
              ? ['role_matched', 'technology_matched']
              : ['role_unknown', 'technology_unknown'],
        };
        const compCriteriaVersion = comp.criteriaVersion ?? criteriaVersion;
        const compEvaluatorVersion = comp.evaluatorVersion ?? 2;
        const compEvidenceRevision = comp.evidenceRevision ?? evidenceRevision;
        const snapshotRoles = comp.criteriaRoles ?? targetRoles;
        const snapshotStack =
          comp.criteriaStack ?? (comp.technologyStatus === 'unrestricted' ? [] : stackKeywords);
        const criteriaSnapshot = {
          criteria: {
            roles: snapshotRoles,
            stack: snapshotStack,
            hybridRegions: ['Porto Alegre'],
            priorityCompanies: [],
            remotePreferred: true,
            acceptJunior: false,
          },
          cltMinimum: 6000,
          pjMinimum: 9000,
        };
        const evidenceItems = (comp.evidenceItems ?? []).map((item, idx) => ({
          evidenceId: item.evidenceId ?? `${job.id}-ev-${idx + 1}`,
          source: item.source ?? job.source?.source ?? 'manual',
          sourceUrl: item.sourceUrl !== undefined ? item.sourceUrl : (job.source?.url ?? null),
          fetchedAt: item.fetchedAt !== undefined ? item.fetchedAt : (job.source?.fetchedAt ?? at),
          field: item.field,
          section: item.section ?? null,
          quote: item.quote,
          matchedCriterion: item.matchedCriterion ?? null,
          observedTerm: item.observedTerm ?? null,
          mentionKind: item.mentionKind,
          observedVersion: item.observedVersion ?? null,
          configuredVersion: item.configuredVersion ?? null,
        }));
        const defaultReasonCodes = [
          `role_${comp.roleStatus}`,
          `technology_${comp.technologyStatus}`,
        ];
        compatibility.run(
          job.id,
          compCriteriaVersion,
          compEvaluatorVersion,
          compEvidenceRevision,
          comp.assessedAt ?? at,
          comp.roleStatus,
          comp.technologyStatus,
          JSON.stringify(comp.reasonCodes ?? defaultReasonCodes),
          JSON.stringify(criteriaSnapshot),
          JSON.stringify(evidenceItems),
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
           evaluator_version = 2,
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

    if (options.studyFacts && options.studyFacts.length > 0) {
      db.prepare(
        `INSERT INTO profile_version(id, source_file, source_hash, status, reviewed_at, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
      ).run('pv-e2e-1', 'curriculo.md', 'hash-e2e-1', 'reviewed', at, at, at);
      const insertFact = db.prepare(
        `INSERT INTO profile_fact(id, profile_version_id, kind, statement, origin, origin_ref, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      );
      options.studyFacts.forEach((statement, idx) => {
        insertFact.run(
          `pf-study-${idx + 1}`,
          'pv-e2e-1',
          'skill',
          statement,
          'study_recommended',
          'Plano de estudos',
          at,
          at,
        );
      });
    }

    db.exec('COMMIT');
  } catch (e) {
    if (db.isTransaction) db.exec('ROLLBACK');
    throw e;
  } finally {
    db.close();
  }
}

/** Simula reinício interrompido com refresh em estado running e opcionalmente remove uma avaliação. */
export function simulateInterruptedRefresh(dbPath: string, unassessOpportunityId?: string) {
  const db = new DatabaseSync(dbPath, { timeout: 10_000 });
  try {
    db.exec('PRAGMA foreign_keys = ON');
    db.exec('BEGIN IMMEDIATE');
    if (unassessOpportunityId) {
      db.prepare('DELETE FROM opportunity_compatibility WHERE opportunity_id = ?').run(
        unassessOpportunityId,
      );
    }
    const at = new Date().toISOString();
    db.prepare(
      `UPDATE compatibility_refresh
       SET state = 'running',
           run_id = 'run-interrupted-e2e',
           started_at = ?,
           finished_at = NULL,
           error_code = NULL,
           remaining_count = CASE WHEN ? IS NOT NULL THEN remaining_count + 1 ELSE remaining_count END
       WHERE id = 1`,
    ).run(at, unassessOpportunityId ?? null);
    db.exec('COMMIT');
  } catch (e) {
    if (db.isTransaction) db.exec('ROLLBACK');
    throw e;
  } finally {
    db.close();
  }
}

/** Primeiro uso define a senha; execuções seguintes (outro viewport) apenas entram. */
export async function signIn(page: Page, baseUrl = '/') {
  await page.goto(baseUrl);
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
