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
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'

import {
  LoginError,
  signInApplicant,
  type LoginFieldErrors,
} from './-login-api'
import { accountAccessHref } from './-return-target'
import styles from './account-access.module.css'

export interface SignInFormProps {
  onNavigate?: (href: string) => void
  returnTo?: string
  onSuccess: (href: string) => void
}

export function SignInForm({
  onNavigate,
  returnTo,
  onSuccess,
}: SignInFormProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string>()
  const [fieldErrors, setFieldErrors] = useState<LoginFieldErrors>({})
  const [signedIn, setSignedIn] = useState(false)
  const emailInput = useRef<HTMLInputElement>(null)
  const passwordInput = useRef<HTMLInputElement>(null)
  const submitting = useRef(false)
  const queryClient = useQueryClient()
  const login = useMutation({
    mutationFn: signInApplicant,
    retry: false,
    gcTime: 0,
  })

  function showFieldErrors(errors: LoginFieldErrors) {
    setFieldErrors(errors)
    if (errors.email) {
      emailInput.current?.focus()
    } else if (errors.password) {
      passwordInput.current?.focus()
    }
  }

  async function submit(): Promise<void> {
    if (submitting.current || signedIn) return
    setError(undefined)
    const errors: LoginFieldErrors = {}
    if (!email.trim()) {
      errors.email = 'Enter your email address.'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      errors.email = 'Enter a valid email address.'
    }
    if (!password) errors.password = 'Enter your password.'
    showFieldErrors(errors)
    if (Object.keys(errors).length) return
    submitting.current = true
    try {
      const destination = await login.mutateAsync({
        email: email.trim(),
        password,
      })
      // A new session must not display data cached for a previous applicant.
      queryClient.removeQueries()
      setPassword('')
      setSignedIn(true)
      onSuccess(destination)
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : 'Sign-in could not be confirmed. Please try again.',
      )
      if (cause instanceof LoginError) showFieldErrors(cause.fields)
    } finally {
      login.reset()
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
            aria-busy={login.isPending}
            spacing={3}
          >
            <Stack spacing={1}>
              <Typography component="h1" variant="heading">
                Welcome back
              </Typography>
              <Typography tone="muted">
                Sign in to continue your KYC application.
              </Typography>
            </Stack>
            {(error ?? fieldErrors.email ?? fieldErrors.password) ? (
              <Alert aria-live="assertive" role="alert" severity="error">
                {error ?? fieldErrors.email ?? fieldErrors.password}
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
              readOnly={login.isPending || signedIn}
              required
              type="email"
              value={email}
            />
            <TextField
              autoComplete="current-password"
              error={Boolean(fieldErrors.password)}
              fullWidth
              helperText={fieldErrors.password}
              id="applicant-password"
              inputRef={passwordInput}
              label="Password"
              name="password"
              onChange={(event) => {
                setPassword(event.target.value)
                setFieldErrors((current) => ({
                  ...current,
                  password: undefined,
                }))
              }}
              readOnly={login.isPending || signedIn}
              required
              type="password"
              value={password}
            />
            {login.isPending ? (
              <Alert role="status">Signing in...</Alert>
            ) : null}
            {signedIn ? (
              <Alert role="status">
                Signed in. Opening your application...
              </Alert>
            ) : null}
            <Button
              disabled={login.isPending || signedIn}
              className={styles.submit}
              fullWidth
              size="large"
              type="submit"
            >
              {login.isPending ? 'Signing in...' : 'Sign in'}
            </Button>
            <Typography
              className={styles.footer}
              align="center"
              variant="caption"
            >
              <span>New to KYC? </span>
              <Link
                href={accountAccessHref('/create-account', returnTo)}
                onClick={(event) => {
                  if (onNavigate) {
                    event.preventDefault()
                    onNavigate(accountAccessHref('/create-account', returnTo))
                  }
                }}
              >
                Create account
              </Link>
            </Typography>
          </Stack>
        </CardContent>
      </Card>
    </main>
  )
}
