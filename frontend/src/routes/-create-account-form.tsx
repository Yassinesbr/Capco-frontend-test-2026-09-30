import {
  Alert,
  Button,
  Card,
  CardContent,
  Link,
  Stack,
  TextField,
  Typography,
} from '@kyc/ui'
import { useMutation } from '@tanstack/react-query'
import { useRef, useState } from 'react'

import {
  passwordGuidance,
  registerApplicant,
  RegistrationError,
  validateRegistration,
  type RegistrationErrors,
} from './-registration-api'
import { accountAccessHref } from './-return-target'
import styles from './account-access.module.css'

export interface CreateAccountFormProps {
  onNavigate?: (href: string) => void
  returnTo?: string
}

export function CreateAccountForm({
  onNavigate,
  returnTo,
}: CreateAccountFormProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState<string>()
  const [fieldErrors, setFieldErrors] = useState<RegistrationErrors>({})
  const [success, setSuccess] = useState(false)
  const emailInput = useRef<HTMLInputElement>(null)
  const passwordInput = useRef<HTMLInputElement>(null)
  const confirmationInput = useRef<HTMLInputElement>(null)
  const submitting = useRef(false)
  const registration = useMutation({
    mutationFn: registerApplicant,
    retry: false,
    gcTime: 0,
  })

  function showFieldErrors(errors: RegistrationErrors) {
    setFieldErrors(errors)
    if (errors.email) {
      emailInput.current?.focus()
    } else if (errors.password) {
      passwordInput.current?.focus()
    } else if (errors.confirmation) {
      confirmationInput.current?.focus()
    }
  }

  async function submit(): Promise<void> {
    if (submitting.current || success) return
    setError(undefined)
    const errors = validateRegistration(email, password, confirmation)
    showFieldErrors(errors)
    if (Object.keys(errors).length) return
    submitting.current = true
    try {
      await registration.mutateAsync({ email: email.trim(), password })
      setPassword('')
      setConfirmation('')
      setSuccess(true)
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : 'Account creation could not be confirmed. Please try again.',
      )
      if (cause instanceof RegistrationError) showFieldErrors(cause.fields)
    } finally {
      registration.reset()
      submitting.current = false
    }
  }

  return (
    <main className={styles.page}>
      <Card className={styles.card}>
        <CardContent className={styles.cardContent}>
          <Stack
            component="form"
            className={styles.form}
            onSubmit={(event) => {
              event.preventDefault()
              void submit()
            }}
            aria-busy={registration.isPending}
            spacing={3}
          >
            <Stack spacing={1}>
              <Typography component="h1" variant="heading">
                Create your account
              </Typography>
              <Typography tone="muted">
                Create credentials to begin your KYC application.
              </Typography>
            </Stack>
            {(error ??
            fieldErrors.email ??
            fieldErrors.password ??
            fieldErrors.confirmation) ? (
              <Alert aria-live="assertive" role="alert" severity="error">
                {error ??
                  fieldErrors.email ??
                  fieldErrors.password ??
                  fieldErrors.confirmation}
              </Alert>
            ) : null}
            {success ? (
              <Alert aria-live="polite" role="status" severity="success">
                Your account has been created. Sign in to begin your
                application.
              </Alert>
            ) : null}
            <TextField
              autoComplete="email"
              error={Boolean(fieldErrors.email)}
              fullWidth
              helperText={fieldErrors.email}
              id="applicant-email"
              inputRef={emailInput}
              label="Email address"
              name="email"
              onChange={(event) => {
                setEmail(event.target.value)
                setFieldErrors((current) => ({ ...current, email: undefined }))
              }}
              readOnly={registration.isPending || success}
              required
              type="email"
              value={email}
            />
            <TextField
              autoComplete="new-password"
              error={Boolean(fieldErrors.password)}
              fullWidth
              helperText={fieldErrors.password ?? passwordGuidance}
              id="applicant-password"
              inputRef={passwordInput}
              label="Password"
              name="password"
              onChange={(event) => {
                setPassword(event.target.value)
                setFieldErrors((current) => ({
                  ...current,
                  password: undefined,
                  confirmation: undefined,
                }))
              }}
              readOnly={registration.isPending || success}
              required
              type="password"
              value={password}
            />
            <TextField
              autoComplete="new-password"
              error={Boolean(fieldErrors.confirmation)}
              fullWidth
              helperText={fieldErrors.confirmation}
              id="applicant-password-confirmation"
              inputRef={confirmationInput}
              label="Confirm password"
              name="passwordConfirmation"
              onChange={(event) => {
                setConfirmation(event.target.value)
                setFieldErrors((current) => ({
                  ...current,
                  confirmation: undefined,
                }))
              }}
              readOnly={registration.isPending || success}
              required
              type="password"
              value={confirmation}
            />
            {registration.isPending ? (
              <Alert role="status">Creating your account...</Alert>
            ) : null}
            <Button
              disabled={registration.isPending || success}
              className={styles.submit}
              fullWidth
              size="large"
              type="submit"
            >
              {registration.isPending
                ? 'Creating account...'
                : 'Create account'}
            </Button>
            <Typography
              className={styles.footer}
              align="center"
              variant="caption"
            >
              <span>
                {success
                  ? 'Your account is ready. '
                  : 'Already have an account? '}
              </span>
              <Link
                href={accountAccessHref('/sign-in', returnTo)}
                onClick={(event) => {
                  if (onNavigate) {
                    event.preventDefault()
                    onNavigate(accountAccessHref('/sign-in', returnTo))
                  }
                }}
              >
                Sign in
              </Link>
            </Typography>
          </Stack>
        </CardContent>
      </Card>
    </main>
  )
}
