import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'
import { ChevronRightIcon } from './icons'

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

export function SettingsMenuCard({ items }: SettingsMenuCardProps) {
  return (
    <div className="overflow-hidden rounded-2xl border border-border/40 bg-card shadow-sm">
      <ul className="divide-y divide-border/40">
        {items.map((item) => {
          const isDanger = item.variant === 'danger'
          return (
            <li key={item.id}>
              <button
                type="button"
                onClick={item.onClick}
                disabled={item.disabled}
                className="flex w-full items-center gap-3 px-4 py-4 text-left transition-colors active:bg-surface disabled:opacity-40"
              >
                <div className="flex h-12 w-12 shrink-0 items-center justify-center">
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
                </div>

                {item.description ? (
                  <p className="shrink-0 text-xs text-muted-foreground">{item.description}</p>
                ) : null}

                <ChevronRightIcon className="h-5 w-5 shrink-0 text-muted-foreground" />
              </button>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
