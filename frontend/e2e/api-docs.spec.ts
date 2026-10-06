import { expect, test } from '@playwright/test'

test('API docs URL renders Swagger UI and its OpenAPI document', async ({
  page,
}) => {
  const openApiResponse = page.waitForResponse((response) =>
    response.url().endsWith('/openapi.json'),
  )
  await page.goto('/api/docs')

  await expect(
    page.getByRole('heading', { name: 'API documentation' }),
  ).toBeVisible()
  await expect(page.locator('.swagger-ui .info .title')).toContainText('KYC')
  expect((await openApiResponse).ok()).toBeTruthy()
})
