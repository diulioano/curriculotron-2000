import { defineConfig, devices } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

// Jornadas J1–J6 em 360 px (celular) e 1280 px (computador) — D-12, NFR-005.
// Sem CURRICULOTRON_URL, sobe o app real isolado com dados controlados (scripts/e2e-backend.mjs);
// os testes semeiam o banco em CURRICULOTRON_E2E_DB. Com CURRICULOTRON_URL, usa o app indicado
// e as jornadas que dependem de semeadura são puladas.
const external = process.env.CURRICULOTRON_URL;
process.env.CURRICULOTRON_E2E_PASSWORD ??= `${randomUUID()}${randomUUID()}`;
const port = process.env.CURRICULOTRON_E2E_PORT ?? '18443';
const home =
  process.env.CURRICULOTRON_E2E_HOME ??
  join(
    fileURLToPath(new URL('.', import.meta.url)),
    '..',
    'backend',
    'build',
    `curriculotron-e2e-${process.pid}-${randomUUID()}`,
  );
if (!external) process.env.CURRICULOTRON_E2E_DB ??= join(home, 'data', 'curriculotron.db');
const baseURL = external ?? `http://127.0.0.1:${port}`;

export default defineConfig({
  testDir: 'tests/e2e',
  testIgnore: '**/frontend.spec.ts',
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  use: { baseURL, ignoreHTTPSErrors: true, trace: 'retain-on-failure' },
  webServer: external
    ? undefined
    : {
        command: `"${process.execPath}" scripts/e2e-backend.mjs`,
        url: `${baseURL}/api/health`,
        timeout: 300_000,
        reuseExistingServer: false,
        env: { CURRICULOTRON_E2E_HOME: home, CURRICULOTRON_E2E_PORT: port },
      },
  projects: [
    {
      name: 'mobile-360',
      use: { ...devices['Desktop Chrome'], viewport: { width: 360, height: 740 } },
    },
    {
      name: 'desktop-1280',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 900 } },
    },
  ],
});
