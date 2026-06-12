import type { ReactNode } from 'react'
import { AlertCircle, CheckCircle, XCircle } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from '../action/Button'

interface ResultStateProps {
  variant: 'success' | 'error' | 'info'
  title: string
  description?: string
  details?: ReactNode
  primaryText: string
  secondaryText?: string
  onPrimary: () => void
  onSecondary?: () => void
}

const iconClasses = {
  success: 'bg-success/10 text-success',
  error: 'bg-destructive/10 text-destructive',
  info: 'bg-info/10 text-info',
}

const VariantIcon = {
  success: CheckCircle,
  error: AlertCircle,
  info: XCircle,
}

export function ResultState({
  variant,
  title,
  description,
  details,
  primaryText,
  secondaryText,
  onPrimary,
  onSecondary,
}: ResultStateProps) {
  const Icon = VariantIcon[variant]

  return (
    <section className="mx-auto w-full max-w-sm rounded-lg border border-border bg-card p-5 text-center">
      <div
        className={cn(
          'mx-auto flex size-12 items-center justify-center rounded-full',
          iconClasses[variant]
        )}
      >
        <Icon className="size-6" aria-hidden />
      </div>
      <h2 className="mt-4 text-xl font-bold">{title}</h2>
      {description ? (
        <p className="mt-2 whitespace-pre-line break-keep text-sm leading-6 text-muted-foreground">
          {description}
        </p>
      ) : null}
      {details ? <div className="mt-4 rounded-lg bg-surface p-3 text-sm">{details}</div> : null}
      <div className="mt-5 space-y-2">
        <Button onClick={onPrimary}>{primaryText}</Button>
        {secondaryText && onSecondary ? (
          <Button variant="secondary" onClick={onSecondary}>
            {secondaryText}
          </Button>
        ) : null}
      </div>
    </section>
  )
}
