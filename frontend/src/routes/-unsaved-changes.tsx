import { ConfirmationDialog, Typography } from '@kyc/ui'
import {
  createContext,
  useCallback,
  useContext,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { useBeforeUnload, useBlocker } from 'react-router'

const UnsavedChangesContext = createContext<{
  setDirty: (dirty: boolean) => void
  requestLeave: (action: () => void) => void
}>({
  setDirty: () => undefined,
  requestLeave: (action: () => void) => action(),
})

export const useUnsavedChanges = () => useContext(UnsavedChangesContext)

export function UnsavedChangesProvider({ children }: { children: ReactNode }) {
  const dirty = useRef(false)
  const [pendingAction, setPendingAction] = useState<(() => void) | null>(null)
  const setDirty = useCallback((value: boolean) => {
    dirty.current = value
  }, [])
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      dirty.current && currentLocation.pathname !== nextLocation.pathname,
  )
  useBeforeUnload(
    useCallback((event: BeforeUnloadEvent) => {
      if (dirty.current) {
        event.preventDefault()
        event.returnValue = ''
      }
    }, []),
  )
  const requestLeave = useCallback((action: () => void) => {
    if (dirty.current) setPendingAction(() => action)
    else action()
  }, [])
  const cancel = () => {
    setPendingAction(null)
    if (blocker.state === 'blocked') blocker.reset()
  }
  const confirm = () => {
    if (pendingAction) {
      const action = pendingAction
      setPendingAction(null)
      action()
    } else if (blocker.state === 'blocked') {
      setDirty(false)
      blocker.proceed()
    }
  }
  return (
    <UnsavedChangesContext.Provider value={{ setDirty, requestLeave }}>
      {children}
      <ConfirmationDialog
        isOpen={Boolean(pendingAction) || blocker.state === 'blocked'}
        title="Leave without saving?"
        cancelLabel="Stay and keep editing"
        confirmLabel={
          pendingAction ? 'Sign out without saving' : 'Leave without saving'
        }
        onCancel={cancel}
        onConfirm={confirm}
      >
        <Typography>
          Your unsaved answers and any selected document that has not been
          uploaded will be lost. Stay to save your work first.
        </Typography>
      </ConfirmationDialog>
    </UnsavedChangesContext.Provider>
  )
}
