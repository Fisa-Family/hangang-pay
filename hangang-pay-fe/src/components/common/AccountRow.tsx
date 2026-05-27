import { cn } from '@/lib/utils'
import { Button } from './Button'

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
  onSetPrimary,
  onDelete,
}: AccountRowProps) {
  return (
<section
  className={cn(
    'rounded-xl border bg-card px-5 py-5',
    selected ? 'border-primary ring-2 ring-primary/15' : 'border-border'
  )}
>
  <div className="flex items-center justify-between gap-3">
  <div className="min-w-0 flex-1">
    <div className="flex items-center gap-2">
      <p className="truncate text-base font-bold">{bankName}</p>

      {primary ? (
<span className="rounded-lg bg-primary/10 px-2 py-0.5 text-xs font-semibold text-primary">
  주거래
</span>      ) : (
        <button
          type="button"
          onClick={onSetPrimary}
          className="text-sm font-medium text-primary"
        >
          주거래 변경
        </button>
      )}
    </div>

    <p className="mt-2 text-sm tabular-nums text-muted-foreground">
      {maskedAccountNumber}
    </p>

    {holderName ? (
      <p className="mt-1 text-xs text-muted-foreground">
        {holderName}
      </p>
    ) : null}
  </div>

  {mode === 'manage' ? (
    <Button
      variant="danger"
      size="md"
      onClick={onDelete}
      className="h-10 w-auto shrink-0 px-4"
    >
      삭제
    </Button>
  ) : null}
</div>
</section>
  )
}
