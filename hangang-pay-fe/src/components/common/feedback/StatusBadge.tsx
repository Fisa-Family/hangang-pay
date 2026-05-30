import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

type StatusBadgeVariant = 'success' | 'warning' | 'danger' | 'neutral'

interface StatusBadgeProps {
  children: ReactNode
  variant?: StatusBadgeVariant
  className?: string
}

const variantClasses: Record<StatusBadgeVariant, string> = {
  success: 'bg-success/10 text-success',
  warning: 'bg-warning/10 text-warning',
  danger: 'bg-destructive/10 text-destructive',
  neutral: 'bg-muted text-muted-foreground',
}

export function StatusBadge({ children, variant = 'neutral', className }: StatusBadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex h-6 shrink-0 items-center rounded-md px-2 text-xs font-semibold whitespace-nowrap',
        variantClasses[variant],
        className
      )}
    >
      {children}
    </span>
  )
}
