import { useRef } from 'react'
import { cn } from '@/lib/utils'

interface PinCodeInputProps {
  value: string
  length: number
  onChange: (value: string) => void
  error?: string
  disabled?: boolean
}

export function PinCodeInput({
  value,
  length,
  onChange,
  error,
  disabled = false,
}: PinCodeInputProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const chars = Array.from({ length }, (_, index) => value[index] ?? '')

  return (
    <div className="space-y-2">
      <div
        className="grid cursor-text gap-2"
        style={{ gridTemplateColumns: `repeat(${length}, minmax(0, 1fr))` }}
        onClick={() => inputRef.current?.focus()}
      >
        {chars.map((char, index) => (
          <div
            key={`${index}-${char}`}
            className={cn(
              'flex h-12 items-center justify-center rounded-lg border bg-card text-xl font-bold tabular-nums',
              error ? 'border-destructive' : 'border-input',
              disabled && 'bg-muted text-muted-foreground'
            )}
          >
            {char}
          </div>
        ))}
      </div>
      <input
        ref={inputRef}
        aria-label="인증 코드"
        inputMode="numeric"
        value={value}
        disabled={disabled}
        maxLength={length}
        onChange={(event) => onChange(event.target.value.replace(/\D/g, '').slice(0, length))}
        className="sr-only"
      />
      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}
    </div>
  )
}
