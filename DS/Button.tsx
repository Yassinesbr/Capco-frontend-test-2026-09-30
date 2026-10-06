import { forwardRef } from 'react'
import {
  Button as AriaButton,
  type ButtonProps as AriaButtonProps,
} from 'react-aria-components'

import styles from './Button.module.css'

export type ButtonProps = Omit<AriaButtonProps, 'className' | 'style'> & {
  className?: string
  variant?: 'primary' | 'secondary' | 'quiet'
  disabled?: boolean
  fullWidth?: boolean
  size?: 'small' | 'medium' | 'large'
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  function Button(
    {
      variant = 'primary',
      disabled,
      isDisabled,
      fullWidth = false,
      size = 'medium',
      className,
      ...props
    },
    ref,
  ) {
    return (
      <AriaButton
        ref={ref}
        isDisabled={isDisabled ?? disabled}
        className={[
          styles.button,
          styles[variant],
          size !== 'medium' && styles[size],
          fullWidth && styles.fullWidth,
          className,
        ]
          .filter(Boolean)
          .join(' ')}
        {...props}
      />
    )
  },
)
