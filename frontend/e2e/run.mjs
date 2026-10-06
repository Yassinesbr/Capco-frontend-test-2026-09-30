import { spawn, spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, rmSync } from 'node:fs'
import { createRequire } from 'node:module'
import { createConnection, createServer } from 'node:net'
import { homedir } from 'node:os'
import { dirname, resolve } from 'node:path'
import { DatabaseSync } from 'node:sqlite'

const frontendDirectory = resolve(import.meta.dirname, '..')
const backendDirectory = resolve(frontendDirectory, '..', 'backend')
const e2eDatabaseName =
  process.env.E2E_DATABASE_NAME ?? 'identity-access-e2e.db'
if (!/^[\w.-]+\.db$/i.test(e2eDatabaseName)) {
  throw new Error(
    'E2E_DATABASE_NAME must be a database filename without a path.',
  )
}
const e2eDatabase = resolve(backendDirectory, 'target', 'e2e', e2eDatabaseName)
const backendPort = Number(process.env.E2E_BACKEND_PORT ?? 18080)
const frontendPort = Number(process.env.E2E_FRONTEND_PORT ?? 13000)
const processes = []
const require = createRequire(import.meta.url)
const vitePackageJson = require('vite/package.json')
const viteBinary =
  typeof vitePackageJson.bin === 'string'
    ? vitePackageJson.bin
    : vitePackageJson.bin.vite
const viteCli = resolve(
  dirname(require.resolve('vite/package.json')),
  viteBinary,
)
const playwrightCli = resolve(
  dirname(require.resolve('@playwright/test/package.json')),
  'cli.js',
)

process.env.PLAYWRIGHT_BROWSERS_PATH ??= resolve(
  homedir(),
  '.codex-tools',
  'playwright-browsers',
)

function resolveJavaHome() {
  if (
    process.env.JAVA_HOME &&
    existsSync(
      resolve(
        process.env.JAVA_HOME,
        'bin',
        process.platform === 'win32' ? 'java.exe' : 'java',
      ),
    )
  ) {
    return process.env.JAVA_HOME
  }
  if (process.platform === 'win32') {
    const query = spawnSync(
      'reg.exe',
      ['query', 'HKCU\\Environment', '/v', 'JAVA_HOME'],
      { encoding: 'utf8', windowsHide: true },
    )
    const match =
      query.status === 0
        ? query.stdout.match(/^\s*JAVA_HOME\s+REG_\w+\s+(.+?)\s*$/im)
        : undefined
    const registeredJavaHome = match?.[1].replace(
      /%([^%]+)%/g,
      (_, name) => process.env[name] ?? `%${name}%`,
    )
    if (
      registeredJavaHome &&
      existsSync(resolve(registeredJavaHome, 'bin', 'java.exe'))
    )
      return registeredJavaHome

    const codexJdkRoot = resolve(homedir(), '.codex-tools', 'temurin-25')
    if (existsSync(resolve(codexJdkRoot, 'bin', 'java.exe')))
      return codexJdkRoot
    if (existsSync(codexJdkRoot)) {
      const installedJdk = spawnSync(
        'powershell.exe',
        [
          '-NoProfile',
          '-Command',
          `Get-ChildItem -LiteralPath '${codexJdkRoot.replaceAll("'", "''")}' -Directory | Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty FullName`,
        ],
        { encoding: 'utf8', windowsHide: true },
      )
      const discoveredJavaHome =
        installedJdk.status === 0 ? installedJdk.stdout.trim() : ''
      if (
        discoveredJavaHome &&
        existsSync(resolve(discoveredJavaHome, 'bin', 'java.exe'))
      )
        return discoveredJavaHome
    }
  }
  return undefined
}

function start(command, arguments_, options) {
  const child = spawn(command, arguments_, {
    ...options,
    stdio: 'inherit',
    windowsHide: true,
  })
  processes.push(child)
  return child
}

function assertPortAvailable(port) {
  return new Promise((resolveAvailable, reject) => {
    const server = createServer()
    server.once('error', () =>
      reject(new Error(`E2E port ${port} is already in use.`)),
    )
    server.listen({ host: '127.0.0.1', port, exclusive: true }, () => {
      server.close((error) => (error ? reject(error) : resolveAvailable()))
    })
  })
}

function waitForPort(port, process_) {
  return new Promise((resolveReady, reject) => {
    const deadline = Date.now() + 120_000
    const attempt = () => {
      if (process_.exitCode !== null) {
        reject(
          new Error(`Process for port ${port} exited before it became ready.`),
        )
        return
      }
      const socket = createConnection({ host: '127.0.0.1', port })
      socket.once('connect', () => {
        socket.end()
        resolveReady()
      })
      socket.once('error', () => {
        socket.destroy()
        if (Date.now() >= deadline) {
          reject(new Error(`Timed out waiting for port ${port}.`))
          return
        }
        setTimeout(attempt, 250)
      })
    }
    attempt()
  })
}

function exitCode(child) {
  return new Promise((resolveCode) =>
    child.once('exit', (code) => resolveCode(code ?? 1)),
  )
}

function seedReviewerFixtures() {
  const database = new DatabaseSync(e2eDatabase)
  const now = new Date()
  const ownerId = '00000000-0000-4000-8000-000000000001'
  database
    .prepare(
      'INSERT INTO applicant_accounts (id, normalized_email, password_verifier, role, created_at) VALUES (?, ?, ?, ?, ?)',
    )
    .run(
      ownerId,
      'reviewer-overview-fixture@example.test',
      'e2e-only-verifier',
      'APPLICANT',
      now.toISOString(),
    )
  const insertApplication = database.prepare(`INSERT INTO kyc_applications
    (id, applicant_account_id, lifecycle, current_step, created_at, updated_at, submitted_at, approved_at)
    VALUES (?, ?, ?, 'PERSONAL_DETAILS', ?, ?, ?, ?)`)
  const insertForm = database.prepare(
    'INSERT INTO kyc_application_forms (application_id, name, date_of_birth, updated_at) VALUES (?, ?, ?, ?)',
  )
  for (let index = 0; index < 12; index++) {
    const id = `00000000-0000-4000-8000-${String(index + 10).padStart(12, '0')}`
    const applicationOwnerId =
      index === 0
        ? ownerId
        : `00000000-0000-4000-8000-${String(index + 100).padStart(12, '0')}`
    if (index > 0) {
      database
        .prepare(
          'INSERT INTO applicant_accounts (id, normalized_email, password_verifier, role, created_at) VALUES (?, ?, ?, ?, ?)',
        )
        .run(
          applicationOwnerId,
          `reviewer-overview-${index}@example.test`,
          'e2e-only-verifier',
          'APPLICANT',
          now.toISOString(),
        )
    }
    const lifecycle =
      index === 0
        ? 'DRAFT'
        : index < 5
          ? 'SUBMITTED'
          : index < 9
            ? 'IN_REVIEW'
            : 'APPROVED'
    const submitted =
      lifecycle === 'DRAFT'
        ? null
        : new Date(now.getTime() - index * 3_600_000).toISOString()
    const approved =
      lifecycle !== 'APPROVED'
        ? null
        : new Date(
            now.getTime() - (index === 11 ? 40 : 1) * 86_400_000,
          ).toISOString()
    const created =
      submitted ?? new Date(now.getTime() - 10 * 86_400_000).toISOString()
    const updated = approved ?? submitted ?? created
    const name =
      index === 1
        ? 'Ada Lovelace'
        : `Fixture Applicant ${String(index).padStart(2, '0')}`
    insertApplication.run(
      id,
      applicationOwnerId,
      'DRAFT',
      created,
      created,
      null,
      null,
    )
    insertForm.run(id, name, '1980-01-01', updated)
    database
      .prepare(
        'UPDATE kyc_applications SET lifecycle = ?, updated_at = ?, submitted_at = ?, approved_at = ? WHERE id = ?',
      )
      .run(lifecycle, updated, submitted, approved, id)
  }
  const insertStaff = database.prepare(`INSERT INTO internal_staff_sessions
    (session_id_hash, staff_account_id, role, created_at, idle_expires_at, absolute_expires_at)
    VALUES (?, ?, ?, ?, ?, ?)`)
  const sessionTokens = {
    REVIEWER: 'e2e-reviewer-session-token',
    ADMINISTRATOR: 'e2e-administrator-session-token',
  }
  for (const [role, token] of Object.entries(sessionTokens)) {
    const hash = createHash('sha256').update(token).digest('base64url')
    insertStaff.run(
      hash,
      `00000000-0000-4000-8000-${role === 'REVIEWER' ? '000000000101' : '000000000102'}`,
      role,
      now.toISOString(),
      new Date(now.getTime() + 3_600_000).toISOString(),
      new Date(now.getTime() + 7_200_000).toISOString(),
    )
  }
  const applicantToken = 'e2e-applicant-session-token'
  database
    .prepare(
      `INSERT INTO applicant_sessions
    (session_id_hash, applicant_account_id, role, created_at, idle_expires_at, absolute_expires_at)
    VALUES (?, ?, 'APPLICANT', ?, ?, ?)`,
    )
    .run(
      createHash('sha256').update(applicantToken).digest('base64url'),
      ownerId,
      now.toISOString(),
      new Date(now.getTime() + 3_600_000).toISOString(),
      new Date(now.getTime() + 7_200_000).toISOString(),
    )
  database.close()
  return { ...sessionTokens, APPLICANT: applicantToken }
}

async function stopProcesses() {
  await Promise.all(
    processes.map(async (child) => {
      if (child.exitCode !== null || child.pid === undefined) {
        return
      }
      if (process.platform === 'win32') {
        const taskkill = spawn(
          'taskkill',
          ['/pid', String(child.pid), '/T', '/F'],
          { stdio: 'ignore', windowsHide: true },
        )
        await exitCode(taskkill)
        return
      }
      child.kill('SIGTERM')
    }),
  )
}

async function main() {
  const javaHome = resolveJavaHome()
  if (!javaHome && process.platform === 'win32') {
    throw new Error(
      'Could not find a JDK. Set JAVA_HOME or install Temurin under ~/.codex-tools/temurin-25.',
    )
  }
  if (javaHome) process.env.JAVA_HOME = javaHome
  if (
    ![backendPort, frontendPort].every(
      (port) => Number.isInteger(port) && port >= 1 && port <= 65535,
    ) ||
    backendPort === frontendPort
  ) {
    throw new Error(
      'E2E_BACKEND_PORT and E2E_FRONTEND_PORT must be different valid port numbers.',
    )
  }
  await assertPortAvailable(backendPort)
  await assertPortAvailable(frontendPort)

  mkdirSync(dirname(e2eDatabase), { recursive: true })
  rmSync(e2eDatabase, { force: true })

  const backend = start(
    process.platform === 'win32' ? 'cmd.exe' : './mvnw',
    process.platform === 'win32'
      ? ['/d', '/s', '/c', 'mvnw.cmd spring-boot:run']
      : ['spring-boot:run'],
    {
      cwd: backendDirectory,
      env: {
        ...process.env,
        SERVER_PORT: String(backendPort),
        KYC_BACKEND_URL: `http://127.0.0.1:${backendPort}`,
        SPRING_DATASOURCE_URL: `jdbc:sqlite:./target/e2e/${e2eDatabaseName}`,
        SPRING_MAIN_BANNER_MODE: 'off',
      },
    },
  )
  await waitForPort(backendPort, backend)
  const reviewerSessions = seedReviewerFixtures()

  const frontend = start(
    process.execPath,
    [
      viteCli,
      '--host',
      '0.0.0.0',
      '--port',
      String(frontendPort),
      '--strictPort',
    ],
    {
      cwd: frontendDirectory,
      env: {
        ...process.env,
        KYC_BACKEND_URL: `http://127.0.0.1:${backendPort}`,
      },
    },
  )
  await waitForPort(frontendPort, frontend)
  for (let attempt = 0; attempt < 3; attempt++) {
    await fetch(`http://127.0.0.1:${frontendPort}/reviewer/applications`).catch(
      () => undefined,
    )
  }

  const playwright = start(
    process.execPath,
    [playwrightCli, 'test', ...process.argv.slice(2)],
    {
      cwd: frontendDirectory,
      env: {
        ...process.env,
        E2E_BASE_URL: `http://localhost:${frontendPort}`,
        E2E_REVIEWER_SESSION: reviewerSessions.REVIEWER,
        E2E_ADMINISTRATOR_SESSION: reviewerSessions.ADMINISTRATOR,
        E2E_APPLICANT_SESSION: reviewerSessions.APPLICANT,
      },
    },
  )
  const result = await exitCode(playwright)
  if (result !== 0) {
    process.exitCode = result
  }
}

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, async () => {
    await stopProcesses()
    process.exit(1)
  })
}

try {
  await main()
} finally {
  await stopProcesses()
}
