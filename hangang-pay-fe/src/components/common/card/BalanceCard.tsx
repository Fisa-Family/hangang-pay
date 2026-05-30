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
  label = '현재 잔액',
  refreshing = false,
  onRefresh,
  className,
}: BalanceCardProps) {
  return (
    <section className={cn('rounded-lg border border-border bg-card p-4', className)}>
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm font-medium text-muted-foreground">{label}</p>
        {onRefresh ? (
          <button
            type="button"
            onClick={onRefresh}
            disabled={refreshing}
            className="rounded-md px-2 py-1 text-xs font-semibold text-primary hover:bg-accent disabled:opacity-45"
          >
            {refreshing ? '새로고침 중' : '새로고침'}
          </button>
        ) : null}
      </div>
      <p className="mt-2 text-right text-2xl font-bold tabular-nums">{formatWon(balance)}</p>
    </section>
  )
}
