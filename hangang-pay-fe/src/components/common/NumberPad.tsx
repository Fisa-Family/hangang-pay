import { cn } from '@/lib/utils'

interface NumberPadProps {
  onDigit: (digit: string) => void
  onBackspace: () => void
  onClear?: () => void
  disabled?: boolean
  className?: string
}

const digits = ['1', '2', '3', '4', '5', '6', '7', '8', '9']

export function NumberPad({
  onDigit,
  onBackspace,
  onClear,
  disabled = false,
  className,
}: NumberPadProps) {
  return (
    <div className={cn('grid grid-cols-3 gap-2', className)}>
      {digits.map((digit) => (
        <button
          key={digit}
          type="button"
          disabled={disabled}
          onClick={() => onDigit(digit)}
          className="h-14 rounded-lg bg-surface text-xl font-semibold tabular-nums text-foreground hover:bg-muted disabled:opacity-45"
        >
          {digit}
        </button>
      ))}
      <button
        type="button"
        disabled={disabled}
        onClick={onClear}
        className="h-14 rounded-lg bg-surface text-sm font-semibold text-muted-foreground hover:bg-muted disabled:opacity-45"
      >
        전체삭제
      </button>
      <button
        type="button"
        disabled={disabled}
        onClick={() => onDigit('0')}
        className="h-14 rounded-lg bg-surface text-xl font-semibold tabular-nums text-foreground hover:bg-muted disabled:opacity-45"
      >
        0
      </button>
      <button
        type="button"
        disabled={disabled}
        onClick={onBackspace}
        className="h-14 rounded-lg bg-surface text-xl font-semibold text-muted-foreground hover:bg-muted disabled:opacity-45"
      >
        ⌫
      </button>
    </div>
  )
}
