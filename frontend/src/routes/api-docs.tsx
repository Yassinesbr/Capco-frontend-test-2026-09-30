import { Link, useLoaderData } from 'react-router'

import type { default as SwaggerUIComponent } from 'swagger-ui-react'
import 'swagger-ui-react/swagger-ui.css'
import './api-docs.css'

export async function clientLoader() {
  const { default: SwaggerUI } = await import('swagger-ui-react')
  return { SwaggerUI }
}

export default function ApiDocumentationRoute() {
  const { SwaggerUI } = useLoaderData<typeof clientLoader>() as {
    SwaggerUI: typeof SwaggerUIComponent
  }

  return (
    <main className="api-docs">
      <header className="api-docs__header">
        <Link to="/">KYC Workspace</Link>
        <h1>API documentation</h1>
        <p>Explore the KYC API. Requests are disabled in this prototype.</p>
        <a href="/openapi.json">OpenAPI JSON</a>
      </header>
      <div className="api-docs__swagger">
        <SwaggerUI
          url="/openapi.json"
          docExpansion="list"
          displayOperationId
          supportedSubmitMethods={[]}
        />
      </div>
    </main>
  )
}
