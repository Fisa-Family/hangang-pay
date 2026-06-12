import { RefreshCw } from 'lucide-react'
import { formatWon } from '@/lib/format'
import { cn } from '@/lib/utils'
import { GradientCard } from './GradientCard'

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
    <GradientCard className={cn('p-5', className)}>
      <div className="relative flex items-center justify-between gap-3">
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
              <RefreshCw className={cn('h-3.5 w-3.5', refreshing && 'animate-spin')} aria-hidden />
            </button>
          ) : null}
        </div>
        <p className="text-2xl font-bold tabular-nums">{formatWon(balance)}</p>
      </div>
    </GradientCard>
  )
}
