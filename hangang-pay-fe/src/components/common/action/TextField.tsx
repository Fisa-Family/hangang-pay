import { cn } from '@/lib/utils'

interface TextFieldProps {
  label: string
  value: string
  onChange: (value: string) => void
  type?: string
  placeholder?: string
  error?: string
  disabled?: boolean
  className?: string
}

export function TextField({
  label,
  value,
  onChange,
  type = 'text',
  placeholder,
  error,
  disabled = false,
  className,
}: TextFieldProps) {
  return (
    <label className={cn('block space-y-2', className)}>
      <span className="text-sm font-semibold text-foreground">{label}</span>
      <input
        type={type}
        value={value}
        placeholder={placeholder}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
        className={cn(
          'h-12 w-full rounded-lg border bg-card px-3 text-base outline-none transition-colors placeholder:text-muted-foreground',
          'focus:border-primary focus:ring-2 focus:ring-primary/15',
          error ? 'border-destructive' : 'border-input',
          disabled && 'bg-muted text-muted-foreground'
        )}
      />
      {error ? <span className="text-sm font-medium text-destructive">{error}</span> : null}
    </label>
  )
}
