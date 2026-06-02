import { cn } from '@/lib/utils'

interface SegmentedTabsProps<T extends string> {
  tabs: ReadonlyArray<{ key: T; label: string }>
  value: T
  onChange: (key: T) => void
}

export function SegmentedTabs<T extends string>({ tabs, value, onChange }: SegmentedTabsProps<T>) {
  return (
    <div className="flex border-b border-border">
      {tabs.map((tab) => {
        const isActive = tab.key === value
        return (
          <button
            key={tab.key}
            type="button"
            onClick={() => onChange(tab.key)}
            className={cn(
              'flex-1 py-3 text-sm font-medium',
              isActive ? '-mb-px border-b-2 border-primary text-primary' : 'text-muted-foreground'
            )}
          >
            {tab.label}
          </button>
        )
      })}
    </div>
  )
}
