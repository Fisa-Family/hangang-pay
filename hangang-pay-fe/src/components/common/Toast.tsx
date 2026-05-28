import { cn } from '@/lib/utils'

export type ToastVariant = 'success' | 'error'

export interface ToastState {
  message: string
  variant: ToastVariant
}

interface ToastProps {
  open: boolean
  message: string
  variant?: ToastVariant
  actionLabel?: string
  onAction?: () => void
  className?: string
}

const toastVariantClasses: Record<ToastVariant, string> = {
  success: 'border-primary/25 text-foreground',
  error: 'border-destructive/25 text-destructive',
}

export function Toast({
  open,
  message,
  variant = 'success',
  actionLabel,
  onAction,
  className,
}: ToastProps) {
  return (
<div
  role={variant === 'error' ? 'alert' : 'status'}
  className={cn(
    'pointer-events-none absolute right-5 bottom-[calc(env(safe-area-inset-bottom)+5.5rem)] left-5 z-20 rounded-lg border bg-card/75 px-4 py-3 text-sm font-semibold shadow-lg shadow-foreground/10 backdrop-blur-md',
    'transition-all duration-300 ease-out',
    open
      ? 'translate-y-0 opacity-100'
      : 'translate-y-3 opacity-0',
    toastVariantClasses[variant],
    className
  )}
>
  <div className="flex items-center justify-between gap-3">
    <span className="flex-1">{message}</span>

    {actionLabel && onAction ? (
<button
  type="button"
  onClick={onAction}
  className={cn(
    'pointer-events-auto shrink-0 font-semibold transition-colors hover:text-foreground',
    variant === 'error'
      ? 'text-destructive hover:text-destructive/80'
      : 'text-muted-foreground hover:text-foreground'
  )}
>
  {actionLabel}
</button>
    ) : null}
  </div>
</div>
    
  )
}