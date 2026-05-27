import type { SVGProps } from 'react'
import { cn } from '@/lib/utils'

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

// 오른쪽 화살표 아이콘
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

export function HistoryEntryCard({ onClick }: HistoryEntryCardProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'flex w-full items-center gap-3 rounded-2xl border border-border bg-card p-4 text-left',
        'active:bg-surface'
      )}
    >
      {/* 아이콘 컨테이너 */}
      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-primary/10">
        <DocumentIcon className="h-6 w-6 text-primary" />
      </div>

      {/* 텍스트 영역 */}
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold text-foreground">내역 확인</p>
        <p className="mt-0.5 text-xs text-muted-foreground">결제 · 충전 · 환불 내역</p>
      </div>

      {/* 오른쪽 화살표 */}
      <ChevronRightIcon className="h-5 w-5 shrink-0 text-muted-foreground" />
    </button>
  )
}
