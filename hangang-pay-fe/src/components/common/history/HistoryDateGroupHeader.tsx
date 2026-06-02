import { formatDateGroup } from '@/lib/format'

interface HistoryDateGroupHeaderProps {
  isoDate: string
}

export function HistoryDateGroupHeader({ isoDate }: HistoryDateGroupHeaderProps) {
  return (
    <div className="bg-muted px-4 py-1.5 text-xs font-medium text-muted-foreground">
      {formatDateGroup(isoDate)}
    </div>
  )
}
