import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { server } from '../test/server'

import { SignInForm } from './-sign-in-form'

const endpoint = '*/api/v1/applicant-sessions'
const journey =
  '/applications/0199a8bb-8687-7c22-aed5-55d432f50f23/personal-details'
const session = {
  data: {
    type: 'applicant-sessions',
    id: '0199a8bb-8687-7c22-aed5-55d432f50f23',
    attributes: { href: journey },
  },
}

function renderForm() {
  const onSuccess = vi.fn()
  const client = new QueryClient()
  const view = render(
    <QueryClientProvider client={client}>
      <SignInForm onSuccess={onSuccess} returnTo="/applications/current" />
    </QueryClientProvider>,
  )
  return { ...view, onSuccess, client }
}

async function fillForm() {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Email address'), 'ada@example.com')
  await user.type(
    screen.getByLabelText('Password', { exact: true }),
    'Example!',
  )
  return user
}

describe('Applicant login', () => {
  it('requires email and password, focuses errors and makes no invalid request', async () => {
    const handler = vi.fn(() => HttpResponse.json(session, { status: 201 }))
    server.use(http.post(endpoint, handler))
    const { container, onSuccess } = renderForm()
    fireEvent.submit(container.querySelector('form')!)
    expect(screen.getByLabelText('Email address')).toHaveFocus()
    expect(screen.getByText('Enter your password.')).toBeInTheDocument()
    expect(screen.getByLabelText('Email address')).toHaveAttribute(
      'aria-invalid',
      'true',
    )
    expect((await axe(container)).violations).toEqual([])
    fireEvent.change(screen.getByLabelText('Email address'), {
      target: { value: 'invalid' },
    })
    fireEvent.submit(container.querySelector('form')!)
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Enter a valid email address.',
    )
    expect(handler).not.toHaveBeenCalled()
    expect(onSuccess).not.toHaveBeenCalled()
  })

  it('posts JSON:API with cookie support, blocks duplicates and follows the API journey', async () => {
    let finishRequest!: () => void
    const pending = new Promise<void>((resolve) => {
      finishRequest = resolve
    })
    let requests = 0
    server.use(
      http.post(endpoint, async ({ request }) => {
        requests++
        expect(request.headers.get('Content-Type')).toBe(
          'application/vnd.api+json',
        )
        expect(request.headers.get('Accept')).toBe('application/vnd.api+json')
        expect(request.credentials).toBe('same-origin')
        expect(await request.json()).toEqual({
          data: {
            type: 'applicant-sessions',
            attributes: { email: 'ada@example.com', password: 'Example!' },
          },
        })
        await pending
        return HttpResponse.json(session, { status: 201 })
      }),
    )
    const { container, onSuccess, client } = renderForm()
    client.setQueryData(['previous-applicant'], { private: 'data' })
    await fillForm()
    fireEvent.submit(container.querySelector('form')!)
    fireEvent.submit(container.querySelector('form')!)
    expect(
      await screen.findByRole('button', { name: 'Signing in...' }),
    ).toBeDisabled()
    expect(screen.getByRole('status')).toHaveTextContent('Signing in...')
    expect(container.querySelector('form')).toHaveAttribute('aria-busy', 'true')
    finishRequest()
    await waitFor(() =>
      expect(onSuccess).toHaveBeenCalledExactlyOnceWith(journey),
    )
    expect(requests).toBe(1)
    expect(client.getQueryData(['previous-applicant'])).toBeUndefined()
    expect(screen.getByLabelText('Password', { exact: true })).toHaveValue('')
    expect((await axe(container)).violations).toEqual([])
  })

  it.each([
    [401, 'Invalid email or password.'],
    [429, 'Too many sign-in attempts.'],
    [500, 'Sign-in could not be confirmed.'],
    [200, 'Sign-in could not be confirmed.'],
  ])(
    'handles HTTP %s without navigation and preserves email for retry',
    async (status, message) => {
      server.use(http.post(endpoint, () => new HttpResponse(null, { status })))
      const { onSuccess, container } = renderForm()
      const user = await fillForm()
      await user.click(screen.getByRole('button', { name: 'Sign in' }))
      expect(await screen.findByRole('alert')).toHaveTextContent(message)
      expect(screen.getByLabelText('Email address')).toHaveValue(
        'ada@example.com',
      )
      expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled()
      expect(onSuccess).not.toHaveBeenCalled()
      if (status === 401) expect((await axe(container)).violations).toEqual([])
    },
  )

  it('maps server validation errors to fields without exposing account existence', async () => {
    server.use(
      http.post(endpoint, () =>
        HttpResponse.json(
          {
            errors: [
              {
                detail: 'Untrusted detail',
                source: { pointer: '/data/attributes/email' },
              },
              { source: { pointer: '/data/attributes/password' } },
            ],
          },
          { status: 400 },
        ),
      ),
    )
    renderForm()
    const user = await fillForm()
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(
      await screen.findByText('Check the password you entered.'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Email address')).toHaveFocus()
    expect(screen.queryByText('Untrusted detail')).not.toBeInTheDocument()
  })

  it.each([
    null,
    {
      data: {
        ...session.data,
        attributes: { href: 'https://evil.example/applications/current' },
      },
    },
    {
      data: {
        ...session.data,
        attributes: { href: '/applications/../../reviewer' },
      },
    },
    {
      data: {
        ...session.data,
        attributes: { href: '/applications/\\evil.example' },
      },
    },
  ])('rejects malformed or unsafe success responses (%j)', async (document) => {
    server.use(
      http.post(endpoint, () => HttpResponse.json(document, { status: 201 })),
    )
    const { onSuccess } = renderForm()
    const user = await fillForm()
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'unexpected sign-in response',
    )
    expect(onSuccess).not.toHaveBeenCalled()
  })

  it('reports network failure and lets the user retry', async () => {
    server.use(http.post(endpoint, () => HttpResponse.error()))
    const { onSuccess } = renderForm()
    const user = await fillForm()
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Check your connection',
    )
    expect(screen.getByLabelText('Email address')).toHaveValue(
      'ada@example.com',
    )
    server.use(
      http.post(endpoint, () => HttpResponse.json(session, { status: 201 })),
    )
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith(journey))
  })
})
