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
import { useEffect, useRef, useState } from 'react'

import { accountAccessHref } from './-return-target'
import styles from './account-access.module.css'

export interface CreateAccountFormProps {
  onNavigate?: (href: string) => void
  returnTo?: string
}

const passwordGuidance =
  'Use at least six characters, including one uppercase letter and one special character.'

export function CreateAccountForm({
  onNavigate,
  returnTo,
}: CreateAccountFormProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string>()
  const [emailError, setEmailError] = useState<string>()
  const [passwordError, setPasswordError] = useState<string>()
  const [successMessage, setSuccessMessage] = useState<string>()
  const emailInput = useRef<HTMLInputElement>(null)
  const passwordInput = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (emailError) {
      emailInput.current?.focus()
    } else if (passwordError) {
      passwordInput.current?.focus()
    }
  }, [emailError, passwordError])

  async function submit(): Promise<void> {
    setError(undefined)
    setEmailError(undefined)
    setPasswordError(undefined)
    setSuccessMessage(undefined)

    try {
      setPassword('')
      setSuccessMessage(
        'Account form is ready. Sign-in is available for this prototype.',
      )
    } catch {
      setError('Account form could not be completed.')
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
            {(error ?? emailError ?? passwordError) ? (
              <Alert aria-live="assertive" role="alert" severity="error">
                {error ?? emailError ?? passwordError}
              </Alert>
            ) : null}
            {successMessage ? (
              <Alert aria-live="polite" role="status" severity="success">
                {successMessage}
              </Alert>
            ) : null}
            <TextField
              autoComplete="email"
              error={Boolean(emailError)}
              fullWidth
              helperText={emailError}
              id="applicant-email"
              inputRef={emailInput}
              label="Email address"
              name="email"
              onChange={(event) => {
                setEmail(event.target.value)
                setEmailError(undefined)
              }}
              required
              type="email"
              value={email}
            />
            <TextField
              autoComplete="new-password"
              error={Boolean(passwordError)}
              fullWidth
              helperText={passwordError ?? passwordGuidance}
              id="applicant-password"
              inputRef={passwordInput}
              label="Password"
              name="password"
              onChange={(event) => {
                setPassword(event.target.value)
                setPasswordError(undefined)
              }}
              required
              type="password"
              value={password}
            />
            <Button
              className={styles.submit}
              fullWidth
              size="large"
              type="submit"
            >
              Create account
            </Button>
            <Typography
              className={styles.footer}
              align="center"
              variant="caption"
            >
              <span>Already have an account? </span>
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
