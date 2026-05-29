import { useSuspenseQuery } from '@tanstack/react-query'
import { Suspense, type SVGProps } from 'react'
import { useNavigate } from 'react-router-dom'
import { useCurrentUser } from '@/auth/useCurrentUser'
import { EmptyState, ErrorBoundary } from '@/components/common'
import { fetchMerchantSettlements } from '@/api/merchant'
import { formatWon } from '@/lib/format'

// 결제 시각 포맷 HH:mm
function formatPaymentTime(isoString: string): string {
  const d = new Date(isoString)
  const hh = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${min}`
}

// 최근 정산 표시 건수
const SETTLEMENT_LIMIT = 4

// 라인 아트 SVG 아이콘
function QrCodeIcon({ className }: { className?: string }) {
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
      <rect width="5" height="5" x="3" y="3" rx="1" />
      <rect width="5" height="5" x="16" y="3" rx="1" />
      <rect width="5" height="5" x="3" y="16" rx="1" />
      <path d="M21 16V21H16" />
      <path d="M9 9h.01" />
      <path d="M15 9h.01" />
      <path d="M9 15h.01" />
      <path d="M14 14h.01" />
      <path d="M18 18h.01" />
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

// 파이 차트 아이콘
function PieChartIcon({ className }: { className?: string }) {
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
      <path d="M21.21 15.89A10 10 0 1 1 8 2.83" />
      <path d="M22 12A10 10 0 0 0 12 2v10z" />
    </svg>
  )
}

// 막대 차트 아이콘
function BarChartIcon({ className }: { className?: string }) {
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
      <line x1="12" x2="12" y1="20" y2="10" />
      <line x1="18" x2="18" y1="20" y2="4" />
      <line x1="6" x2="6" y1="20" y2="16" />
    </svg>
  )
}

// 보조 메뉴 항목과 이동 경로
const secondaryMenuItems = [
  { label: '결제 내역', icon: DocumentIcon, path: '/merchant/payments' },
  { label: '출금 내역', icon: PieChartIcon, path: '/merchant/settlement/history' },
  { label: '매출 분석', icon: BarChartIcon, path: '/merchant/analytics' },
] as const

// 최근 정산 목록 (Suspense 전용, 오류는 상위 ErrorBoundary 위임)
function RecentSettlementList() {
  const { data } = useSuspenseQuery({
    queryKey: ['merchant', 'settlements', SETTLEMENT_LIMIT],
    queryFn: () => fetchMerchantSettlements(SETTLEMENT_LIMIT),
    retry: false,
  })

  const items = data.content

  if (items.length === 0) {
    return <EmptyState message="최근 출금 내역이 없습니다." />
  }

  return (
    <div className="flex flex-col">
      {items.map((item, idx) => (
        <div
          key={item.settlementId}
          className="flex items-center gap-3 py-3"
          style={{
            borderBottom: idx < items.length - 1 ? '1px solid #F9FAFB' : 'none',
          }}
        >
          <span className="w-10 shrink-0 text-[13px] text-[#9CA3AF]">
            {formatPaymentTime(item.requestedAt)}
          </span>
          <span className="flex-1 text-[14px] font-bold text-[#111827]">
            {item.settlementStatusText || '정산'}
          </span>
          <span className="text-[14px] font-bold text-[#111827]">{formatWon(item.amount)}</span>
        </div>
      ))}
    </div>
  )
}

export function MerchantHomePage() {
  const navigate = useNavigate()
  const { currentUser, isLoading } = useCurrentUser()

  // CSS: 페이지 전체 — 세로 스크롤 flex 컨테이너
  return (
    <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto pb-4">
      {/* 헤더: 가맹점명 — 좌측 정렬, 상단 여백 */}
      <header className="flex items-center justify-between pt-1">
        <h1 className="text-2xl font-bold text-[#111827]">{currentUser?.name ?? '가맹점'}</h1>
      </header>

      {/* 대시보드 카드: 흰 카드 + 우하단 블루 그라데이션 장식 / 매출·결제 건수 좌우 분할 */}
      <div className="relative overflow-hidden rounded-2xl bg-white shadow-sm">
        {/* 우측 하단 블루 그라데이션 장식 */}
        <div
          className="pointer-events-none absolute -bottom-12 -right-12 h-40 w-40 rounded-full"
          style={{
            background: 'radial-gradient(circle, #BFDBFE 0%, #DBEAFE 50%, transparent 80%)',
          }}
        />
        <div className="relative flex p-5">
          {/* 오늘 매출 */}
          <div className="flex-1 pr-5">
            <p className="text-sm text-[#9CA3AF]">오늘 매출</p>
            <p className="mt-1 text-[22px] font-bold leading-tight text-[#2563EB]">
              {formatWon(0)}
            </p>
          </div>
          {/* 수직 구분선 */}
          <div className="w-px self-stretch bg-[#F3F4F6]" />
          {/* 오늘 결제 건수 */}
          <div className="flex-1 pl-5">
            <p className="text-sm text-[#9CA3AF]">오늘 결제</p>
            <p className="mt-1 text-[22px] font-bold leading-tight text-[#111827]">0건</p>
          </div>
        </div>
      </div>

      {/* 주요 버튼: 2열 그리드 / 고정 높이 120px 파란 카드 */}
      <div className="grid grid-cols-2 gap-3">
        <button
          type="button"
          onClick={() => navigate('/merchant/qr')}
          className="flex h-[120px] flex-col justify-between rounded-2xl bg-[#2563EB] p-4 text-left transition-opacity active:opacity-90"
        >
          <QrCodeIcon className="h-6 w-6 text-white" />
          <div className="flex items-end justify-between">
            <span className="text-[15px] font-bold text-white">내 QR 보기</span>
            {/* 원형 배경 화살표 */}
            <div className="flex h-7 w-7 items-center justify-center rounded-full bg-white/20">
              <ChevronRightIcon className="h-4 w-4 text-white" />
            </div>
          </div>
        </button>

        <button
          type="button"
          onClick={() => navigate('/merchant/settlement')}
          className="flex h-[120px] flex-col justify-between rounded-2xl bg-[#1E40AF] p-4 text-left transition-opacity active:opacity-90"
        >
          {/* 원화 기호 원형 뱃지 */}
          <div className="flex h-8 w-8 items-center justify-center rounded-full border-2 border-white/70">
            <span className="font-bold text-white">₩</span>
          </div>
          <div className="flex items-end justify-between">
            <span className="text-[15px] font-bold text-white">출금하기</span>
            <ChevronRightIcon className="h-4 w-4 text-white" />
          </div>
        </button>
      </div>

      {/* 보조 메뉴: 3열 그리드 / 흰 카드에 아이콘+라벨 세로 정렬 */}
      <div className="grid grid-cols-3 gap-3">
        {secondaryMenuItems.map(({ label, icon: Icon, path }) => (
          <button
            key={label}
            type="button"
            onClick={() => navigate(path)}
            className="flex flex-col items-center gap-2.5 rounded-2xl bg-white p-4 shadow-sm transition-colors active:bg-muted"
          >
            <Icon className="h-6 w-6 text-[#2563EB]" />
            <span className="text-[13px] font-bold text-[#111827]">
              {label} <span className="font-normal text-[#9CA3AF]">&gt;</span>
            </span>
          </button>
        ))}
      </div>

      {/* 최근 정산 내역: 흰 카드 / 헤더(제목+전체보기) + 목록 */}
      <div className="rounded-2xl bg-white p-5 shadow-sm">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-base font-bold text-[#111827]">최근 출금 내역</h2>
          <button
            type="button"
            onClick={() => navigate('/merchant/settlement/history')}
            className="text-xs font-medium text-[#9CA3AF]"
          >
            전체보기 &gt;
          </button>
        </div>

        {isLoading ? (
          <div className="py-4 text-center text-sm text-muted-foreground">불러오는 중…</div>
        ) : (
          <ErrorBoundary fallback={<EmptyState message="출금 내역을 불러올 수 없습니다." />}>
            <Suspense
              fallback={
                <div className="py-4 text-center text-sm text-muted-foreground">불러오는 중…</div>
              }
            >
              <RecentSettlementList />
            </Suspense>
          </ErrorBoundary>
        )}
      </div>
    </div>
  )
}
