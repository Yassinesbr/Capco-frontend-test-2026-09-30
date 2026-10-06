declare module 'swagger-ui-react' {
  import type { ComponentType } from 'react'

  export interface SwaggerRequest {
    credentials?: RequestCredentials
    headers?: Record<string, string>
    [key: string]: unknown
  }

  export interface SwaggerUIProps {
    url: string
    displayOperationId?: boolean
    docExpansion?: 'list' | 'full' | 'none'
    requestInterceptor?: (
      request: SwaggerRequest,
    ) => SwaggerRequest | Promise<SwaggerRequest>
    withCredentials?: boolean
    supportedSubmitMethods?: string[]
  }

  const SwaggerUI: ComponentType<SwaggerUIProps>
  export default SwaggerUI
}
