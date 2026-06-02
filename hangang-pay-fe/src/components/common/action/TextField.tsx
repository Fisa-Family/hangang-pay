import { useState } from 'react'
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
  return open ? (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="h-5 w-5"
      aria-hidden
    >
      <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  ) : (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="h-5 w-5"
      aria-hidden
    >
      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
      <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
      <line x1="2" y1="2" x2="22" y2="22" />
    </svg>
  )
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
