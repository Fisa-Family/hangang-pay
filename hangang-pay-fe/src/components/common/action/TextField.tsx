import { useState } from 'react'
import { Eye, EyeOff } from 'lucide-react'
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

function EyeIcon({ open }: { open: boolean }) {
  return open ? <Eye className="h-5 w-5" aria-hidden /> : <EyeOff className="h-5 w-5" aria-hidden />
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
  const [showPassword, setShowPassword] = useState(false)
  const isPassword = type === 'password'
  const resolvedType = isPassword ? (showPassword ? 'text' : 'password') : type

  return (
    <label className={cn('block space-y-2', className)}>
      <span className="text-sm font-semibold text-foreground">{label}</span>
      <div className="relative">
        <input
          type={resolvedType}
          value={value}
          placeholder={placeholder}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
          className={cn(
            'h-12 w-full rounded-lg border bg-card px-3 text-base outline-none transition-colors placeholder:text-muted-foreground',
            'focus:border-primary focus:ring-2 focus:ring-primary/15',
            error ? 'border-destructive' : 'border-input',
            disabled && 'bg-muted text-muted-foreground',
            isPassword && 'pr-10'
          )}
        />
        {isPassword && (
          <button
            type="button"
            tabIndex={-1}
            onClick={() => setShowPassword((v) => !v)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
          >
            <EyeIcon open={showPassword} />
          </button>
        )}
      </div>
      {error ? <span className="text-sm font-medium text-destructive">{error}</span> : null}
    </label>
  )
}
