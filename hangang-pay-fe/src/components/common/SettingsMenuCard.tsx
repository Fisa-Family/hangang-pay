import type { ReactNode, SVGProps } from 'react'
import { cn } from '@/lib/utils'

export interface SettingsMenuItem {
  id: string
  label: string
  description?: string
  icon: ReactNode
  onClick: () => void
  variant?: 'default' | 'danger'
  disabled?: boolean
}

interface SettingsMenuCardProps {
  items: SettingsMenuItem[]
}

function ChevronRightIcon({ className, ...props }: SVGProps<SVGSVGElement>) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden
      {...props}
    >
      <path d="m9 18 6-6-6-6" />
    </svg>
  )
}

export function SettingsMenuCard({ items }: SettingsMenuCardProps) {
  return (
    <div className="flex flex-col gap-2">
      {items.map((item) => {
        const isDanger = item.variant === 'danger'
        return (
          <button
            key={item.id}
            type="button"
            onClick={item.onClick}
            disabled={item.disabled}
            className={cn(
              'flex w-full items-center gap-3 rounded-2xl border border-border bg-card p-4 text-left',
              'active:bg-surface disabled:opacity-40'
            )}
          >
            <div
              className={cn(
                'flex h-12 w-12 shrink-0 items-center justify-center rounded-lg',
                isDanger ? 'bg-destructive/10' : 'bg-primary/10'
              )}
            >
              {item.icon}
            </div>

            <div className="min-w-0 flex-1">
              <p
                className={cn(
                  'text-sm font-semibold',
                  isDanger ? 'text-destructive' : 'text-foreground'
                )}
              >
                {item.label}
              </p>
              {item.description ? (
                <p className="mt-0.5 text-xs text-muted-foreground">{item.description}</p>
              ) : null}
            </div>

            <ChevronRightIcon className="h-5 w-5 shrink-0 text-muted-foreground" />
          </button>
        )
      })}
    </div>
  )
}
