import type { DisplayHistoryType, HistoryListItem as HistoryListItemModel } from '@/api/user'
import { formatTimeHHmm, formatWon } from '@/lib/format'
import { cn } from '@/lib/utils'

interface HistoryListItemProps {
  item: HistoryListItemModel
}

const SUBTYPE_LABEL: Record<DisplayHistoryType, string> = {
  PAYMENT: '결제',
  CANCEL: '결제 취소',
  CHARGE: '충전',
  EXCHANGE: '환불',
}

function amountClass(displayType: DisplayHistoryType): string {
  if (displayType === 'CHARGE' || displayType === 'CANCEL') return 'text-primary'
  if (displayType === 'EXCHANGE') return 'text-destructive'
  return 'text-foreground'
}

export function HistoryListItem({ item }: HistoryListItemProps) {
  return (
    <div className="flex w-full items-center gap-3 px-4 py-3">
      <span className="w-12 shrink-0 text-sm font-medium tabular-nums text-muted-foreground">
        {formatTimeHHmm(item.createdAt)}
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-foreground">{item.counterpartName}</p>
        <p className="mt-0.5 text-xs text-muted-foreground">{SUBTYPE_LABEL[item.displayType]}</p>
      </div>
      <span
        className={cn('shrink-0 text-sm font-bold tabular-nums', amountClass(item.displayType))}
      >
        {item.sign}
        {formatWon(item.amount)}
      </span>
    </div>
  )
}
