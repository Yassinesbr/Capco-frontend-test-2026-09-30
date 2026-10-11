import { useId, type ReactNode } from 'react'
import { Dialog, Heading, Modal, ModalOverlay } from 'react-aria-components'

import { Button } from './Button'
import styles from './ConfirmationDialog.module.css'
import { Stack } from './Primitives'

export interface ConfirmationDialogProps {
  isOpen: boolean
  title: string
  children: ReactNode
  confirmLabel: string
  cancelLabel?: string
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmationDialog({
  isOpen,
  title,
  children,
  confirmLabel,
  cancelLabel = 'Cancel',
  onConfirm,
  onCancel,
}: ConfirmationDialogProps) {
  const descriptionId = useId()
  return (
    <ModalOverlay
      isOpen={isOpen}
      onOpenChange={(open) => {
        if (!open) onCancel()
      }}
      className={styles.overlay}
    >
      <Modal className={styles.modal}>
        <Dialog
          role="alertdialog"
          aria-describedby={descriptionId}
          className={styles.dialog}
        >
          <Heading slot="title">{title}</Heading>
          <div id={descriptionId}>{children}</div>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <Button autoFocus variant="secondary" onPress={onCancel}>
              {cancelLabel}
            </Button>
            <Button onPress={onConfirm}>{confirmLabel}</Button>
          </Stack>
        </Dialog>
      </Modal>
    </ModalOverlay>
  )
}
