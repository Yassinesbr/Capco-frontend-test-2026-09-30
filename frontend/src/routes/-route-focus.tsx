import { useEffect } from 'react'
import { useLocation } from 'react-router'

export function RouteFocus() {
  const { pathname } = useLocation()
  useEffect(() => {
    // A query-backed route may render its heading after its loading state.
    const focusHeading = () => {
      const main = document.querySelector('main')
      if (main) main.id = 'main-content'
      const heading = document.querySelector<HTMLElement>('main h1')
      if (!heading) return false
      heading.tabIndex = -1
      heading.focus()
      return true
    }
    if (focusHeading()) return undefined
    const observer = new MutationObserver(() => {
      if (focusHeading()) observer.disconnect()
    })
    observer.observe(document.body, { childList: true, subtree: true })
    return () => observer.disconnect()
  }, [pathname])
  return null
}
