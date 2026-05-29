import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

interface SummaryRow {
  label: string
  value: ReactNode
  emphasis?: boolean
}

interface SummaryCardProps {
  title?: string
  rows: SummaryRow[]
  divided?: boolean
  className?: string
}

export function SummaryCard({ title, rows, divided = false, className }: SummaryCardProps) {
  return (
    <section className={cn('rounded-lg border border-border bg-card p-4', className)}>
      {title ? <h2 className="mb-3 text-sm font-semibold text-foreground">{title}</h2> : null}
      <dl className={cn(divided ? 'divide-y divide-border/50' : 'space-y-3')}>
        {rows.map((row) => (
          <div
            key={row.label}
            className={cn(
              'flex items-center justify-between gap-4',
              divided && 'py-3 first:pt-0 last:pb-0'
            )}
          >
            <dt className="text-sm text-muted-foreground">{row.label}</dt>
            <dd
              className={cn(
                'text-right text-sm font-semibold tabular-nums text-foreground',
                row.emphasis && 'text-base text-primary'
              )}
            >
              {row.value}
            </dd>
          </div>
        ))}
      </dl>
    </section>
  )
}
