import { index, route, type RouteConfig } from '@react-router/dev/routes'

export default [
  index('routes/index.tsx'),
  route('api/docs', 'routes/api-docs-redirect.tsx'),
  route('api-docs', 'routes/api-docs.tsx'),
  route('create-account', 'routes/create-account.tsx'),
  route('sign-in', 'routes/sign-in.tsx'),
  route('applications', 'routes/applications.tsx', [
    route('current', 'routes/applications/current.tsx'),
    route(
      ':applicationId/submitted',
      'routes/applications/$applicationId.submitted.tsx',
    ),
    route(
      ':applicationId/:step',
      'routes/applications/$applicationId/$step.tsx',
    ),
  ]),
  route('reviewer/applications', 'routes/reviewer/applications.tsx'),
] satisfies RouteConfig
