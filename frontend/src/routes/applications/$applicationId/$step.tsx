import { Alert, Button, Card, CardContent, Stack, Typography } from '@kyc/ui'
import { useQuery } from '@tanstack/react-query'
import { Navigate, useNavigate } from 'react-router'
import { z } from 'zod'

import { formKey, getForm } from '../-application-api'
import { ApplicationError } from '../-application-error'
import { ApplicationForm } from '../../-application-form'
import styles from '../../application-step.module.css'

import type { Route } from './+types/$step'

export default function CurrentApplicationStepPage({
  params,
}: Route.ComponentProps) {
  const navigate = useNavigate()
  if (!z.uuid().safeParse(params.applicationId).success)
    return <Navigate to="/applications/current" replace />
  if (params.step === 'review') {
    return (
      <main className={styles.reviewPage}>
        <Card className={styles.reviewCard}>
          <CardContent>
            <Stack spacing={2}>
              <Typography component="h1" variant="heading">
                Review application
              </Typography>
              <Typography>Your application is ready for review.</Typography>
              <label>
                <input type="checkbox" /> I confirm the information is complete
                and accurate.
              </label>
              <Button
                onClick={() =>
                  void navigate(
                    `/applications/${params.applicationId}/submitted`,
                  )
                }
              >
                Submit
              </Button>
              <Button
                variant="secondary"
                onClick={() =>
                  void navigate(
                    `/applications/${params.applicationId}/identity-and-address`,
                  )
                }
              >
                Return to form
              </Button>
            </Stack>
          </CardContent>
        </Card>
      </main>
    )
  }

  if (
    params.step !== 'personal-details' &&
    params.step !== 'identity-and-address'
  ) {
    return (
      <main>
        <Typography component="h1" variant="heading">
          Step not found
        </Typography>
      </main>
    )
  }
  return (
    <SavedStep
      key={`${params.applicationId}/${params.step}`}
      id={params.applicationId}
      step={params.step}
      onNavigate={(href) => {
        void navigate(href)
      }}
    />
  )
}

function SavedStep({
  id,
  step,
  onNavigate,
}: {
  id: string
  step: 'personal-details' | 'identity-and-address'
  onNavigate: (href: string) => void
}) {
  const query = useQuery({
    queryKey: formKey(id),
    queryFn: () => getForm(id),
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })
  if (query.isPending || query.isFetching)
    return (
      <main>
        <Alert role="status">Loading saved answers...</Alert>
      </main>
    )
  if (query.isError)
    return (
      <main>
        <Typography component="h1" variant="heading">
          Application form
        </Typography>
        <ApplicationError
          error={query.error}
          onRetry={() => {
            void query.refetch()
          }}
        />
      </main>
    )
  return (
    <ApplicationForm
      initialForm={query.data}
      step={step}
      onNavigate={onNavigate}
    />
  )
}
