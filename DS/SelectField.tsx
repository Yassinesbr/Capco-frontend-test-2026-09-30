import {
  Button,
  Label,
  ListBox,
  ListBoxItem,
  Popover,
  Select,
  SelectValue,
} from 'react-aria-components'

import styles from './SelectField.module.css'

export interface SelectFieldOption {
  value: string
  label: string
}
export interface SelectFieldProps {
  id: string
  label: string
  value: string
  options: SelectFieldOption[]
  onChange: (value: string) => void
}

export function SelectField({
  id,
  label,
  value,
  options,
  onChange,
}: SelectFieldProps) {
  return (
    <Select
      id={id}
      className={styles.select}
      selectedKey={value}
      onSelectionChange={(key) => onChange(String(key))}
    >
      <Label>{label}</Label>
      <Button className={styles.trigger}>
        <SelectValue /> <span aria-hidden="true">⌄</span>
      </Button>
      <Popover className={styles.popover}>
        <ListBox className={styles.list}>
          {options.map((option) => (
            <ListBoxItem
              className={styles.option}
              key={option.value}
              id={option.value}
            >
              {option.label}
            </ListBoxItem>
          ))}
        </ListBox>
      </Popover>
    </Select>
  )
}
