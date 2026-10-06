import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, describe, it, vi } from 'vitest'

import {
  parseReviewerApplicationsSearch,
  ReviewerApplicationsView,
  type ReviewerApplicationsResponse,
} from './applications'

const response: ReviewerApplicationsResponse = {
  kind: 'found',
  result: {
    summary: { pendingReview: 2, inProgress: 3, approvedThisMonth: 1 },
    pagination: { page: 0, pageSize: 10, totalItems: 1, totalPages: 1 },
    applications: [
      {
        id: '0199a8bb-8687-7c22-aed5-55d432f50f23',
        applicantName: 'Ada Lovelace',
        submittedAt: '2026-09-24T10:30:00Z',
        status: 'in-review',
      },
    ],
  },
}

describe('Reviewer applications overview', () => {
  it('validates route search values and supplies first-page defaults', () => {
    expect(
      parseReviewerApplicationsSearch({
        q: 'Ada',
        status: 'approved',
        page: 2,
      }),
    ).toEqual({ q: 'Ada', status: 'approved', page: 2 })
    expect(
      parseReviewerApplicationsSearch({
        q: 'x'.repeat(121),
        status: 'unknown',
        page: -1,
      }),
    ).toEqual({ q: '', status: 'all', page: 0 })
    expect(parseReviewerApplicationsSearch({})).toEqual({
      q: '',
      status: 'all',
      page: 0,
    })
  })

  it('shows workload metrics, readable status, submission date, and detail action', () => {
    render(
      <ReviewerApplicationsView
        response={response}
        q=""
        status="all"
        page={0}
        onSearch={vi.fn()}
        onStatus={vi.fn()}
        onPage={vi.fn()}
      />,
    )

    expect(
      screen.getByRole('heading', { name: 'Applications' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Pending review').parentElement).toHaveTextContent(
      '2',
    )
    expect(screen.getByText('In progress').parentElement).toHaveTextContent('3')
    expect(
      screen.getByText('Approved this month').parentElement,
    ).toHaveTextContent('1')
    expect(screen.getAllByText('In review')).toHaveLength(2)
    expect(screen.getByText('Sep 24, 2026')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View' })).toHaveAttribute(
      'href',
      `/reviewer/applications/${response.kind === 'found' ? response.result.applications[0]!.id : ''}`,
    )
  })

  it('keeps search, status, and pagination controls keyboard accessible', async () => {
    const user = userEvent.setup()
    const onSearch = vi.fn()
    const onStatus = vi.fn()
    const onPage = vi.fn()
    const multiPage: ReviewerApplicationsResponse = {
      ...response,
      result: {
        ...response.result,
        pagination: {
          ...response.result.pagination,
          totalItems: 21,
          totalPages: 3,
        },
      },
    }
    render(
      <ReviewerApplicationsView
        response={multiPage}
        q=""
        status="all"
        page={0}
        onSearch={onSearch}
        onStatus={onStatus}
        onPage={onPage}
      />,
    )

    await user.type(screen.getByRole('textbox', { name: 'Search' }), 'Ada')
    await user.click(
      screen.getByRole('button', { name: 'All statuses Status' }),
    )
    await user.click(screen.getByRole('option', { name: 'In review' }))
    await user.click(screen.getByRole('button', { name: 'Next page' }))
    expect(onSearch).toHaveBeenLastCalledWith('a')
    expect(onStatus).toHaveBeenCalledWith('in-review')
    expect(onPage).toHaveBeenCalledWith(1)
  })

  it('explains empty results and lets staff clear filters', async () => {
    const user = userEvent.setup()
    const onSearch = vi.fn()
    const onStatus = vi.fn()
    const empty: ReviewerApplicationsResponse = {
      ...response,
      result: { ...response.result, applications: [] },
    }
    render(
      <ReviewerApplicationsView
        response={empty}
        q="Ada"
        status="approved"
        page={0}
        onSearch={onSearch}
        onStatus={onStatus}
        onPage={vi.fn()}
      />,
    )
    expect(
      screen.getByText('No applications match these filters.'),
    ).toBeInTheDocument()
    await user.click(
      screen.getByRole('button', { name: 'Clear search and status' }),
    )
    expect(onSearch).toHaveBeenCalledWith('')
    expect(onStatus).toHaveBeenCalledWith('all')
  })

  it('shows denial and API error without application data', () => {
    const denied = render(
      <ReviewerApplicationsView
        response={{ kind: 'denied' }}
        q=""
        status="all"
        page={0}
        onSearch={vi.fn()}
        onStatus={vi.fn()}
        onPage={vi.fn()}
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent(
      'You do not have access',
    )
    expect(screen.queryByText('Ada Lovelace')).not.toBeInTheDocument()
    denied.unmount()
    render(
      <ReviewerApplicationsView
        response={{ kind: 'error' }}
        q=""
        status="all"
        page={0}
        onSearch={vi.fn()}
        onStatus={vi.fn()}
        onPage={vi.fn()}
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Applications could not be loaded',
    )
  })
})
