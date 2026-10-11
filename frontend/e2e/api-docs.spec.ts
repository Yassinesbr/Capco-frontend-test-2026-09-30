import { expect, test } from '@playwright/test'

test('API docs URL renders Swagger UI and its OpenAPI document', async ({
  page,
}) => {
  // Await both operations together so a failed navigation cannot leave a
  // response waiter rejecting after the test ends. Cold Swagger loads can
  // trigger Vite dependency optimization before requesting the contract.
  const [openApiResponse] = await Promise.all([
    page.waitForResponse(
      (response) => response.url().endsWith('/openapi.json'),
      { timeout: 20_000 },
    ),
    page.goto('/api/docs'),
  ])
  await expect(page).toHaveURL(/\/api-docs$/)

  await expect(
    page.getByRole('heading', { name: 'API documentation' }),
  ).toBeVisible()
  await expect(page.locator('.swagger-ui .info .title')).toContainText('KYC')
  expect(openApiResponse.ok()).toBeTruthy()
})
