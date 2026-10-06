import { redirect } from 'react-router'

export function clientLoader() {
  return redirect('/create-account')
}

export default function IndexRoute() {
  return null
}
