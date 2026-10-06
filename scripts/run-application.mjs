import { existsSync, mkdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { spawn, spawnSync } from 'node:child_process';

const rootDirectory = dirname(dirname(fileURLToPath(import.meta.url)));
const backendDirectory = join(rootDirectory, 'backend');
const frontendDirectory = join(rootDirectory, 'frontend');
const backendDataDirectory = join(backendDirectory, 'data');
const mavenWrapper = join(backendDirectory, 'mvnw.cmd');
function resolveViteCli(directory) {
  const packageJsonPath = join(directory, 'node_modules', 'vite', 'package.json');
  if (!existsSync(packageJsonPath)) return undefined;

  const packageJson = JSON.parse(readFileSync(packageJsonPath, 'utf8'));
  const binary = typeof packageJson.bin === 'string' ? packageJson.bin : packageJson.bin?.vite;
  return binary ? join(dirname(packageJsonPath), binary) : undefined;
}

const viteCli = [resolveViteCli(frontendDirectory), resolveViteCli(rootDirectory)]
  .find((candidate) => candidate && existsSync(candidate));

if (process.argv.includes('--help') || process.argv.includes('-h')) {
  console.log('Starts the Spring Boot API and Vite development server.');
  console.log('Usage: npm run dev:app');
  console.log('Requires JAVA_HOME to point to a JDK.');
  process.exit(0);
}

function readPersistentUserJavaHome() {
  if (process.platform !== 'win32') return undefined;

  const query = spawnSync('reg.exe', ['query', 'HKCU\\Environment', '/v', 'JAVA_HOME'], {
    encoding: 'utf8',
    windowsHide: true
  });
  if (query.status !== 0) return undefined;

  const match = query.stdout.match(/^\s*JAVA_HOME\s+REG_\w+\s+(.+?)\s*$/mi);
  return match?.[1].replace(/%([^%]+)%/g, (_, variableName) => process.env[variableName] ?? `%${variableName}%`);
}

const inheritedJavaHome = process.env.JAVA_HOME;
const javaHome = inheritedJavaHome ?? readPersistentUserJavaHome();
const javaExecutable = javaHome && join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');

if (!javaHome) {
  console.error('JAVA_HOME is not set in this terminal or in the per-user Windows environment.');
  console.error('Example: $env:JAVA_HOME = \'C:\\path\\to\\jdk\'');
  process.exit(1);
}

if (!existsSync(javaExecutable)) {
  const source = inheritedJavaHome ? 'this terminal' : 'the per-user Windows environment';
  console.error(`JAVA_HOME from ${source} does not point to a JDK: ${javaHome}`);
  process.exit(1);
}

process.env.JAVA_HOME = javaHome;

if (!existsSync(mavenWrapper)) {
  console.error(`Maven Wrapper was not found at ${mavenWrapper}.`);
  process.exit(1);
}

if (!viteCli) {
  console.error('Frontend dependencies are missing. Run npm ci from the repository root first.');
  process.exit(1);
}

mkdirSync(backendDataDirectory, { recursive: true });

const children = new Set();
let stopping = false;

function start(name, command, arguments_, options) {
  const child = spawn(command, arguments_, {
    ...options,
    env: process.env,
    stdio: 'inherit'
  });

  children.add(child);
  child.on('error', (error) => {
    console.error(`${name} could not be started: ${error.message}`);
    shutdown(1);
  });
  child.on('exit', (code, signal) => {
    children.delete(child);
    if (!stopping) {
      console.error(`${name} stopped unexpectedly (${signal ?? `exit code ${code ?? 1}`}).`);
      shutdown(code ?? 1);
    }
  });
  return child;
}

async function stopChild(child) {
  if (!child.pid) return;

  if (process.platform === 'win32') {
    await new Promise((resolve) => {
      const taskkill = spawn('taskkill.exe', ['/pid', String(child.pid), '/T', '/F'], {
        stdio: 'ignore',
        windowsHide: true
      });
      taskkill.on('close', resolve);
      taskkill.on('error', resolve);
    });
    return;
  }

  child.kill('SIGTERM');
}

async function shutdown(exitCode = 0) {
  if (stopping) return;
  stopping = true;
  await Promise.all([...children].map(stopChild));
  process.exit(exitCode);
}

process.on('SIGINT', () => shutdown());
process.on('SIGTERM', () => shutdown());

console.log('Starting API at http://127.0.0.1:8080 and frontend at http://127.0.0.1:3000.');

if (process.platform === 'win32') {
  const escapedMavenWrapper = mavenWrapper.replaceAll("'", "''");
  start('Backend', 'powershell.exe', [
    '-NoProfile',
    '-ExecutionPolicy',
    'Bypass',
    '-Command',
    `& '${escapedMavenWrapper}' spring-boot:run`
  ], {
    cwd: backendDirectory,
    windowsHide: false
  });
} else {
  start('Backend', join(backendDirectory, 'mvnw'), ['spring-boot:run'], { cwd: backendDirectory });
}

start('Frontend', process.execPath, [viteCli, '--port', '3000'], { cwd: frontendDirectory });
