import { useState } from 'react'

import { Button } from './Button'
import {
  Alert,
  Box,
  Card,
  CardContent,
  Link,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeaderCell,
  TableRow,
  TextField,
  Typography,
} from './Primitives'
import { SelectField } from './SelectField'

import type { Meta, StoryObj } from '@storybook/react-vite'

const meta = {
  title: 'Design System/Components',
  component: Button,
  tags: ['autodocs'],
} satisfies Meta<typeof Button>
export default meta
type Story = StoryObj<typeof meta>

export const ButtonPrimary: Story = {
  name: 'Button / primary',
  render: () => <Button>Continue</Button>,
}
export const ButtonSecondary: Story = {
  name: 'Button / secondary',
  render: () => <Button variant="secondary">Save draft</Button>,
}
export const ButtonQuiet: Story = {
  name: 'Button / quiet',
  render: () => <Button variant="quiet">Cancel</Button>,
}
export const ButtonDisabled: Story = {
  name: 'Button / disabled',
  render: () => <Button isDisabled>Unavailable</Button>,
}
export const Select: Story = {
  name: 'SelectField',
  render: () => <SelectStory />,
}
export const TextInput: Story = {
  name: 'TextField',
  render: () => (
    <TextField
      label="Email address"
      type="email"
      placeholder="you@example.com"
    />
  ),
}
export const TextInputInvalid: Story = {
  name: 'TextField / invalid',
  render: () => (
    <TextField label="Tax ID" error helperText="Enter a valid tax ID." />
  ),
}
export const AlertInfo: Story = {
  name: 'Alert / info',
  render: () => <Alert severity="info">Applications are being reviewed.</Alert>,
}
export const AlertError: Story = {
  name: 'Alert / error',
  render: () => <Alert severity="error">Check the highlighted fields.</Alert>,
}
export const AlertSuccess: Story = {
  name: 'Alert / success',
  render: () => <Alert severity="success">Application saved.</Alert>,
}
export const CardSurface: Story = {
  name: 'Card',
  render: () => (
    <Card>
      <CardContent>
        <Typography component="h2" variant="subheading">
          Application details
        </Typography>
        <Typography tone="muted">Submitted today</Typography>
      </CardContent>
    </Card>
  ),
}
export const Layout: Story = {
  name: 'Box and Stack',
  render: () => (
    <Box>
      <Stack spacing={2}>
        <Typography component="h2" variant="subheading">
          Review queue
        </Typography>
        <Typography>Three applications need review.</Typography>
        <Button>Open queue</Button>
      </Stack>
    </Box>
  ),
}
export const TypographyText: Story = {
  name: 'Typography',
  render: () => (
    <Stack spacing={1}>
      <Typography component="h1" variant="heading">
        Application submitted
      </Typography>
      <Typography tone="muted">The review team will contact you.</Typography>
    </Stack>
  ),
}
export const NavigationLink: Story = {
  name: 'Link',
  render: () => <Link href="#applications">View applications</Link>,
}
export const DataTable: Story = {
  name: 'Table',
  render: () => (
    <Table aria-label="Applications">
      <TableHead>
        <TableHeaderCell id="applicant" isRowHeader>
          Applicant
        </TableHeaderCell>
        <TableHeaderCell id="status">Status</TableHeaderCell>
      </TableHead>
      <TableBody>
        <TableRow id="ada-lovelace">
          <TableCell>Ada Lovelace</TableCell>
          <TableCell>In review</TableCell>
        </TableRow>
      </TableBody>
    </Table>
  ),
}

function SelectStory() {
  const [value, setValue] = useState('submitted')
  return (
    <SelectField
      id="application-status"
      label="Status"
      value={value}
      onChange={setValue}
      options={[
        { value: 'submitted', label: 'Submitted' },
        { value: 'in-review', label: 'In review' },
        { value: 'approved', label: 'Approved' },
      ]}
    />
  )
}
