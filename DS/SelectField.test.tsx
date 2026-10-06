import { fireEvent, render, screen } from '@testing-library/react'
import { axe, toHaveNoViolations } from 'jest-axe'
import { describe, expect, it, vi } from 'vitest'

import { SelectField } from './SelectField'

expect.extend(toHaveNoViolations)

describe('SelectField', () => {
  const options = [
    { value: 'all', label: 'All statuses' },
    { value: 'approved', label: 'Approved' },
  ]

  it('renders selected label and reports option changes', async () => {
    const onChange = vi.fn()
    render(
      <SelectField
        id="status"
        label="Status"
        value="all"
        options={options}
        onChange={onChange}
      />,
    )
    const trigger = screen.getByRole('button', { name: /Status/ })
    expect(trigger.textContent).toContain('All statuses')
    fireEvent.click(trigger)
    fireEvent.click(await screen.findByRole('option', { name: 'Approved' }))
    expect(onChange).toHaveBeenCalledWith('approved')
  })

  it('has no automated accessibility violations', async () => {
    const { container } = render(
      <SelectField
        id="status"
        label="Status"
        value="all"
        options={options}
        onChange={() => {}}
      />,
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
