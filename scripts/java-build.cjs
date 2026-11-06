const { spawnSync } = require('node:child_process');
const options = { stdio: 'inherit', cwd: require('node:path').resolve(__dirname, '..') };
const result = process.platform === 'win32'
  ? spawnSync(process.env.ComSpec || 'cmd.exe', ['/d', '/s', '/c', 'mvn.cmd verify spotless:check'], options)
  : spawnSync('mvn', ['verify', 'spotless:check'], options);
if (result.error) console.error('Build Java indisponível: instale JDK 21 e Maven 3.9+.');
process.exit(result.status ?? 1);
