import { formatWon } from '@/lib/format'
import { cn } from '@/lib/utils'

interface BalanceCardProps {
  balance: number
  label?: string
  refreshing?: boolean
  onRefresh?: () => void
  className?: string
}

export function BalanceCard({
  balance,
  label = '잔액',
  refreshing = false,
  onRefresh,
  className,
}: BalanceCardProps) {
  return (
    <section className={cn('rounded-2xl bg-card p-5 shadow-sm', className)}>
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-1.5">
          <p className="text-sm font-medium text-muted-foreground">{label}</p>
          {onRefresh ? (
            <button
              type="button"
              onClick={onRefresh}
              disabled={refreshing}
              className="flex h-6 w-6 items-center justify-center rounded-full text-muted-foreground hover:bg-muted disabled:opacity-45"
              aria-label="새로고침"
            >
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className={cn('h-3.5 w-3.5', refreshing && 'animate-spin')}
                aria-hidden
              >
                <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8" />
                <path d="M21 3v5h-5" />
                <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16" />
                <path d="M8 16H3v5" />
              </svg>
            </button>
          ) : null}
        </div>
        <p className="text-2xl font-bold tabular-nums">{formatWon(balance)}</p>
      </div>
    </section>
  )
}
