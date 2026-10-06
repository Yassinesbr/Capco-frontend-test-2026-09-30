export interface AccountAccessSearch {
  returnTo?: string
}

/** Restricts post-authentication navigation to local applicant journey paths. */
export function parseAccountAccessSearch(
  search: Record<string, unknown>,
): AccountAccessSearch {
  const returnTo = search.returnTo
  return typeof returnTo === 'string' && isSafeJourneyPath(returnTo)
    ? { returnTo }
    : {}
}

export function isSafeJourneyPath(path: string): boolean {
  return path.startsWith('/applications/') && !path.startsWith('//')
}

export function accountAccessHref(
  path: '/sign-in' | '/create-account',
  returnTo?: string,
): string {
  return returnTo ? `${path}?returnTo=${encodeURIComponent(returnTo)}` : path
}
