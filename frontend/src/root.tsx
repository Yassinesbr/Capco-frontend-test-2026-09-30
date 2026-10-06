import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import {
  isRouteErrorResponse,
  Links,
  Meta,
  Outlet,
  Scripts,
  ScrollRestoration,
  useRouteError,
} from 'react-router'
import './styles.css'

export function Layout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <Meta />
        <Links />
      </head>
      <body>
        {children}
        <ScrollRestoration />
        <Scripts />
      </body>
    </html>
  )
}

export default function App() {
  const [queryClient] = useState(
    () => new QueryClient({ defaultOptions: { mutations: { retry: false } } }),
  )
  return (
    <QueryClientProvider client={queryClient}>
      <Outlet />
    </QueryClientProvider>
  )
}

export function ErrorBoundary() {
  const error = useRouteError()
  const notFound = isRouteErrorResponse(error) && error.status === 404
  return (
    <main>
      <h1>{notFound ? 'Page not found' : 'Something went wrong'}</h1>
      <p>
        {notFound
          ? 'The page you requested does not exist.'
          : 'Please try again.'}
      </p>
    </main>
  )
}
