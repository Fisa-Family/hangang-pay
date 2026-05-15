import { Button } from './Button'

interface ConfirmDialogProps {
  open: boolean
  title: string
  description?: string
  confirmText: string
  cancelText: string
  variant?: 'default' | 'danger'
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({
  open,
  title,
  description,
  confirmText,
  cancelText,
  variant = 'default',
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  if (!open) {
    return null
  }

  return (
    <div className="absolute inset-0 z-30 flex items-end bg-foreground/45 px-4 pb-4">
      <section className="w-full rounded-lg bg-card p-5 shadow-xl">
        <h2 className="text-lg font-bold text-card-foreground">{title}</h2>
        {description ? (
          <p className="mt-2 text-sm leading-6 text-muted-foreground">{description}</p>
        ) : null}
        <div className="mt-5 grid grid-cols-2 gap-2">
          <Button variant="secondary" onClick={onCancel}>
            {cancelText}
          </Button>
          <Button variant={variant === 'danger' ? 'danger' : 'primary'} onClick={onConfirm}>
            {confirmText}
          </Button>
        </div>
      </section>
    </div>
  )
}
