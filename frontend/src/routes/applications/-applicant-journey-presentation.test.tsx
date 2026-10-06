import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe, toHaveNoViolations } from 'jest-axe'
import { describe, expect, it, vi } from 'vitest'

import {
  ApplicantJourneyPresentation,
  applicantJourneyFixtures,
  type ApplicantJourneyPresentationFixture,
} from './-applicant-journey-presentation'

expect.extend(toHaveNoViolations)

function renderJourney(
  fixture: ApplicantJourneyPresentationFixture,
  onPrimaryAction = vi.fn(),
) {
  return {
    onPrimaryAction,
    ...render(
      <ApplicantJourneyPresentation
        {...fixture}
        onPrimaryAction={onPrimaryAction}
      />,
    ),
  }
}

describe('route-colocated ApplicantJourneyPresentation', () => {
  it('presents the Penpot start state with a named start action and truthful progress text', () => {
    renderJourney(applicantJourneyFixtures.start)

    expect(
      screen.getByRole('heading', {
        level: 1,
        name: 'Start your KYC application',
      }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Start application' }),
    ).toHaveAccessibleDescription(
      /two short steps.*Personal details.*Current step.*Identity and address.*Remaining/i,
    )
    expect(
      screen.queryByRole('button', { name: 'Resume application' }),
    ).not.toBeInTheDocument()
    expect(
      screen.getByRole('list', { name: 'Application progress' }),
    ).toHaveTextContent(
      '1Personal detailsCurrent step2Identity and addressRemaining',
    )
    expect(screen.getByText('Personal details').closest('li')).toHaveAttribute(
      'aria-current',
      'step',
    )
  })

  it('presents the draft resume state without a competing new application action', () => {
    renderJourney(applicantJourneyFixtures.resume)

    expect(
      screen.getByRole('heading', {
        level: 1,
        name: 'Resume your KYC application',
      }),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/Your application is still a draft\./),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Resume application' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Start application' }),
    ).not.toBeInTheDocument()
  })

  it('keeps the primary action keyboard-operable and focused', async () => {
    const user = userEvent.setup()
    const { onPrimaryAction } = renderJourney(applicantJourneyFixtures.start)

    await user.tab()
    const action = screen.getByRole('button', { name: 'Start application' })
    expect(action).toHaveFocus()
    await user.keyboard('{Enter}')

    expect(onPrimaryAction).toHaveBeenCalledOnce()
    expect(action).toHaveFocus()
  })

  it('communicates complete and current progress without visual-only meaning', async () => {
    const fixture: ApplicantJourneyPresentationFixture = {
      entryState: 'resume',
      progress: [
        { step: 'personal-details', state: 'complete' },
        { step: 'identity-and-address', state: 'current' },
      ],
    }
    const { container } = renderJourney(fixture)

    expect(
      screen.getByRole('list', { name: 'Application progress' }),
    ).toHaveTextContent(/Complete.*Current step/)
    expect(await axe(container)).toHaveNoViolations()
  })
})
