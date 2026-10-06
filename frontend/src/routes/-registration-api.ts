import { z } from 'zod'

export const passwordGuidance =
  'Use at least six characters, including one uppercase letter and one special character.'

export interface RegistrationErrors {
  email?: string
  password?: string
  confirmation?: string
}

export function validateRegistration(
  email: string,
  password: string,
  confirmation: string,
): RegistrationErrors {
  const errors: RegistrationErrors = {}
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
    errors.email = 'Enter a valid email address.'
  }
  if (
    password.length < 6 ||
    !/\p{Lu}/u.test(password) ||
    !/[^\p{L}\p{N}]/u.test(password)
  ) {
    errors.password = passwordGuidance
  }
  if (!confirmation || confirmation !== password) {
    errors.confirmation = 'Enter the same password in both fields.'
  }
  return errors
}

const errorDocument = z.object({
  errors: z.array(
    z.object({
      detail: z.string().optional(),
      source: z.object({ pointer: z.string() }).optional(),
    }),
  ),
})

export class RegistrationError extends Error {
  constructor(
    message: string,
    readonly fields: RegistrationErrors = {},
  ) {
    super(message)
  }
}

export async function registerApplicant(credentials: {
  email: string
  password: string
}): Promise<void> {
  let response: Response
  try {
    response = await fetch('/api/v1/applicant-accounts', {
      method: 'POST',
      headers: {
        Accept: 'application/vnd.api+json',
        'Content-Type': 'application/vnd.api+json',
      },
      body: JSON.stringify({
        data: { type: 'applicant-accounts', attributes: credentials },
      }),
    })
  } catch {
    throw new RegistrationError(
      'We could not connect to the server. Check your connection and try again.',
    )
  }
  // The contract specifies an empty 201 response, without a session.
  if (response.status === 201) {
    return
  }
  if (response.status === 409) {
    throw new RegistrationError(
      'An account already exists for this email address. Sign in or use another email.',
      { email: 'An account already exists for this email address.' },
    )
  }
  if (response.status === 429) {
    throw new RegistrationError(
      'Too many attempts. Please wait and try again later.',
    )
  }
  if (response.status === 400) {
    const document = errorDocument.safeParse(
      await response.json().catch(() => null),
    )
    const fields: RegistrationErrors = {}
    if (document.success) {
      for (const error of document.data.errors) {
        if (error.source?.pointer === '/data/attributes/email') {
          fields.email = error.detail ?? 'Enter a valid email address.'
        }
        if (error.source?.pointer === '/data/attributes/password') {
          fields.password = error.detail ?? passwordGuidance
        }
      }
    }
    throw new RegistrationError(
      'The server could not accept these details. Check the form and try again.',
      fields,
    )
  }
  throw new RegistrationError(
    'Account creation could not be confirmed. Please try again later.',
  )
}
