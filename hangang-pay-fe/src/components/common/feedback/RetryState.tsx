import { Button } from '../action/Button'
import { AlertCircleIcon } from '../icons/AlertCircleIcon'

interface RetryStateProps {
  title: string
  description: string
  primaryText: string
  onPrimary: () => void
  secondaryText: string
  onSecondary: () => void
}

export function RetryState({
  title,
  description,
  primaryText,
  onPrimary,
  secondaryText,
  onSecondary,
}: RetryStateProps) {
  return (
    <div className="flex h-full w-full flex-col items-center justify-between py-8">
      <div className="flex w-full flex-1 flex-col items-center justify-center gap-5">
        <AlertCircleIcon />

        <p className="text-xl font-bold text-foreground">{title}</p>

        <div className="w-full rounded-2xl bg-card p-5 shadow-sm ring-1 ring-border/60">
          <p className="whitespace-pre-line break-keep text-center text-sm leading-6 text-muted-foreground">
            {description}
          </p>
        </div>
      </div>

      <div className="w-full space-y-2">
        <Button onClick={onPrimary} className="rounded-2xl">
          {primaryText}
        </Button>
        <Button variant="secondary" size="lg" className="rounded-2xl" onClick={onSecondary}>
          {secondaryText}
        </Button>
      </div>
    </div>
  )
}
