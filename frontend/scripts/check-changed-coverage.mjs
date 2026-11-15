import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const base = process.argv[2];
if (!/^[0-9a-f]{40}$/.test(base ?? '')) {
  throw new Error('Pass the pull request base commit SHA.');
}

const diff = execFileSync(
  'git',
  ['diff', '--no-color', '--unified=0', '--diff-filter=ACMR', base, 'HEAD', '--', 'src'],
  { encoding: 'utf8' },
);
const changed = new Map();
let file;
for (const line of diff.split('\n')) {
  if (line.startsWith('+++ b/')) {
    file = line.slice(6);
    if (/^frontend\/src\/.*\.tsx?$/.test(file)) changed.set(file, new Set());
    else file = undefined;
  } else if (file && line.startsWith('@@')) {
    const match = line.match(/\+(\d+)(?:,(\d+))? @@/);
    if (!match) throw new Error(`Cannot parse diff hunk: ${line}`);
    const start = Number(match[1]);
    const count = Number(match[2] ?? 1);
    for (let number = start; number < start + count; number++) changed.get(file).add(number);
  }
}

const report = JSON.parse(readFileSync(resolve('coverage/coverage-final.json'), 'utf8'));
const coverageByFile = new Map(
  Object.values(report).map((entry) => [entry.path.replaceAll('\\', '/'), entry]),
);
let covered = 0;
let total = 0;
for (const [path, lines] of changed) {
  const entry = [...coverageByFile].find(([absolute]) => absolute.endsWith(`/${path}`))?.[1];
  if (!entry) throw new Error(`No coverage report for changed source file: ${path}`);
  const executable = new Map();
  for (const [id, statement] of Object.entries(entry.statementMap)) {
    const number = statement.start.line;
    if (lines.has(number)) {
      executable.set(number, Math.max(executable.get(number) ?? 0, entry.s[id]));
    }
  }
  for (const [id, branch] of Object.entries(entry.branchMap)) {
    const number = branch.line;
    if (lines.has(number)) {
      executable.set(number, Math.max(executable.get(number) ?? 0, ...entry.b[id]));
    }
  }
  const hits = [...executable.values()].filter((count) => count > 0).length;
  covered += hits;
  total += executable.size;
  console.log(`${path}: ${hits}/${executable.size} changed executable lines covered`);
}
if (total === 0) {
  console.log('No changed executable TypeScript lines.');
} else {
  const percent = (100 * covered) / total;
  console.log(`Changed-line coverage: ${covered}/${total} (${percent.toFixed(2)}%)`);
  if (percent < 80) process.exitCode = 1;
}
