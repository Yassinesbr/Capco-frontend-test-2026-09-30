import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Button } from './Button'

describe('Button', () => {
  it('renders an accessible React Aria button', () => {
    render(<Button>Continue</Button>)
    expect(screen.getByRole('button', { name: 'Continue' })).toBeDefined()
  })
})
