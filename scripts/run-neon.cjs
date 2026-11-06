const { spawn } = require('node:child_process');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const bootstrap = process.argv.includes('--bootstrap');
const verify = process.argv.includes('--verify');
const args = bootstrap || verify
  ? ['-Dloader.main=br.com.financa.tools.' + (bootstrap ? 'NeonBootstrap' : 'NeonVerify'), '-cp', 'target/controle-financeiro-1.0.0.jar', 'org.springframework.boot.loader.launch.PropertiesLauncher']
  : ['-jar', 'target/controle-financeiro-1.0.0.jar', '--spring.profiles.active=neon', '--server.address=127.0.0.1', '--server.port=' + (process.env.PORT || '8083'), '--app.jobs-enabled=false'];
const child = spawn('java', args, { cwd: root, stdio: 'inherit', env: process.env });
child.on('error', () => { console.error('Não foi possível iniciar Java. Execute npm run build primeiro.'); process.exitCode = 1; });
child.on('exit', code => { process.exitCode = code ?? 1; });
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => child.kill(signal));
