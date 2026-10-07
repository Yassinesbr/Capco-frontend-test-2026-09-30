import { Alert, Button, Link, Stack } from '@kyc/ui'

import { ApplicationApiError } from './-application-api'

export function ApplicationError({
  error,
  onRetry,
  keepEdits = false,
}: {
  error: Error
  onRetry?: () => void
  keepEdits?: boolean
}) {
  const expired =
    error instanceof ApplicationApiError && [401, 403].includes(error.status)
  return (
    <Stack spacing={1}>
      <Alert severity="error" role="alert">
        {error.message}
      </Alert>
      {expired ? (
        <Link
          href="/sign-in"
          target={keepEdits ? '_blank' : undefined}
          rel={keepEdits ? 'noopener noreferrer' : undefined}
        >
          {keepEdits ? 'Sign in in a new tab, then retry here' : 'Sign in'}
        </Link>
      ) : null}
      {onRetry ? (
        <Button type="button" variant="secondary" onClick={onRetry}>
          Retry
        </Button>
      ) : null}
    </Stack>
  )
}
