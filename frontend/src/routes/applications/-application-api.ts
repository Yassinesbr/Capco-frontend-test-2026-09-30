import { z } from 'zod'

const stepSchema = z.enum(['personal-details', 'identity-and-address'])
export type ApplicationStep = z.infer<typeof stepSchema>
export type Answers = Record<string, string | boolean | null | undefined>
const formSchema = z.object({
  data: z.object({
    type: z.literal('applicant-application-forms'),
    id: z.uuid(),
    attributes: z.object({
      status: z.literal('draft'),
      currentStep: stepSchema,
      steps: z.array(
        z.object({
          step: stepSchema,
          state: z.enum(['current', 'complete', 'remaining']),
        }),
      ),
      answers: z.record(
        z.string(),
        z.union([z.string(), z.boolean(), z.null()]),
      ),
      documentEvidence: z.object({ present: z.boolean() }),
      version: z.number().int().nonnegative(),
    }),
  }),
})
export type SavedForm = z.infer<typeof formSchema>['data']
const applicationSchema = z.object({
  data: z.object({
    type: z.literal('applicant-applications'),
    id: z.uuid(),
    attributes: z.object({
      status: z.literal('draft'),
      currentStep: stepSchema,
      progress: z.array(
        z.object({
          step: stepSchema,
          state: z.enum(['not-started', 'current', 'complete']),
        }),
      ),
    }),
  }),
})
export type Draft = z.infer<typeof applicationSchema>['data']
export const formKey = (id: string) => ['application-form', id] as const
export const currentDraftKey = ['current-draft'] as const

export class ApplicationApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message)
  }
}

async function request(
  path: string,
  init: RequestInit = {},
  accepted = [200],
): Promise<Response> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/vnd.api+json')
  if (init.method && init.method !== 'GET') {
    const cookie = document.cookie
      .split('; ')
      .find((part) => part.startsWith('XSRF-TOKEN='))
    if (cookie) {
      try {
        headers.set(
          'X-XSRF-TOKEN',
          decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)),
        )
      } catch {
        throw new ApplicationApiError(
          'Your session could not be verified. Sign in again.',
          401,
        )
      }
    }
  }
  let response: Response
  try {
    response = await fetch(`/api/v1/${path}`, {
      ...init,
      headers,
      credentials: 'same-origin',
    })
  } catch {
    throw new ApplicationApiError(
      'Could not connect to the server. Your changes have not been saved. Check your connection and retry.',
      0,
    )
  }
  if (accepted.includes(response.status)) return response
  const messages: Record<number, string> = {
    400: 'The server could not accept these answers. Check your entries and retry.',
    401: 'Your session has expired. Sign in again, then retry. Your entered answers are still here.',
    403: 'Your session could not authorize this action. Sign in again, then retry.',
    404: 'This draft could not be found or is no longer available to your account.',
    409: 'This draft changed in another session. Your edits are still here. Load the latest version before saving again.',
    422: 'Some answers or document evidence were rejected. Check your entries and retry.',
    429: 'Too many requests. Please wait and retry.',
  }
  throw new ApplicationApiError(
    messages[response.status] ??
      'The server could not complete this action. Please retry.',
    response.status,
  )
}

async function parse<T>(response: Response, schema: z.ZodType<T>): Promise<T> {
  const result = schema.safeParse(await response.json().catch(() => null))
  if (!result.success)
    throw new ApplicationApiError(
      'The server returned an unexpected response. Success could not be confirmed. Please retry.',
      response.status,
    )
  return result.data
}

export async function getCurrentDraft(): Promise<Draft | null> {
  const response = await request(
    'applicant-applications/current',
    {},
    [200, 204],
  )
  if (response.status === 204) return null
  return (await parse(response, applicationSchema)).data
}
export async function startDraft(): Promise<Draft> {
  return (
    await parse(
      await request('applicant-applications', { method: 'POST' }, [200, 201]),
      applicationSchema,
    )
  ).data
}
export async function getForm(id: string): Promise<SavedForm> {
  const form = (
    await parse(await request(`applicant-applications/${id}/form`), formSchema)
  ).data
  if (form.id !== id)
    throw new ApplicationApiError(
      'The server returned a different application. Please reload your draft.',
      500,
    )
  return form
}
export async function saveForm(
  id: string,
  step: ApplicationStep,
  answers: Answers,
  version: number,
): Promise<SavedForm> {
  const response = await request(`applicant-applications/${id}/form`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/vnd.api+json' },
    body: JSON.stringify({
      data: {
        type: 'applicant-application-forms',
        id,
        attributes: { step, answers, version },
      },
    }),
  })
  const form = (await parse(response, formSchema)).data
  if (form.id !== id)
    throw new ApplicationApiError(
      'The server returned a different application. Save could not be confirmed.',
      500,
    )
  return form
}
export async function uploadEvidence(
  id: string,
  file: File,
): Promise<SavedForm> {
  if (
    !['image/jpeg', 'image/png'].includes(file.type) ||
    file.size === 0 ||
    file.size > 10 * 1024 * 1024
  ) {
    throw new ApplicationApiError(
      'Choose a non-empty JPG or PNG image no larger than 10 MiB.',
      422,
    )
  }
  const body = new FormData()
  body.append('file', file)
  const schema = z.object({
    data: z.object({
      type: z.literal('applicant-application-document-evidence'),
      id: z.literal(id),
      attributes: z.object({ present: z.literal(true) }),
    }),
  })
  await parse(
    await request(
      `applicant-applications/${id}/document-evidence`,
      { method: 'POST', body },
      [201],
    ),
    schema,
  )
  // Uploading changes the form version, so fetch it before the next answer save.
  return getForm(id)
}
export async function signOut(): Promise<void> {
  await request('applicant-sessions/current', { method: 'DELETE' }, [204])
}
