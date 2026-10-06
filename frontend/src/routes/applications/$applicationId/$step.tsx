import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Stack,
  TextField,
  Typography,
} from '@kyc/ui'
import { useState } from 'react'
import { useNavigate } from 'react-router'

import styles from '../../application-step.module.css'

import type { Route } from './+types/$step'

const fields = {
  'personal-details': [
    'Name',
    'Date of birth',
    'Country',
    'Nationality',
    'Email',
    'Phone',
  ],
  'identity-and-address': [
    'Document type',
    'Document number',
    'Document country',
    'Expiry',
    'Street',
    'City',
    'Postal code',
    'Residential country',
  ],
} as const

export default function CurrentApplicationStepPage({
  params,
}: Route.ComponentProps) {
  const navigate = useNavigate()
  const step =
    params.step === 'identity-and-address'
      ? 'identity-and-address'
      : 'personal-details'
  const [missing, setMissing] = useState(false)
  const [saved, setSaved] = useState(false)
  const advance = () => {
    const form = document.querySelector('form')
    if (form && !form.reportValidity()) return
    setMissing(false)
    setSaved(true)
    if (step === 'personal-details')
      void navigate(
        `/applications/${params.applicationId}/identity-and-address`,
      )
    else void navigate(`/applications/${params.applicationId}/review`)
  }

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

  return (
    <main className={styles.page}>
      <Box component="div" className={styles.stepContent}>
        <Stack spacing={3}>
          <Typography component="p" variant="eyebrow" tone="accent">
            STEP {step === 'personal-details' ? '1' : '2'} OF 2
          </Typography>
          <Typography component="h1" variant="heading">
            {step === 'personal-details'
              ? 'Personal details'
              : 'Identity and address'}
          </Typography>
          <Typography tone="muted">
            Complete this form as part of the frontend implementation exercise.
          </Typography>
          <Stack
            component="form"
            className={styles.stepForm}
            onSubmit={(event) => {
              event.preventDefault()
              advance()
            }}
            spacing={2}
          >
            {fields[step].map((label) => (
              <TextField key={label} label={`${label} (required)`} required />
            ))}
            {step === 'personal-details' ? (
              <label>
                <input required type="checkbox" /> I confirm these details are
                accurate and belong to me.
              </label>
            ) : (
              <>
                <label htmlFor="document-evidence">
                  Document evidence (required)
                </label>
                <input
                  id="document-evidence"
                  type="file"
                  accept="image/jpeg,image/png"
                  required
                />
              </>
            )}
            {missing ? (
              <Alert role="alert" severity="error">
                Complete all required fields before continuing.
              </Alert>
            ) : null}
            {saved ? (
              <Alert role="status" severity="success">
                Your progress was saved in this prototype.
              </Alert>
            ) : null}
            <Stack
              component="div"
              className={styles.formActions}
              direction="row"
              spacing={2}
            >
              <Button
                type="button"
                variant="quiet"
                onClick={() =>
                  step === 'personal-details'
                    ? void navigate('/applications/current')
                    : void navigate(
                        `/applications/${params.applicationId}/personal-details`,
                      )
                }
              >
                Back
              </Button>
              <Button type="submit">
                {step === 'personal-details' ? 'Continue' : 'Save and review'}
              </Button>
            </Stack>
          </Stack>
        </Stack>
      </Box>
    </main>
  )
}
