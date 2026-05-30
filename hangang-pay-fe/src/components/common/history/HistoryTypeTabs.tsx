import type { HistoryTab } from '@/api/user'
import { cn } from '@/lib/utils'

interface HistoryTypeTabsProps {
  value: HistoryTab
  onChange: (tab: HistoryTab) => void
}

const TABS: ReadonlyArray<{ key: HistoryTab; label: string }> = [
  { key: 'ALL', label: '전체' },
  { key: 'PAYMENT', label: '결제' },
  { key: 'CHARGE', label: '충전' },
  { key: 'EXCHANGE', label: '환불' },
]

export function HistoryTypeTabs({ value, onChange }: HistoryTypeTabsProps) {
  return (
    <div className="flex border-b border-border">
      {TABS.map((tab) => {
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
