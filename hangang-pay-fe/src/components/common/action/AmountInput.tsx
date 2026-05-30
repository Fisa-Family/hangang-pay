import { formatWon } from '@/lib/format'
import { cn } from '@/lib/utils'

interface AmountInputProps {
  value: number
  placeholder?: string
  error?: string
  disabled?: boolean
  active?: boolean
  onClick?: () => void
  className?: string
}

export function AmountInput({
  value,
  placeholder = '금액 입력',
  error,
  disabled = false,
  active = false,
  onClick,
  className,
}: AmountInputProps) {
  return (
    <div className={cn('space-y-2', className)}>
      <button
        type="button"
        aria-disabled={disabled}
        disabled={disabled}
        onClick={onClick}
        className={cn(
          'flex min-h-20 w-full items-center justify-end rounded-lg border bg-card px-4 text-left transition-colors',
          error ? 'border-destructive' : 'border-input',
          active && !error && 'border-primary ring-2 ring-primary/15',
          disabled && 'bg-muted text-muted-foreground'
        )}
      >
        <p
          className={cn(
            'text-right text-3xl font-bold tabular-nums',
            !value && 'text-muted-foreground'
          )}
        >
          {value ? formatWon(value) : placeholder}
        </p>
      </button>
      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}
    </div>
  )
}
