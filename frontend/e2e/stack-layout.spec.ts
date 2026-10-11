import { expect, test } from '@playwright/test'

test('application form lays out actions without forwarding style props to the DOM', async ({
  page,
}) => {
  const reactWarnings: string[] = []
  page.on('console', (message) => {
    if (
      message.type() === 'error' &&
      message.text().includes('justifyContent')
    ) {
      reactWarnings.push(message.text())
    }
  })

  await page.route('**/api/v1/applicant-applications/*/form', (route) =>
    route.fulfill({
      json: {
        data: {
          type: 'applicant-application-forms',
          id: '00000000-0000-4000-8000-000000000001',
          attributes: {
            status: 'draft',
            currentStep: 'personal-details',
            steps: [],
            answers: {},
            documentEvidence: { present: false },
            version: 0,
          },
        },
      },
    }),
  )
  await page.goto(
    '/applications/00000000-0000-4000-8000-000000000001/personal-details',
  )

  const actions = page
    .getByRole('button', { name: 'Save and continue' })
    .locator('xpath=..')
  await expect(actions).toHaveCSS('justify-content', 'space-between')
  expect(reactWarnings).toEqual([])
})
