import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

interface PageHeaderProps {
  title: string
  onBack?: () => void
  rightAction?: ReactNode
  className?: string
}

export function PageHeader({ title, onBack, rightAction, className }: PageHeaderProps) {
  return (
    <header className={cn('grid h-12 grid-cols-[44px_1fr_44px] items-center', className)}>
      <div>
        {onBack ? (
          <button
            type="button"
            aria-label="뒤로가기"
            onClick={onBack}
            className="flex size-11 items-center justify-center rounded-lg text-2xl text-foreground hover:bg-muted"
          >
            ‹
          </button>
        ) : null}
      </div>
      <h1 className="truncate text-center text-base font-semibold">{title}</h1>
      <div className="flex justify-end">{rightAction}</div>
    </header>
  )
}
