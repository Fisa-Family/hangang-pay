import { cn } from '@/lib/utils'
import { Button } from './Button'
import { StatusBadge } from './StatusBadge'

interface AccountRowProps {
  bankName: string
  maskedAccountNumber: string
  holderName?: string
  primary?: boolean
  selected?: boolean
  mode: 'select' | 'manage'
  onSelect?: () => void
  onSetPrimary?: () => void
  onDelete?: () => void
}

export function AccountRow({
  bankName,
  maskedAccountNumber,
  holderName,
  primary = false,
  selected = false,
  mode,
  onSelect,
  onSetPrimary,
  onDelete,
}: AccountRowProps) {
  return (
    <section
      className={cn(
        'rounded-lg border bg-card p-4',
        selected ? 'border-primary ring-2 ring-primary/15' : 'border-border'
      )}
    >
      <div className="flex items-start gap-3">
        {mode === 'select' ? (
          <button
            type="button"
            aria-label="계좌 선택"
            onClick={onSelect}
            className={cn(
              'mt-1 size-5 rounded-full border',
              selected ? 'border-primary bg-primary shadow-inner' : 'border-input bg-background'
            )}
          />
        ) : null}
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <p className="truncate text-sm font-bold">{bankName}</p>
            {primary ? <StatusBadge variant="success">주거래</StatusBadge> : null}
          </div>
          <p className="mt-1 text-sm tabular-nums text-muted-foreground">{maskedAccountNumber}</p>
          {holderName ? <p className="mt-1 text-xs text-muted-foreground">{holderName}</p> : null}
        </div>
      </div>
      {mode === 'manage' ? (
        <div className="mt-4 grid grid-cols-2 gap-2">
          <Button variant="secondary" onClick={onSetPrimary} disabled={primary}>
            주거래 변경
          </Button>
          <Button variant="ghost" onClick={onDelete}>
            삭제
          </Button>
        </div>
      ) : null}
    </section>
  )
}
