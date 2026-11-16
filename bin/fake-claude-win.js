'use strict';

const { spawnSync } = require('node:child_process');
const path = require('node:path');

const script = path.join(__dirname, 'fake-claude');
const args = process.argv.slice(2);
const env = Object.assign({}, process.env, { PYTHONUTF8: '1' });
const interpreters = [
  ['py', ['-3']],
  ['python3', []],
  ['python', []],
];

for (const [command, prefix] of interpreters) {
  const run = spawnSync(command, prefix.concat([script], args), { stdio: 'inherit', env: env });
  if (!run.error) process.exit(run.status === null ? 1 : run.status);
}
process.stderr.write('fake-claude: no Python interpreter found (tried py, python3, python)\n');
process.exit(1);
