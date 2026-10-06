import { useNavigate } from 'react-router'

import { parseAccountAccessSearch } from './-return-target'
import { SignInForm } from './-sign-in-form'

import type { Route } from './+types/sign-in'

export function clientLoader({ request }: Route.ClientLoaderArgs) {
  const search = Object.fromEntries(new URL(request.url).searchParams)
  return parseAccountAccessSearch(search)
}

export default function SignInRoute({ loaderData }: Route.ComponentProps) {
  const navigate = useNavigate()
  return (
    <SignInForm
      onNavigate={(href) => {
        void navigate(href)
      }}
      onSuccess={(href) => {
        void navigate(href)
      }}
      returnTo={loaderData.returnTo}
    />
  )
}
