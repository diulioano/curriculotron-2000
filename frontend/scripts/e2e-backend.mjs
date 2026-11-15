// Sobe o app real (API + SPA) para as jornadas e2e com dados controlados (quickstart §3).
// Diretório de dados próprio e descartável, HTTP só em 127.0.0.1, agendador desligado:
// nenhuma rotina dispara busca em portais reais. Os testes semeiam o SQLite diretamente.
import { spawn, spawnSync } from 'node:child_process';
import { existsSync, readdirSync, rmSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontend = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const backend = resolve(frontend, '../backend');
const home = process.env.CURRICULOTRON_E2E_HOME ?? join(backend, 'build', 'e2e-home');
const port = process.env.CURRICULOTRON_E2E_PORT ?? '18443';
const windows = process.platform === 'win32';
const java = process.env.JAVA_HOME
  ? join(process.env.JAVA_HOME, 'bin', windows ? 'java.exe' : 'java')
  : 'java';

function run(command, args, cwd) {
  // Argumentos fixos deste script; o shell só é usado para executar o .bat do Gradle no Windows.
  const result = spawnSync([command, ...args].join(' '), { cwd, stdio: 'inherit', shell: true });
  if (result.status !== 0) {
    console.error(`Falhou: ${command} ${args.join(' ')}`);
    process.exit(result.status ?? 1);
  }
}

if (!process.env.CURRICULOTRON_E2E_SKIP_BUILD) {
  run(`"${process.execPath}"`, ['node_modules/typescript/bin/tsc', '-b'], frontend);
  run(`"${process.execPath}"`, ['node_modules/vite/bin/vite.js', 'build'], frontend);
  run(
    `"${join(backend, windows ? 'gradlew.bat' : 'gradlew')}"`,
    ['bootJar', '--console=plain', '-q'],
    backend,
  );
}

const libs = join(backend, 'build', 'libs');
const jar =
  existsSync(libs) && readdirSync(libs).find((f) => f.endsWith('.jar') && !f.includes('-plain'));
if (!jar) {
  console.error('Jar do backend não encontrado em backend/build/libs.');
  process.exit(1);
}

rmSync(home, { recursive: true, force: true });
const app = spawn(
  java,
  [
    '--enable-native-access=ALL-UNNAMED',
    '-jar',
    join(libs, jar),
    '--curriculotron.scheduler.enabled=false',
    '--curriculotron.resume-file=',
  ],
  {
    cwd: backend,
    stdio: 'inherit',
    env: {
      ...process.env,
      CURRICULOTRON_HOME: home,
      CURRICULOTRON_PORT: port,
      CURRICULOTRON_BIND: '127.0.0.1',
      CURRICULOTRON_TLS_ENABLED: 'false',
    },
  },
);
const stop = () => app.kill();
process.on('SIGINT', stop);
process.on('SIGTERM', stop);
app.on('exit', (code) => process.exit(code ?? 0));
