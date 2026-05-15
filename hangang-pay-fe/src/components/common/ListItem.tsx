import type { ReactNode } from 'react'
import { formatWon } from '@/lib/format'
import { cn } from '@/lib/utils'

interface ListItemProps {
  title: string
  description?: string
  amount?: number
  badge?: ReactNode
  rightAction?: ReactNode
  onClick?: () => void
  disabled?: boolean
}

export function ListItem({
  title,
  description,
  amount,
  badge,
  rightAction,
  onClick,
  disabled = false,
}: ListItemProps) {
  const Component = onClick ? 'button' : 'div'

  return (
    <Component
      type={onClick ? 'button' : undefined}
      onClick={onClick}
      disabled={onClick ? disabled : undefined}
      className={cn(
        'flex min-h-16 w-full items-center gap-3 rounded-lg border border-border bg-card px-4 text-left',
        onClick && 'transition-colors hover:bg-surface',
        disabled && 'opacity-45'
      )}
    >
      <div className="min-w-0 flex-1">
        <div className="flex min-w-0 items-center gap-2">
          <p className="truncate text-sm font-semibold text-foreground">{title}</p>
          {badge}
        </div>
        {description ? (
          <p className="mt-1 truncate text-xs text-muted-foreground">{description}</p>
        ) : null}
      </div>
      {typeof amount === 'number' ? (
        <p className="shrink-0 text-right text-sm font-bold tabular-nums">{formatWon(amount)}</p>
      ) : null}
      {rightAction ? <div className="shrink-0">{rightAction}</div> : null}
    </Component>
  )
}
