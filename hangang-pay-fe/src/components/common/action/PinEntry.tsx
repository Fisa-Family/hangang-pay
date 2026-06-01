import { cn } from '@/lib/utils'
import { BackspaceIcon } from '../icons'

interface PinEntryProps {
  pin: string
  length?: number
  onChange: (pin: string) => void
  title?: string
  subtitle?: string
  onForgot?: () => void
  className?: string
}

const PIN_PAD_ROWS = [
  ['1', '2', '3'],
  ['4', '5', '6'],
  ['7', '8', '9'],
] as const

export function PinEntry({
  pin,
  length = 6,
  onChange,
  title = 'PIN번호를 입력해주세요',
  subtitle = '안전한 서비스 이용을 위해 PIN번호를 입력해주세요.',
  onForgot,
  className,
}: PinEntryProps) {
  const handleDigit = (d: string) => {
    if (pin.length >= length) return
    onChange(pin + d)
  }
  const handleBackspace = () => onChange(pin.slice(0, -1))

  return (
    <div className={cn('flex min-h-0 flex-1 flex-col', className)}>
      <div className="flex flex-1 flex-col items-center justify-center gap-8 px-5">
        <div className="flex flex-col items-center gap-3 text-center">
          <p className="text-[22px] font-bold text-foreground">{title}</p>
          <p className="text-[13px] text-muted-foreground">{subtitle}</p>
        </div>

        <div className="flex gap-4">
          {Array.from({ length }, (_, i) => (
            <div
              key={i}
              className={cn(
                'h-4 w-4 rounded-full border-2 transition-colors',
                i < pin.length
                  ? 'border-foreground bg-foreground'
                  : 'border-muted-foreground/40 bg-transparent'
              )}
            />
          ))}
        </div>

        {onForgot ? (
          <button type="button" className="text-sm font-medium text-primary" onClick={onForgot}>
            PIN번호를 잊으셨나요?
          </button>
        ) : null}
      </div>

      <div className="grid grid-cols-3 gap-px bg-border/30">
        {PIN_PAD_ROWS.flat().map((d) => (
          <button
            key={d}
            type="button"
            onClick={() => handleDigit(d)}
            className="flex h-20 items-center justify-center bg-background text-xl font-semibold text-foreground active:bg-muted"
          >
            {d}
          </button>
        ))}
        <div className="h-20 bg-background" />
        <button
          type="button"
          onClick={() => handleDigit('0')}
          className="flex h-20 items-center justify-center bg-background text-xl font-semibold text-foreground active:bg-muted"
        >
          0
        </button>
        <button
          type="button"
          onClick={handleBackspace}
          className="flex h-20 items-center justify-center bg-background text-foreground active:bg-muted"
          aria-label="지우기"
        >
          <BackspaceIcon />
        </button>
      </div>
    </div>
  )
}
