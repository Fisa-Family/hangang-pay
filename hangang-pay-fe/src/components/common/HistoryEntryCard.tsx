import { cn } from '@/lib/utils'
import { ChevronRightIcon } from './icons'

interface HistoryEntryCardProps {
  onClick: () => void
}

// 문서 아이콘
function DocumentIcon({ className }: { className?: string }) {
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
    >
      <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z" />
      <path d="M14 2v4a2 2 0 0 0 2 2h4" />
      <path d="M10 9H8" />
      <path d="M16 13H8" />
      <path d="M16 17H8" />
    </svg>
  )
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
        <DocumentIcon className="h-6 w-6 text-primary" />
      </div>

      {/* 라벨 */}
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold text-foreground">내역 확인</p>
      </div>

      {/* 우측 보조 텍스트 */}
      <p className="shrink-0 text-xs text-muted-foreground">결제 · 충전 · 환전 내역</p>

      {/* 오른쪽 화살표 */}
      <ChevronRightIcon className="h-5 w-5 shrink-0 text-muted-foreground" />
    </button>
  )
}
