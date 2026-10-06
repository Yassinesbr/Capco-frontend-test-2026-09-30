import { useNavigate } from 'react-router'

import { CreateAccountForm } from './-create-account-form'
import { parseAccountAccessSearch } from './-return-target'

import type { Route } from './+types/create-account'

export function clientLoader({ request }: Route.ClientLoaderArgs) {
  const search = Object.fromEntries(new URL(request.url).searchParams)
  return parseAccountAccessSearch(search)
}

export default function CreateAccountRoute({
  loaderData,
}: Route.ComponentProps) {
  const navigate = useNavigate()
  return (
    <CreateAccountForm
      onNavigate={(href) => {
        void navigate(href)
      }}
      returnTo={loaderData.returnTo}
    />
  )
}
