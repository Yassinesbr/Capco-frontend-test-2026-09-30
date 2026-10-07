import { Alert, Button, Stack, Typography } from '@kyc/ui'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef } from 'react'
import { useNavigate } from 'react-router'

import { ApplicantJourneyPresentation } from './-applicant-journey-presentation'
import {
  currentDraftKey,
  getCurrentDraft,
  startDraft,
} from './-application-api'
import { ApplicationError } from './-application-error'

export default function CurrentApplicationJourneyPage() {
  const navigate = useNavigate()
  const client = useQueryClient()
  const starting = useRef(false)
  const draft = useQuery({
    queryKey: currentDraftKey,
    queryFn: getCurrentDraft,
    retry: false,
    refetchOnWindowFocus: false,
  })
  const start = useMutation({ mutationFn: startDraft, retry: false })
  const open = async () => {
    if (starting.current) return
    starting.current = true
    try {
      const application = draft.data ?? (await start.mutateAsync())
      client.setQueryData(currentDraftKey, application)
      void navigate(
        `/applications/${application.id}/${application.attributes.currentStep}`,
      )
    } catch {
      /* The mutation exposes an accessible error below. */
    } finally {
      starting.current = false
    }
  }
  if (draft.isPending)
    return (
      <main>
        <Alert role="status">Loading your application...</Alert>
      </main>
    )
  if (draft.isError)
    return (
      <main>
        <Typography component="h1" variant="heading">
          Your application
        </Typography>
        <ApplicationError
          error={draft.error}
          onRetry={() => {
            void draft.refetch()
          }}
        />
      </main>
    )
  return (
    <Stack spacing={2}>
      {start.isError ? (
        <ApplicationError
          error={start.error}
          onRetry={() => {
            void open()
          }}
        />
      ) : null}
      {start.isPending ? (
        <Alert role="status">Starting your application...</Alert>
      ) : (
        <ApplicantJourneyPresentation
          entryState={draft.data ? 'resume' : 'start'}
          progress={
            draft.data
              ? draft.data.attributes.progress.map(({ step, state }) => ({
                  step,
                  state: state === 'not-started' ? 'remaining' : state,
                }))
              : [
                  { step: 'personal-details', state: 'current' },
                  { step: 'identity-and-address', state: 'remaining' },
                ]
          }
          onPrimaryAction={() => {
            void open()
          }}
        />
      )}
      {draft.data ? (
        <Button
          variant="quiet"
          onClick={() => {
            void navigate(`/applications/${draft.data?.id}/personal-details`)
          }}
        >
          Edit personal details
        </Button>
      ) : null}
    </Stack>
  )
}
