import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { server } from '../test/server'

import { CreateAccountForm } from './-create-account-form'

function renderForm() {
  const onNavigate = vi.fn()
  const view = render(
    <QueryClientProvider client={new QueryClient()}>
      <CreateAccountForm
        onNavigate={onNavigate}
        returnTo="/applications/current"
      />
    </QueryClientProvider>,
  )
  return { ...view, onNavigate }
}

async function fillForm() {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Email address'), 'ada@example.com')
  await user.type(
    screen.getByLabelText('Password', { exact: true }),
    'Example!',
  )
  await user.type(screen.getByLabelText('Confirm password'), 'Example!')
  return user
}

const endpoint = '*/api/v1/applicant-accounts'

describe('Applicant registration', () => {
  it('validates fields, focuses the first error and sends no invalid request', async () => {
    let requests = 0
    server.use(
      http.post(endpoint, () => {
        requests++
        return new HttpResponse(null, { status: 201 })
      }),
    )
    const { container } = renderForm()
    fireEvent.submit(container.querySelector('form')!)
    expect(screen.getByLabelText('Email address')).toHaveFocus()
    expect(screen.getByLabelText('Password', { exact: true })).toHaveAttribute(
      'aria-invalid',
      'true',
    )
    expect(screen.getByLabelText('Confirm password')).toHaveAttribute(
      'aria-invalid',
      'true',
    )
    expect(requests).toBe(0)
    expect((await axe(container)).violations).toEqual([])
    await fillForm()
    fireEvent.change(screen.getByLabelText('Confirm password'), {
      target: { value: 'different' },
    })
    fireEvent.submit(container.querySelector('form')!)
    expect(screen.getByLabelText('Confirm password')).toHaveFocus()
    expect(requests).toBe(0)
  })

  it('posts the contract document once and confirms creation without signing in', async () => {
    let requests = 0
    let finishRequest!: () => void
    const pendingRequest = new Promise<void>((resolve) => {
      finishRequest = resolve
    })
    server.use(
      http.post(endpoint, async ({ request }) => {
        requests++
        expect(request.headers.get('Content-Type')).toBe(
          'application/vnd.api+json',
        )
        expect(request.headers.get('Accept')).toBe('application/vnd.api+json')
        expect(await request.json()).toEqual({
          data: {
            type: 'applicant-accounts',
            attributes: { email: 'ada@example.com', password: 'Example!' },
          },
        })
        await pendingRequest
        return new HttpResponse(null, { status: 201 })
      }),
    )
    const { container, onNavigate } = renderForm()
    await fillForm()
    fireEvent.submit(container.querySelector('form')!)
    fireEvent.submit(container.querySelector('form')!)
    expect(
      await screen.findByRole('button', { name: 'Creating account...' }),
    ).toBeDisabled()
    finishRequest()
    await waitFor(() =>
      expect(screen.getByRole('status')).toHaveTextContent(
        'Your account has been created',
      ),
    )
    expect(requests).toBe(1)
    expect(onNavigate).not.toHaveBeenCalled()
    expect(screen.getByLabelText('Password', { exact: true })).toHaveValue('')
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute(
      'href',
      '/sign-in?returnTo=%2Fapplications%2Fcurrent',
    )
    expect((await axe(container)).violations).toEqual([])
  })

  it.each([
    [409, 'An account already exists'],
    [429, 'Too many attempts'],
    [500, 'Account creation could not be confirmed'],
    [200, 'Account creation could not be confirmed'],
  ])(
    'handles HTTP %s and preserves input for retry',
    async (status, message) => {
      server.use(http.post(endpoint, () => new HttpResponse(null, { status })))
      renderForm()
      const user = await fillForm()
      await user.click(screen.getByRole('button', { name: 'Create account' }))
      expect(await screen.findByRole('alert')).toHaveTextContent(message)
      expect(screen.getByLabelText('Email address')).toHaveValue(
        'ada@example.com',
      )
      expect(screen.getByLabelText('Password', { exact: true })).toHaveValue(
        'Example!',
      )
      expect(
        screen.getByRole('button', { name: 'Create account' }),
      ).toBeEnabled()
      expect(screen.queryByRole('status')).not.toBeInTheDocument()
    },
  )

  it('maps multiple API validation errors to their fields', async () => {
    server.use(
      http.post(endpoint, () =>
        HttpResponse.json(
          {
            errors: [
              {
                detail: 'Email rejected by server.',
                source: { pointer: '/data/attributes/email' },
              },
              {
                detail: 'Password rejected by server.',
                source: { pointer: '/data/attributes/password' },
              },
            ],
          },
          { status: 400 },
        ),
      ),
    )
    renderForm()
    const user = await fillForm()
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(
      await screen.findByText('Email rejected by server.'),
    ).toBeInTheDocument()
    expect(screen.getByText('Password rejected by server.')).toBeInTheDocument()
    expect(screen.getByLabelText('Email address')).toHaveFocus()
  })

  it('handles network failure and allows a successful retry', async () => {
    server.use(http.post(endpoint, () => HttpResponse.error()))
    renderForm()
    const user = await fillForm()
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Check your connection',
    )
    server.use(
      http.post(endpoint, () => new HttpResponse(null, { status: 201 })),
    )
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Your account has been created',
    )
  })
})
