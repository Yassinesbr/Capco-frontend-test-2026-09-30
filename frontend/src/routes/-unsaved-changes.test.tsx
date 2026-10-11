import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { http, HttpResponse } from 'msw'
import {
  createMemoryRouter,
  Outlet,
  RouterProvider,
  useNavigate,
  useParams,
} from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import { server } from '../test/server'

import { ApplicationForm } from './-application-form'
import { RouteFocus } from './-route-focus'
import { UnsavedChangesProvider } from './-unsaved-changes'
import ApplicationsLayout from './applications'

import type { SavedForm } from './applications/-application-api'

const id = '00000000-0000-4000-8000-000000000001'
const form: SavedForm = {
  type: 'applicant-application-forms',
  id,
  attributes: {
    status: 'draft',
    currentStep: 'personal-details',
    version: 1,
    steps: [],
    answers: { name: 'Saved applicant' },
    documentEvidence: { present: false },
  },
}
function FormPage() {
  const navigate = useNavigate()
  const { step } = useParams()
  return (
    <ApplicationForm
      initialForm={form}
      step={
        step === 'identity-and-address'
          ? 'identity-and-address'
          : 'personal-details'
      }
      onNavigate={(href) => {
        void navigate(href)
      }}
    />
  )
}
function mount(step = 'personal-details') {
  const router = createMemoryRouter(
    [
      {
        element: (
          <UnsavedChangesProvider>
            <RouteFocus />
            <Outlet />
          </UnsavedChangesProvider>
        ),
        children: [
          {
            path: '/applications',
            element: <ApplicationsLayout />,
            children: [
              { path: ':id/:step', element: <FormPage /> },
              {
                path: 'current',
                element: (
                  <main>
                    <h1>Current draft</h1>
                  </main>
                ),
              },
            ],
          },
          {
            path: '/sign-in',
            element: (
              <main>
                <h1>Sign in</h1>
              </main>
            ),
          },
        ],
      },
    ],
    { initialEntries: [`/applications/${id}/${step}`] },
  )
  render(
    <QueryClientProvider client={new QueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

describe('Unsaved changes', () => {
  it('compares retained edits with the latest server answers after recovering a conflict', async () => {
    const endpoint = `*/api/v1/applicant-applications/${id}/form`
    server.use(
      http.patch(endpoint, () => new HttpResponse(null, { status: 409 })),
      http.get(endpoint, () =>
        HttpResponse.json({
          data: {
            ...form,
            attributes: {
              ...form.attributes,
              version: 2,
              answers: { name: 'Other session' },
            },
          },
        }),
      ),
    )
    mount()
    const user = userEvent.setup()
    fireEvent.change(screen.getByLabelText('Name (required)'), {
      target: { value: 'Local edit' },
    })
    await user.click(screen.getByRole('button', { name: 'Save' }))
    await user.click(
      await screen.findByRole('button', {
        name: 'Load latest version and keep my edits',
      }),
    )
    await screen.findByText(/Latest saved version loaded/)
    expect(screen.getByLabelText('Name (required)')).toHaveValue('Local edit')
    fireEvent.change(screen.getByLabelText('Name (required)'), {
      target: { value: 'Saved applicant' },
    })
    await user.click(screen.getByRole('button', { name: 'Back' }))
    expect(await screen.findByRole('alertdialog')).toBeInTheDocument()
  })
  it('warns for a selected document even when no answers changed, and releases after deselection', async () => {
    mount('identity-and-address')
    const user = userEvent.setup()
    const input = screen.getByLabelText(
      'Document evidence (JPG or PNG, up to 10 MiB)',
    )
    await user.upload(
      input,
      new File(['image'], 'document.png', { type: 'image/png' }),
    )
    await user.click(screen.getByRole('button', { name: 'Back' }))
    expect(await screen.findByRole('alertdialog')).toBeInTheDocument()
    await user.click(
      screen.getByRole('button', { name: 'Stay and keep editing' }),
    )
    await user.upload(input, [])
    const unload = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(unload)
    expect(unload.defaultPrevented).toBe(false)
  })
  it('announces a modal, traps keyboard focus, cancels with Escape and keeps edits', async () => {
    mount()
    const user = userEvent.setup()
    await user.clear(screen.getByLabelText('Name (required)'))
    await user.type(screen.getByLabelText('Name (required)'), 'Local edit')
    await user.click(screen.getByRole('button', { name: 'Back' }))
    const dialog = await screen.findByRole('alertdialog', {
      name: 'Leave without saving?',
    })
    const stay = screen.getByRole('button', { name: 'Stay and keep editing' })
    expect(stay).toHaveFocus()
    expect((await axe(dialog)).violations).toEqual([])
    await user.tab({ shift: true })
    expect(
      screen.getByRole('button', { name: 'Leave without saving' }),
    ).toHaveFocus()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Name (required)')).toHaveValue('Local edit')
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Back' })).toHaveFocus(),
    )
  })

  it('does not sign out before confirmation and preserves the guard when logout fails', async () => {
    const logout = vi.fn(() => new HttpResponse(null, { status: 500 }))
    server.use(http.delete('*/api/v1/applicant-sessions/current', logout))
    mount()
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Name (required)'), ' edit')
    await user.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(logout).not.toHaveBeenCalled()
    await user.click(
      screen.getByRole('button', { name: 'Stay and keep editing' }),
    )
    expect(logout).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Sign out' }))
    await user.click(
      screen.getByRole('button', { name: 'Sign out without saving' }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'could not complete',
    )
    expect(logout).toHaveBeenCalledTimes(1)
    await user.click(screen.getByRole('button', { name: 'Back' }))
    expect(await screen.findByRole('alertdialog')).toBeInTheDocument()
  })

  it('warns on refresh, stops warning after reverting, and permits an explicit discard', async () => {
    mount()
    const input = screen.getByLabelText('Name (required)')
    fireEvent.change(input, { target: { value: 'Changed' } })
    const dirtyUnload = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(dirtyUnload)
    expect(dirtyUnload.defaultPrevented).toBe(true)
    fireEvent.change(input, { target: { value: 'Saved applicant' } })
    const cleanUnload = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(cleanUnload)
    expect(cleanUnload.defaultPrevented).toBe(false)
    fireEvent.change(input, { target: { value: 'Changed again' } })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Back' }))
    await user.click(
      screen.getByRole('button', { name: 'Leave without saving' }),
    )
    expect(
      await screen.findByRole('heading', { name: 'Current draft' }),
    ).toHaveFocus()
  })

  it('keeps the guard after failed saves and releases it after a confirmed save', async () => {
    const endpoint = `*/api/v1/applicant-applications/${id}/form`
    server.use(
      http.patch(endpoint, () => new HttpResponse(null, { status: 500 })),
    )
    mount()
    const user = userEvent.setup()
    fireEvent.change(screen.getByLabelText('Name (required)'), {
      target: { value: 'Changed' },
    })
    await user.click(screen.getByRole('button', { name: 'Save' }))
    await screen.findByRole('alert')
    await user.click(screen.getByRole('button', { name: 'Back' }))
    await user.click(
      screen.getByRole('button', { name: 'Stay and keep editing' }),
    )
    server.use(
      http.patch(endpoint, () =>
        HttpResponse.json({
          data: {
            ...form,
            attributes: {
              ...form.attributes,
              version: 2,
              answers: { name: 'Changed' },
            },
          },
        }),
      ),
    )
    await user.click(screen.getByRole('button', { name: 'Save' }))
    await screen.findByText('Your answers have been saved.')
    await user.click(screen.getByRole('button', { name: 'Back' }))
    expect(
      await screen.findByRole('heading', { name: 'Current draft' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })
})
