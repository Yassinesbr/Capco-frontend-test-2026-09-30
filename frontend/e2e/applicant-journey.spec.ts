import { randomUUID } from 'node:crypto'

import { expect, test, type Page } from '@playwright/test'

const password = 'Example!234'
const personal = {
  Name: 'Browser Applicant',
  'Date of birth': '1990-01-02',
  Country: 'UK',
  Nationality: 'British',
  Email: 'contact@example.test',
  Phone: '+44123456789',
}
const identity = {
  'Document type': 'Passport',
  'Document number': 'P123456',
  'Document country': 'UK',
  Expiry: '2035-01-02',
  Street: '1 Main Street',
  City: 'London',
  'Postal code': 'AB1',
  'Residential country': 'UK',
}

async function signIn(page: Page, email: string) {
  await page.goto('/sign-in')
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL(/\/applications\//)
}

async function startApplicant(page: Page) {
  const email = `browser-${randomUUID()}@example.test`
  const response = await page.request.post('/api/v1/applicant-accounts', {
    headers: {
      'Content-Type': 'application/vnd.api+json',
      Accept: 'application/vnd.api+json',
    },
    data: {
      data: { type: 'applicant-accounts', attributes: { email, password } },
    },
  })
  expect(response.status()).toBe(201)
  await signIn(page, email)
  await page.getByRole('button', { name: 'Start application' }).click()
  await expect(page).toHaveURL(/\/personal-details$/)
  return email
}

async function fillAnswers(page: Page, answers: Record<string, string>) {
  for (const [label, value] of Object.entries(answers)) {
    await page.getByLabel(`${label} (required)`, { exact: true }).fill(value)
  }
}

async function expectAnswers(page: Page, answers: Record<string, string>) {
  for (const [label, value] of Object.entries(answers)) {
    await expect(
      page.getByLabel(`${label} (required)`, { exact: true }),
    ).toHaveValue(value)
  }
}

test('registers without login and restores both steps and evidence after logout, login and refresh', async ({
  page,
  context,
}) => {
  const email = `journey-${randomUUID()}@example.test`
  await page.goto('/create-account')
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByLabel('Confirm password').fill(password)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('status')).toContainText(
    'Your account has been created',
  )
  await expect(page).toHaveURL(/\/create-account$/)
  expect(
    (await context.cookies()).some(
      (cookie) => cookie.name === '__Host-KYCSESSION',
    ),
  ).toBe(false)
  await page.getByRole('link', { name: 'Sign in', exact: true }).click()
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.getByRole('button', { name: 'Start application' }).click()
  await expect(
    page.getByRole('heading', { name: 'Personal details', exact: true }),
  ).toBeFocused()
  await fillAnswers(page, personal)
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: 'Save and continue' }).click()
  await expect(page).toHaveURL(/\/identity-and-address$/)
  await fillAnswers(page, identity)
  await page
    .getByLabel('Document evidence (JPG or PNG, up to 10 MiB)')
    .setInputFiles({
      name: 'identity.png',
      mimeType: 'image/png',
      buffer: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5q0AAAAASUVORK5CYII=',
        'base64',
      ),
    })
  await page.getByRole('button', { name: 'Upload document' }).click()
  await expect(page.getByRole('status')).toContainText(
    'Document evidence saved',
  )
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('status')).toContainText(
    'Your answers have been saved',
  )
  const identityUrl = page.url()
  await page.getByRole('button', { name: 'Sign out', exact: true }).click()
  await expect(page).toHaveURL(/\/sign-in$/)
  await signIn(page, email)
  await expect(page).toHaveURL(identityUrl)
  await expectAnswers(page, identity)
  await expect(
    page.getByText('Document evidence is saved.', { exact: false }),
  ).toBeVisible()
  await page.reload()
  await expectAnswers(page, identity)
  await page.getByRole('button', { name: 'Back', exact: true }).click()
  await expectAnswers(page, personal)
  await expect(page.getByRole('checkbox')).toBeChecked()
  await page.reload()
  await expectAnswers(page, personal)
})

test('protects edits on navigation, browser back, refresh and logout with accessible keyboard choices', async ({
  page,
}) => {
  await startApplicant(page)
  await page
    .getByLabel('Name (required)', { exact: true })
    .fill('Unsaved applicant')
  const formUrl = page.url()
  await page.getByRole('button', { name: 'Back', exact: true }).click()
  const dialog = page.getByRole('alertdialog', {
    name: 'Leave without saving?',
  })
  await expect(dialog).toBeVisible()
  const stay = dialog.getByRole('button', { name: 'Stay and keep editing' })
  await expect(stay).toBeFocused()
  await page.keyboard.press('Shift+Tab')
  await expect(
    dialog.getByRole('button', { name: 'Leave without saving', exact: true }),
  ).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(stay).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
  await expect(
    page.getByRole('button', { name: 'Back', exact: true }),
  ).toBeFocused()
  await expect(page.getByLabel('Name (required)', { exact: true })).toHaveValue(
    'Unsaved applicant',
  )
  await page.evaluate(() => {
    window.history.back()
  })
  await expect(dialog).toBeVisible()
  await stay.click()
  await expect(page).toHaveURL(formUrl)
  const dialogEvent = page.waitForEvent('dialog')
  const reload = page.evaluate(() => {
    window.location.reload()
  })
  const browserDialog = await dialogEvent
  expect(browserDialog.type()).toBe('beforeunload')
  await browserDialog.dismiss()
  await reload
  await expect(page.getByLabel('Name (required)', { exact: true })).toHaveValue(
    'Unsaved applicant',
  )
  let logoutRequests = 0
  page.on('request', (request) => {
    if (request.method() === 'DELETE') logoutRequests++
  })
  await page.getByRole('button', { name: 'Sign out', exact: true }).click()
  await expect(dialog).toBeVisible()
  expect(logoutRequests).toBe(0)
  await dialog.getByRole('button', { name: 'Sign out without saving' }).click()
  await expect(page).toHaveURL(/\/sign-in$/)
  expect(logoutRequests).toBe(1)
})

test('keeps answers after failed and expired-session saves and clears the warning only after confirmed save', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await startApplicant(page)
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true)
  await page
    .getByLabel('Name (required)', { exact: true })
    .fill('Preserved applicant')
  const endpoint = '**/api/v1/applicant-applications/*/form'
  await page.route(endpoint, async (route) => {
    if (route.request().method() === 'PATCH')
      await route.fulfill({ status: 500, body: '{}' })
    else await route.continue()
  })
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('could not complete')
  await expect(page.getByLabel('Name (required)', { exact: true })).toHaveValue(
    'Preserved applicant',
  )
  await expect(
    page.getByText('Your answers have been saved.'),
  ).not.toBeVisible()
  await page.getByRole('button', { name: 'Back', exact: true }).click()
  await page.getByRole('button', { name: 'Stay and keep editing' }).click()
  await page.unroute(endpoint)
  await page.route(endpoint, async (route) => {
    if (route.request().method() === 'PATCH')
      await route.fulfill({ status: 401, body: '{}' })
    else await route.continue()
  })
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('session has expired')
  await expect(
    page.getByRole('link', { name: 'Sign in in a new tab, then retry here' }),
  ).toHaveAttribute('target', '_blank')
  await page.unroute(endpoint)
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('status')).toContainText(
    'Your answers have been saved',
  )
  await page.getByRole('button', { name: 'Back', exact: true }).click()
  await expect(page).toHaveURL(/\/current$/)
  await expect(page.getByRole('alertdialog')).not.toBeVisible()
})

test('exposes validation links, route focus and a keyboard skip link', async ({
  page,
}) => {
  await page.goto('/sign-in')
  await expect(
    page.getByRole('heading', { name: 'Welcome back' }),
  ).toBeFocused()
  await page.keyboard.press('Shift+Tab')
  await expect(
    page.getByRole('link', { name: 'Skip to main content' }),
  ).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('main')).toBeFocused()
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByLabel('Email address')).toBeFocused()
  await page.getByRole('link', { name: 'Check Password', exact: true }).click()
  await expect(page.getByLabel('Password', { exact: true })).toBeFocused()
  await expect(page.getByLabel('Password', { exact: true })).toHaveAttribute(
    'aria-invalid',
    'true',
  )
})
