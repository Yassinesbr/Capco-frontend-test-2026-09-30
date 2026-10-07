import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import { server } from '../test/server'

import { ApplicationForm } from './-application-form'
import { signInApplicant } from './-login-api'
import {
  getForm,
  signOut,
  type SavedForm,
} from './applications/-application-api'
import CurrentApplicationJourneyPage from './applications/current'

const id = '0199a8bb-8687-7c22-aed5-55d432f50f23'
const formEndpoint = `*/api/v1/applicant-applications/${id}/form`
const initial: SavedForm = {
  type: 'applicant-application-forms',
  id,
  attributes: {
    status: 'draft',
    currentStep: 'personal-details',
    version: 2,
    steps: [
      { step: 'personal-details', state: 'current' },
      { step: 'identity-and-address', state: 'remaining' },
    ],
    documentEvidence: { present: true },
    answers: {
      name: 'Ada',
      dateOfBirth: '1990-01-02',
      country: 'UK',
      nationality: 'British',
      email: 'ada@example.com',
      phone: '123',
      consentConfirmed: true,
      documentType: 'Passport',
      documentNumber: 'P123',
      documentCountry: 'UK',
      expiry: '2030-01-02',
      street: '1 Main Street',
      city: 'London',
      postal: 'AB1',
      residentialCountry: 'UK',
    },
  },
}
function renderForm(
  form = initial,
  step: 'personal-details' | 'identity-and-address' = 'personal-details',
) {
  const onNavigate = vi.fn()
  const view = render(
    <QueryClientProvider client={new QueryClient()}>
      <ApplicationForm initialForm={form} step={step} onNavigate={onNavigate} />
    </QueryClientProvider>,
  )
  return { ...view, onNavigate }
}

function formResponse(form = initial) {
  return { data: form }
}

describe('Save and resume application', () => {
  it('populates every answer in both steps, including consent and saved evidence', async () => {
    const personal = renderForm()
    for (const key of [
      'name',
      'dateOfBirth',
      'country',
      'nationality',
      'email',
      'phone',
    ]) {
      expect(personal.container.querySelector(`[name="${key}"]`)).toHaveValue(
        String(initial.attributes.answers[key]),
      )
    }
    expect(screen.getByRole('checkbox')).toBeChecked()
    expect((await axe(personal.container)).violations).toEqual([])
    personal.unmount()
    const identity = renderForm(initial, 'identity-and-address')
    for (const key of [
      'documentType',
      'documentNumber',
      'documentCountry',
      'expiry',
      'street',
      'city',
      'postal',
      'residentialCountry',
    ]) {
      expect(identity.container.querySelector(`[name="${key}"]`)).toHaveValue(
        String(initial.attributes.answers[key]),
      )
    }
    expect(
      screen.getByText(
        'Document evidence is saved. You may upload a replacement.',
      ),
    ).toBeInTheDocument()
    expect((await axe(identity.container)).violations).toEqual([])
  })

  it('saves only this step with CSRF and version, blocks duplicates and clears saved status on edits', async () => {
    document.cookie = 'XSRF-TOKEN=test-csrf; path=/'
    let release!: () => void
    const pending = new Promise<void>((resolve) => {
      release = resolve
    })
    let requests = 0
    server.use(
      http.patch(formEndpoint, async ({ request }) => {
        requests++
        expect(request.credentials).toBe('same-origin')
        expect(request.headers.get('X-XSRF-TOKEN')).toBe('test-csrf')
        expect(request.headers.get('Content-Type')).toBe(
          'application/vnd.api+json',
        )
        expect(await request.json()).toEqual({
          data: {
            type: initial.type,
            id,
            attributes: {
              step: 'personal-details',
              version: 2,
              answers: {
                name: 'Updated',
                dateOfBirth: '1990-01-02',
                country: 'UK',
                nationality: 'British',
                email: 'ada@example.com',
                phone: '123',
                consentConfirmed: true,
              },
            },
          },
        })
        await pending
        return HttpResponse.json(
          formResponse({
            ...initial,
            attributes: {
              ...initial.attributes,
              version: 3,
              answers: { ...initial.attributes.answers, name: 'Updated' },
            },
          }),
        )
      }),
    )
    const { container } = renderForm()
    fireEvent.change(screen.getByLabelText('Name (required)'), {
      target: { value: 'Updated' },
    })
    fireEvent.submit(container.querySelector('form')!)
    fireEvent.submit(container.querySelector('form')!)
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Saving or loading',
    )
    expect(
      screen.queryByText('Your answers have been saved.'),
    ).not.toBeInTheDocument()
    release()
    expect(
      await screen.findByText('Your answers have been saved.'),
    ).toBeInTheDocument()
    expect(requests).toBe(1)
    fireEvent.change(screen.getByLabelText('Name (required)'), {
      target: { value: 'Unsaved' },
    })
    expect(
      screen.queryByText('Your answers have been saved.'),
    ).not.toBeInTheDocument()
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
  })

  it.each([400, 401, 403, 422, 500])(
    'preserves entered values and never reports success after HTTP %s',
    async (status) => {
      server.use(
        http.patch(formEndpoint, () => new HttpResponse(null, { status })),
      )
      renderForm()
      const user = userEvent.setup()
      await user.clear(screen.getByLabelText('Name (required)'))
      await user.type(screen.getByLabelText('Name (required)'), 'Keep my edits')
      await user.click(screen.getByRole('button', { name: /^Save$/ }))
      expect(await screen.findByRole('alert')).toBeInTheDocument()
      expect(screen.getByLabelText('Name (required)')).toHaveValue(
        'Keep my edits',
      )
      expect(
        screen.queryByText('Your answers have been saved.'),
      ).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: /^Save$/ })).toBeEnabled()
      if ([401, 403].includes(status))
        expect(screen.getByRole('link')).toHaveAttribute('target', '_blank')
    },
  )

  it('handles conflicts by refreshing the version without losing edits, then retries explicitly', async () => {
    let expectedVersion = 2
    server.use(
      http.get(formEndpoint, () =>
        HttpResponse.json(
          formResponse({
            ...initial,
            attributes: {
              ...initial.attributes,
              version: 7,
              answers: { name: 'Other edit' },
            },
          }),
        ),
      ),
      http.patch(formEndpoint, async ({ request }) => {
        const body = (await request.json()) as {
          data: { attributes: { version: number; answers: { name: string } } }
        }
        expect(body.data.attributes.version).toBe(expectedVersion)
        if (expectedVersion === 2)
          return new HttpResponse(null, { status: 409 })
        expect(body.data.attributes.answers.name).toBe('My edit')
        return HttpResponse.json(
          formResponse({
            ...initial,
            attributes: {
              ...initial.attributes,
              version: 8,
              answers: { name: 'My edit' },
            },
          }),
        )
      }),
    )
    renderForm()
    const user = userEvent.setup()
    fireEvent.change(screen.getByLabelText('Name (required)'), {
      target: { value: 'My edit' },
    })
    await user.click(screen.getByRole('button', { name: /^Save$/ }))
    await user.click(
      await screen.findByRole('button', {
        name: 'Load latest version and keep my edits',
      }),
    )
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Latest saved version loaded',
    )
    expect(screen.getByLabelText('Name (required)')).toHaveValue('My edit')
    expectedVersion = 7
    await user.click(screen.getByRole('button', { name: /^Save$/ }))
    expect(
      await screen.findByText('Your answers have been saved.'),
    ).toBeInTheDocument()
  })

  it('retries network failures and saves partial answers', async () => {
    server.use(http.patch(formEndpoint, () => HttpResponse.error()))
    const blank = {
      ...initial,
      attributes: { ...initial.attributes, answers: {} },
    }
    renderForm(blank)
    const user = userEvent.setup()
    fireEvent.change(screen.getByLabelText('Name (required)'), {
      target: { value: 'Partial' },
    })
    await user.click(screen.getByRole('button', { name: /^Save$/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Could not connect',
    )
    server.use(
      http.patch(formEndpoint, () =>
        HttpResponse.json(
          formResponse({
            ...blank,
            attributes: {
              ...blank.attributes,
              version: 3,
              answers: { name: 'Partial' },
            },
          }),
        ),
      ),
    )
    await user.click(screen.getByRole('button', { name: /^Save$/ }))
    expect(
      await screen.findByText('Your answers have been saved.'),
    ).toBeInTheDocument()
  })

  it('does not report success or advance on a malformed save response', async () => {
    server.use(http.patch(formEndpoint, () => HttpResponse.json({ data: {} })))
    const { onNavigate } = renderForm()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Save and continue' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'unexpected response',
    )
    expect(onNavigate).not.toHaveBeenCalled()
  })

  it('reloads persisted answers from the API after logout and another login', async () => {
    let persisted = structuredClone(initial)
    server.use(
      http.get(formEndpoint, () => HttpResponse.json(formResponse(persisted))),
      http.patch(formEndpoint, async ({ request }) => {
        const body = (await request.json()) as {
          data: { attributes: { answers: Record<string, string | boolean> } }
        }
        persisted = {
          ...persisted,
          attributes: {
            ...persisted.attributes,
            version: 3,
            answers: {
              ...persisted.attributes.answers,
              ...body.data.attributes.answers,
            },
          },
        }
        return HttpResponse.json(formResponse(persisted))
      }),
      http.delete(
        '*/api/v1/applicant-sessions/current',
        () => new HttpResponse(null, { status: 204 }),
      ),
      http.post('*/api/v1/applicant-sessions', () =>
        HttpResponse.json(
          {
            data: {
              type: 'applicant-sessions',
              id,
              attributes: { href: '/applications/current' },
            },
          },
          { status: 201 },
        ),
      ),
    )
    const view = renderForm(await getForm(id))
    const user = userEvent.setup()
    fireEvent.change(screen.getByLabelText('Name (required)'), {
      target: { value: 'Persisted after login' },
    })
    await user.click(screen.getByRole('button', { name: /^Save$/ }))
    await screen.findByText('Your answers have been saved.')
    view.unmount()
    await signOut()
    await signInApplicant({ email: 'ada@example.com', password: 'Example!' })
    renderForm(await getForm(id))
    expect(screen.getByLabelText('Name (required)')).toHaveValue(
      'Persisted after login',
    )
    expect(screen.getByRole('checkbox')).toBeChecked()
  })

  it('uploads evidence separately and uses the refreshed form version on the next save', async () => {
    server.use(
      http.post(
        `*/api/v1/applicant-applications/${id}/document-evidence`,
        async ({ request }) => {
          expect(request.headers.get('Content-Type')).toContain(
            'multipart/form-data',
          )
          expect(await request.text()).toContain('name="file"')
          return HttpResponse.json(
            {
              data: {
                type: 'applicant-application-document-evidence',
                id,
                attributes: { present: true },
              },
            },
            { status: 201 },
          )
        },
      ),
      http.get(formEndpoint, () =>
        HttpResponse.json(
          formResponse({
            ...initial,
            attributes: { ...initial.attributes, version: 4 },
          }),
        ),
      ),
      http.patch(formEndpoint, async ({ request }) => {
        const body = (await request.json()) as {
          data: { attributes: { version: number; step: string } }
        }
        expect(body.data.attributes.version).toBe(4)
        expect(body.data.attributes.step).toBe('identity-and-address')
        return HttpResponse.json(
          formResponse({
            ...initial,
            attributes: { ...initial.attributes, version: 5 },
          }),
        )
      }),
    )
    renderForm(initial, 'identity-and-address')
    const user = userEvent.setup()
    await user.upload(
      screen.getByLabelText('Document evidence (JPG or PNG, up to 10 MiB)'),
      new File(['image'], 'id.png', { type: 'image/png' }),
    )
    await user.click(screen.getByRole('button', { name: 'Upload document' }))
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Document evidence saved',
    )
    await user.click(screen.getByRole('button', { name: /^Save$/ }))
    expect(
      await screen.findByText('Your answers have been saved.'),
    ).toBeInTheDocument()
  })

  it('loads a real draft before offering resume and navigates using its application ID', async () => {
    server.use(
      http.get('*/api/v1/applicant-applications/current', () =>
        HttpResponse.json({
          data: {
            type: 'applicant-applications',
            id,
            attributes: {
              status: 'draft',
              currentStep: 'identity-and-address',
              progress: [
                { step: 'personal-details', state: 'complete' },
                { step: 'identity-and-address', state: 'current' },
              ],
            },
          },
        }),
      ),
    )
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <Routes>
            <Route path="/" element={<CurrentApplicationJourneyPage />} />
            <Route
              path={`/applications/${id}/identity-and-address`}
              element={<p>Real draft opened</p>}
            />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )
    const user = userEvent.setup()
    await user.click(
      await screen.findByRole('button', { name: 'Resume application' }),
    )
    expect(await screen.findByText('Real draft opened')).toBeInTheDocument()
  })
})
