import { ChevronRight, FileText } from 'lucide-react'
import { cn } from '@/lib/utils'

interface HistoryEntryCardProps {
  onClick: () => void
}

export function HistoryEntryCard({ onClick }: HistoryEntryCardProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'flex w-full items-center gap-3 rounded-2xl border border-border/40 bg-card p-4 text-left shadow-sm',
        'active:bg-surface'
      )}
    >
      {/* 아이콘 */}
      <div className="flex h-12 w-12 shrink-0 items-center justify-center">
        <FileText className="h-6 w-6 text-primary" aria-hidden />
      </div>

      {/* 라벨 */}
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold text-foreground">내역 확인</p>
      </div>

      {/* 우측 보조 텍스트 */}
      <p className="shrink-0 text-xs text-muted-foreground">결제 · 충전 · 환불 내역</p>

      {/* 오른쪽 화살표 */}
      <ChevronRight className="h-5 w-5 shrink-0 text-muted-foreground" aria-hidden />
    </button>
  )
}
