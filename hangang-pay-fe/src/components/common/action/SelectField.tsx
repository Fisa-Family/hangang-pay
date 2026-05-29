import { cn } from '@/lib/utils'

interface SelectOption {
  label: string
  value: string
}

interface SelectFieldProps {
  label: string
  value: string
  options: SelectOption[]
  onChange: (value: string) => void
  placeholder?: string
  error?: string
  className?: string
}

export function SelectField({
  label,
  value,
  options,
  onChange,
  placeholder,
  error,
  className,
}: SelectFieldProps) {
  return (
    <label className={cn('block space-y-2', className)}>
      <span className="text-sm font-semibold text-foreground">{label}</span>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className={cn(
          'h-12 w-full rounded-lg border bg-card px-3 text-base outline-none transition-colors',
          'focus:border-primary focus:ring-2 focus:ring-primary/15',
          error ? 'border-destructive' : 'border-input',
          !value && 'text-muted-foreground'
        )}
      >
        {placeholder ? <option value="">{placeholder}</option> : null}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {error ? <span className="text-sm font-medium text-destructive">{error}</span> : null}
    </label>
  )
}
