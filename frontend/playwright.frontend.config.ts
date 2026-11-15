import { defineConfig } from '@playwright/test';
import base from './playwright.config';

// Interface isolada com API simulada: substitui (não soma) o webServer do app real.
export default defineConfig({
  ...base,
  testMatch: '**/frontend.spec.ts',
  testIgnore: [],
  use: { ...base.use, baseURL: 'http://127.0.0.1:4173' },
  webServer: {
    command: `"${process.execPath}" node_modules/vite/bin/vite.js --host 127.0.0.1 --port 4173 --strictPort`,
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: false,
  },
});
