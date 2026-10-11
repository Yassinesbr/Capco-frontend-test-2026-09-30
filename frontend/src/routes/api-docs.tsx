import { Alert } from '@kyc/ui'
import { lazy, Suspense } from 'react'
import { Link } from 'react-router'

import 'swagger-ui-react/swagger-ui.css'
import './api-docs.css'

const SwaggerUI = lazy(() => import('swagger-ui-react'))

export default function ApiDocumentationRoute() {
  return (
    <main className="api-docs">
      <header className="api-docs__header">
        <Link to="/">KYC Workspace</Link>
        <h1>API documentation</h1>
        <p>Explore the KYC API. Requests are disabled in this prototype.</p>
        <a href="/openapi.json">OpenAPI JSON</a>
      </header>
      <div className="api-docs__swagger">
        <Suspense
          fallback={<Alert role="status">Loading API documentation...</Alert>}
        >
          <SwaggerUI
            url="/openapi.json"
            docExpansion="list"
            displayOperationId
            supportedSubmitMethods={[]}
          />
        </Suspense>
      </div>
    </main>
  )
}
