import { Alert, Button, Stack, Typography } from '@kyc/ui'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Outlet, useNavigate } from 'react-router'

import { useUnsavedChanges } from './-unsaved-changes'
import { signOut } from './applications/-application-api'

export default function ApplicationsLayout() {
  const client = useQueryClient()
  const navigate = useNavigate()
  const { requestLeave, setDirty } = useUnsavedChanges()
  const logout = useMutation({
    mutationFn: signOut,
    retry: false,
    onSuccess: () => {
      setDirty(false)
      client.clear()
      void navigate('/sign-in')
    },
  })
  return (
    <>
      <Stack
        component="header"
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1}
      >
        <Typography variant="caption">
          Save your answers before signing out or leaving a step.
        </Typography>
        <Button
          variant="quiet"
          disabled={logout.isPending}
          onClick={() => {
            requestLeave(() => logout.mutate())
          }}
        >
          {logout.isPending ? 'Signing out...' : 'Sign out'}
        </Button>
        {logout.isError ? (
          <Alert role="alert" severity="error">
            {logout.error.message}
          </Alert>
        ) : null}
      </Stack>
      <Outlet />
    </>
  )
}
