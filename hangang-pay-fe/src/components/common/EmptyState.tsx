import type { ReactNode } from 'react'

interface EmptyStateProps {
  message: string
  action?: ReactNode
}

export function EmptyState({ message, action }: EmptyStateProps) {
  return (
    <section className="rounded-lg border border-dashed border-border bg-surface p-6 text-center">
      <p className="text-sm font-medium text-muted-foreground">{message}</p>
      {action ? <div className="mt-4">{action}</div> : null}
    </section>
  )
}
