import { z } from 'zod'

export interface LoginFieldErrors {
  email?: string
  password?: string
}

export class LoginError extends Error {
  constructor(
    message: string,
    readonly fields: LoginFieldErrors = {},
  ) {
    super(message)
  }
}

function isApplicantJourney(href: string): boolean {
  if (!href.startsWith('/applications/') || href.includes('\\')) return false
  const url = new URL(href, 'http://localhost')
  return (
    url.origin === 'http://localhost' &&
    url.pathname.startsWith('/applications/')
  )
}

const sessionDocument = z.object({
  data: z.object({
    type: z.literal('applicant-sessions'),
    id: z.uuid(),
    attributes: z.object({ href: z.string().refine(isApplicantJourney) }),
  }),
})

const errorDocument = z.object({
  errors: z.array(
    z.object({
      source: z.object({ pointer: z.string() }).optional(),
    }),
  ),
})

export async function signInApplicant(credentials: {
  email: string
  password: string
}): Promise<string> {
  let response: Response
  try {
    response = await fetch('/api/v1/applicant-sessions', {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        Accept: 'application/vnd.api+json',
        'Content-Type': 'application/vnd.api+json',
      },
      body: JSON.stringify({
        data: { type: 'applicant-sessions', attributes: credentials },
      }),
    })
  } catch {
    throw new LoginError(
      'We could not connect to the server. Check your connection and try again.',
    )
  }
  if (response.status === 401) {
    throw new LoginError('Invalid email or password. Please try again.')
  }
  if (response.status === 429) {
    throw new LoginError(
      'Too many sign-in attempts. Please wait and try again later.',
    )
  }
  if (response.status === 400) {
    const document = errorDocument.safeParse(
      await response.json().catch(() => null),
    )
    const fields: LoginFieldErrors = {}
    if (document.success) {
      for (const error of document.data.errors) {
        if (error.source?.pointer === '/data/attributes/email') {
          fields.email = 'Enter a valid email address.'
        }
        if (error.source?.pointer === '/data/attributes/password') {
          fields.password = 'Check the password you entered.'
        }
      }
    }
    throw new LoginError(
      'The server could not accept these details. Check the form and try again.',
      fields,
    )
  }
  if (response.status !== 201) {
    throw new LoginError(
      'Sign-in could not be confirmed. Please try again later.',
    )
  }
  const document = sessionDocument.safeParse(
    await response.json().catch(() => null),
  )
  if (!document.success) {
    throw new LoginError(
      'The server returned an unexpected sign-in response. Please try again.',
    )
  }
  // The browser handles the HttpOnly session and XSRF cookies; no token storage is needed.
  return document.data.data.attributes.href
}
