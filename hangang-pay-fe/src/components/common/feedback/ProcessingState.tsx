import type { ReactNode } from 'react'
import { Button } from '../action/Button'

interface ProcessingStateProps {
  status: 'loading' | 'error'
  loadingText: string
  errorTitle: string
  errorMessage?: string
  summary?: ReactNode
  retryText?: string
  homeText?: string
  onRetry?: () => void
  onHome?: () => void
}

export function ProcessingState({
  status,
  loadingText,
  errorTitle,
  errorMessage,
  summary,
  retryText,
  homeText,
  onRetry,
  onHome,
}: ProcessingStateProps) {
  return (
    <section className="rounded-lg border border-border bg-card p-5 text-center">
      {status === 'loading' ? (
        <div className="mx-auto size-10 animate-spin rounded-full border-4 border-muted border-t-primary" />
      ) : (
        <div className="mx-auto flex size-10 items-center justify-center rounded-full bg-destructive/10 text-xl font-bold text-destructive">
          !
        </div>
      )}
      <h2 className="mt-4 text-lg font-bold">{status === 'loading' ? loadingText : errorTitle}</h2>
      {status === 'error' && errorMessage ? (
        <p className="mt-2 text-sm leading-6 text-muted-foreground">{errorMessage}</p>
      ) : null}
      {summary ? <div className="mt-4 rounded-lg bg-surface p-3 text-sm">{summary}</div> : null}
      {status === 'error' && (onRetry || onHome) ? (
        <div className="mt-5 grid grid-cols-2 gap-2">
          {onRetry && retryText ? <Button onClick={onRetry}>{retryText}</Button> : null}
          {onHome && homeText ? (
            <Button variant="secondary" onClick={onHome}>
              {homeText}
            </Button>
          ) : null}
        </div>
      ) : null}
    </section>
  )
}
