import { Alert, Box, Button, Stack, TextField, Typography } from '@kyc/ui'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'

import styles from './application-step.module.css'
import {
  ApplicationApiError,
  currentDraftKey,
  formKey,
  getForm,
  saveForm,
  uploadEvidence,
  type Answers,
  type ApplicationStep,
  type SavedForm,
} from './applications/-application-api'
import { ApplicationError } from './applications/-application-error'

const fields = {
  'personal-details': [
    ['name', 'Name'],
    ['dateOfBirth', 'Date of birth'],
    ['country', 'Country'],
    ['nationality', 'Nationality'],
    ['email', 'Email'],
    ['phone', 'Phone'],
  ],
  'identity-and-address': [
    ['documentType', 'Document type'],
    ['documentNumber', 'Document number'],
    ['documentCountry', 'Document country'],
    ['expiry', 'Expiry'],
    ['street', 'Street'],
    ['city', 'City'],
    ['postal', 'Postal code'],
    ['residentialCountry', 'Residential country'],
  ],
} as const

type Action =
  | { kind: 'save'; advance: boolean }
  | { kind: 'upload'; file: File }
  | { kind: 'reload' }

export function ApplicationForm({
  initialForm,
  step,
  onNavigate,
}: {
  initialForm: SavedForm
  step: ApplicationStep
  onNavigate: (href: string) => void
}) {
  const [answers, setAnswers] = useState<Answers>({
    ...initialForm.attributes.answers,
  })
  const [version, setVersion] = useState(initialForm.attributes.version)
  const [evidencePresent, setEvidencePresent] = useState(
    initialForm.attributes.documentEvidence.present,
  )
  const [file, setFile] = useState<File>()
  const [saved, setSaved] = useState(false)
  const [notice, setNotice] = useState<string>()
  const busy = useRef(false)
  const fileInput = useRef<HTMLInputElement>(null)
  const client = useQueryClient()
  const id = initialForm.id
  const operation = useMutation({
    retry: false,
    mutationFn: async (action: Action) => {
      if (action.kind === 'reload') return getForm(id)
      if (action.kind === 'upload') return uploadEvidence(id, action.file)
      const selected: Answers = Object.fromEntries(
        fields[step].map(([key]) => [key, answers[key] ?? '']),
      )
      if (step === 'personal-details')
        selected.consentConfirmed = answers.consentConfirmed === true
      return saveForm(id, step, selected, version)
    },
  })

  async function run(action: Action) {
    if (busy.current) return
    busy.current = true
    setSaved(false)
    setNotice(undefined)
    try {
      const form = await operation.mutateAsync(action)
      client.setQueryData(formKey(id), form)
      void client.invalidateQueries({ queryKey: currentDraftKey })
      setVersion(form.attributes.version)
      setEvidencePresent(form.attributes.documentEvidence.present)
      if (action.kind === 'reload') {
        setNotice(
          'Latest saved version loaded. Your entered answers are unchanged. Saving again will replace this step with your answers.',
        )
      } else if (action.kind === 'upload') {
        setFile(undefined)
        if (fileInput.current) fileInput.current.value = ''
        setNotice('Document evidence saved. Save your answers separately.')
      } else {
        setAnswers({ ...form.attributes.answers })
        setSaved(true)
        if (action.advance)
          onNavigate(
            `/applications/${id}/${step === 'personal-details' ? 'identity-and-address' : 'review'}`,
          )
      }
    } catch {
      /* The mutation retains the error; the editor retains all entered values. */
    } finally {
      busy.current = false
    }
  }

  const change = (key: string, value: string | boolean) => {
    setAnswers((current) => ({ ...current, [key]: value }))
    setSaved(false)
    setNotice(undefined)
    operation.reset()
  }
  const conflict =
    operation.error instanceof ApplicationApiError &&
    operation.error.status === 409
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
            Save your answers at any time, including an incomplete form. Saved
            answers will be available when you return.
          </Typography>
          <Stack
            component="form"
            className={styles.stepForm}
            aria-busy={operation.isPending}
            onSubmit={(event) => {
              event.preventDefault()
              void run({ kind: 'save', advance: false })
            }}
            spacing={2}
          >
            {fields[step].map(([key, label]) => (
              <TextField
                key={key}
                id={`kyc-${key}`}
                name={key}
                label={`${label} (required)`}
                helperText={
                  key === 'dateOfBirth' || key === 'expiry'
                    ? 'Use YYYY-MM-DD.'
                    : undefined
                }
                type="text"
                fullWidth
                required
                readOnly={operation.isPending}
                value={typeof answers[key] === 'string' ? answers[key] : ''}
                onChange={(event) => change(key, event.target.value)}
              />
            ))}
            {step === 'personal-details' ? (
              <label>
                <input
                  type="checkbox"
                  name="consentConfirmed"
                  checked={answers.consentConfirmed === true}
                  disabled={operation.isPending}
                  onChange={(event) =>
                    change('consentConfirmed', event.target.checked)
                  }
                />{' '}
                I confirm these details are accurate and belong to me.
              </label>
            ) : (
              <Stack spacing={1}>
                <Typography>
                  {evidencePresent
                    ? 'Document evidence is saved. You may upload a replacement.'
                    : 'No document evidence has been saved yet.'}
                </Typography>
                <label htmlFor="document-evidence">
                  Document evidence (JPG or PNG, up to 10 MiB)
                </label>
                <input
                  ref={fileInput}
                  id="document-evidence"
                  type="file"
                  accept="image/jpeg,image/png"
                  disabled={operation.isPending}
                  onChange={(event) => {
                    setFile(event.target.files?.[0])
                    setNotice(undefined)
                    operation.reset()
                  }}
                />
                {file ? (
                  <Typography>
                    Selected file has not been uploaded yet.
                  </Typography>
                ) : null}
                <Button
                  type="button"
                  variant="secondary"
                  disabled={!file || operation.isPending}
                  onClick={() => {
                    if (file) void run({ kind: 'upload', file })
                  }}
                >
                  Upload document
                </Button>
              </Stack>
            )}
            {operation.isPending ? (
              <Alert role="status">Saving or loading your application...</Alert>
            ) : null}
            {operation.isError ? (
              <ApplicationError error={operation.error} keepEdits />
            ) : null}
            {conflict ? (
              <Button
                type="button"
                variant="secondary"
                onClick={() => {
                  void run({ kind: 'reload' })
                }}
              >
                Load latest version and keep my edits
              </Button>
            ) : null}
            {saved ? (
              <Alert role="status" severity="success">
                Your answers have been saved.
              </Alert>
            ) : null}
            {notice ? <Alert role="status">{notice}</Alert> : null}
            <Stack
              className={styles.formActions}
              direction={{ xs: 'column', sm: 'row' }}
              spacing={2}
            >
              <Button
                type="button"
                variant="quiet"
                disabled={operation.isPending}
                onClick={() =>
                  onNavigate(
                    step === 'personal-details'
                      ? '/applications/current'
                      : `/applications/${id}/personal-details`,
                  )
                }
              >
                Back
              </Button>
              <Button type="submit" disabled={operation.isPending}>
                Save
              </Button>
              <Button
                type="button"
                variant="secondary"
                disabled={operation.isPending}
                onClick={() => {
                  void run({ kind: 'save', advance: true })
                }}
              >
                {step === 'personal-details'
                  ? 'Save and continue'
                  : 'Save and review'}
              </Button>
            </Stack>
          </Stack>
        </Stack>
      </Box>
    </main>
  )
}
