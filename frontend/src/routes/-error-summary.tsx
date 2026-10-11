import { Alert, Link } from '@kyc/ui'

import type { RefObject } from 'react'

export function ErrorSummary({
  message,
  fields,
}: {
  message?: string
  fields: {
    id: string
    label: string
    error?: string
    ref: RefObject<HTMLInputElement | null>
  }[]
}) {
  const invalid = fields.filter((field) => field.error)
  if (!message && !invalid.length) return null
  return (
    <Alert role="alert" severity="error">
      {message ?? invalid[0]?.error}
      {invalid.length > 0 ? (
        <ul>
          {invalid.map((field) => (
            <li key={field.id}>
              <Link
                href={`#${field.id}`}
                onClick={(event) => {
                  event.preventDefault()
                  field.ref.current?.focus()
                }}
              >
                Check {field.label}
              </Link>
            </li>
          ))}
        </ul>
      ) : null}
    </Alert>
  )
}
