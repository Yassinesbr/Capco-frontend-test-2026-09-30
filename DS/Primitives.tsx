import {
  useId,
  type ComponentPropsWithoutRef,
  type ElementType,
  type InputHTMLAttributes,
  type ReactNode,
  type Ref,
} from 'react'
import {
  Cell,
  Column,
  Form as AriaForm,
  Group,
  Input,
  Label,
  Link as AriaLink,
  Row,
  Table as AriaTable,
  TableBody as AriaTableBody,
  TableHeader,
  Text,
  TextField as AriaTextField,
  type CellProps,
  type ColumnProps,
  type FormProps,
  type GroupProps,
  type RowProps,
  type TableBodyProps,
  type TableHeaderProps,
  type TableProps,
} from 'react-aria-components'

import styles from './Primitives.module.css'

type BoxProps = Omit<
  ComponentPropsWithoutRef<'div'>,
  'role' | 'style' | 'onSubmit'
> & { component?: ElementType }
export function Box({
  component: Component = 'div',
  className,
  ...props
}: BoxProps) {
  if (Component === 'div') {
    return (
      <Group
        role="presentation"
        className={[styles.box, className].filter(Boolean).join(' ')}
        {...props}
      />
    )
  }
  return (
    <Component
      className={[styles.box, className].filter(Boolean).join(' ')}
      {...props}
    />
  )
}

type Direction =
  'row' | 'column' | { xs?: 'row' | 'column'; sm?: 'row' | 'column' }
type StackProps = BoxProps &
  Pick<FormProps, 'onSubmit'> & { spacing?: number; direction?: Direction }
export function Stack({
  component: Component = 'div',
  spacing = 1,
  direction = 'column',
  className,
  onSubmit,
  ...props
}: StackProps) {
  const gapClass =
    styles[`gap${String(spacing).replace('.', '_')}`] ?? styles.gap1
  let directionClass = ''
  if (typeof direction === 'string') {
    if (direction === 'row') {
      directionClass = styles.row
    }
  } else if (direction.sm === 'row') {
    directionClass = styles.responsiveRow
  } else if (direction.xs === 'row') {
    directionClass = styles.row
  }
  const stackClass = [styles.stack, gapClass, directionClass, className]
    .filter(Boolean)
    .join(' ')
  if (Component === 'div') {
    const role =
      'aria-label' in props || 'aria-labelledby' in props
        ? 'group'
        : 'presentation'
    return <Group role={role} className={stackClass} {...props} />
  }
  if (Component === 'form') {
    return (
      <AriaForm
        className={stackClass}
        validationBehavior="aria"
        onSubmit={onSubmit}
        {...(props as FormProps)}
      />
    )
  }
  return <Component className={stackClass} {...props} />
}

export type CardProps = Omit<GroupProps, 'className' | 'role'> & {
  className?: string
}
export function Card({ className, ...props }: CardProps) {
  return (
    <Group
      role="presentation"
      className={[styles.card, className].filter(Boolean).join(' ')}
      {...props}
    />
  )
}
Card.displayName = 'Card'

type CardContentProps = ComponentPropsWithoutRef<'div'>
export function CardContent({ className, ...props }: CardContentProps) {
  return (
    <div
      className={[styles.cardContent, className].filter(Boolean).join(' ')}
      {...props}
    />
  )
}

type TypographyProps = Omit<
  ComponentPropsWithoutRef<'span'>,
  'color' | 'style'
> & {
  component?: string
  variant?:
    'display' | 'heading' | 'subheading' | 'body' | 'caption' | 'eyebrow'
  tone?: 'default' | 'muted' | 'accent'
  align?: 'left' | 'center' | 'right'
}
const defaultElementTypes = {
  display: 'h1',
  heading: 'h2',
  subheading: 'h3',
  body: 'p',
  caption: 'p',
  eyebrow: 'p',
} as const
export function Typography({
  component,
  variant = 'body',
  tone = 'default',
  align,
  className,
  ...props
}: TypographyProps) {
  const elementType = component ?? defaultElementTypes[variant]
  return (
    <Text
      elementType={elementType}
      className={[
        styles.text,
        styles[variant],
        tone !== 'default' && styles[tone],
        align === 'center' && styles.center,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      {...props}
    />
  )
}

type AlertProps = Omit<ComponentPropsWithoutRef<'div'>, 'style'> & {
  severity?: 'error' | 'success' | 'info'
}
export function Alert({ severity = 'info', className, ...props }: AlertProps) {
  return (
    <Text
      elementType="div"
      role={severity === 'error' ? 'alert' : 'status'}
      className={[
        styles.alert,
        styles[`alert${severity[0]!.toUpperCase()}${severity.slice(1)}`],
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      {...props}
    />
  )
}

export type TextFieldProps = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'className' | 'size' | 'style'
> & {
  label: ReactNode
  helperText?: ReactNode
  error?: boolean
  fullWidth?: boolean
  inputRef?: Ref<HTMLInputElement>
  className?: string
  inputClassName?: string
}
export function TextField({
  label,
  helperText,
  error = false,
  fullWidth = false,
  inputRef,
  id,
  className,
  inputClassName,
  ...props
}: TextFieldProps) {
  const generatedId = useId()
  return (
    <AriaTextField
      id={id ?? generatedId}
      isInvalid={error}
      isDisabled={props.disabled}
      className={[styles.textField, fullWidth && styles.fullWidth, className]
        .filter(Boolean)
        .join(' ')}
    >
      <Label>{label}</Label>
      <Input
        {...props}
        ref={inputRef}
        className={[styles.input, inputClassName].filter(Boolean).join(' ')}
      />
      {Boolean(helperText) && (
        <Text
          slot={error ? 'errorMessage' : 'description'}
          className={styles.description}
        >
          {helperText}
        </Text>
      )}
    </AriaTextField>
  )
}

type LinkProps = Omit<ComponentPropsWithoutRef<typeof AriaLink>, 'style'>
export function Link(props: LinkProps) {
  return <AriaLink {...props} />
}

export function Table({ className, ...props }: Omit<TableProps, 'style'>) {
  return (
    <AriaTable
      className={[styles.table, className].filter(Boolean).join(' ')}
      {...props}
    />
  )
}
export function TableHead({
  className,
  ...props
}: Omit<TableHeaderProps<unknown>, 'style'>) {
  return (
    <TableHeader
      className={[styles.tableHeader, className].filter(Boolean).join(' ')}
      {...props}
    />
  )
}
export function TableBody({
  ...props
}: Omit<TableBodyProps<unknown>, 'style'>) {
  return <AriaTableBody {...props} />
}
export function TableRow({
  className,
  ...props
}: Omit<RowProps<unknown>, 'style'>) {
  return <Row className={className} {...props} />
}
export function TableCell({ className, ...props }: Omit<CellProps, 'style'>) {
  return (
    <Cell
      className={[styles.tableCell, className].filter(Boolean).join(' ')}
      {...props}
    />
  )
}
export function TableHeaderCell({
  className,
  ...props
}: Omit<ColumnProps, 'style'>) {
  return (
    <Column
      className={[styles.tableCell, styles.tableHeadCell, className]
        .filter(Boolean)
        .join(' ')}
      {...props}
    />
  )
}
