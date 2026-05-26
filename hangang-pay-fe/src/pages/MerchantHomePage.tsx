import { useQuery, useSuspenseQuery } from '@tanstack/react-query'
import { Suspense } from 'react'
import { useNavigate } from 'react-router-dom'
import { useCurrentUser } from '@/auth/useCurrentUser'
import { EmptyState, ErrorBoundary } from '@/components/common'
import { fetchMerchantDashboard, fetchMerchantPayments } from '@/api/merchant'
import { ApiError, type ApiError as ApiErrorType } from '@/api/client'
import { apiErrorMessages, isApiErrorCode, apiUserErrorMessages } from '@/api/errorCodes'
import { formatWon } from '@/lib/format'

// rest_api.md 기준 API 명세
const API_SPEC = {
  MERCHANT_001: { id: 'MERCHANT-001', path: 'GET /merchant/dashboard', role: 'MERCHANT' },
  MERCHANT_002: { id: 'MERCHANT-002', path: 'GET /merchant/payments', role: 'MERCHANT' },
} as const

// errorCodes.ts 기반 오류 메시지 반환
function buildErrorMessage(spec: (typeof API_SPEC)[keyof typeof API_SPEC], error: unknown): string {
  if (!(error instanceof ApiError)) {
    return (
      apiUserErrorMessages[spec.id]?.[0] ??
      '서비스에 연결할 수 없습니다. 네트워크 연결을 확인해 주세요.'
    )
  }

  const { status, code } = error as ApiErrorType

  if (code && isApiErrorCode(code)) return apiErrorMessages[code]

  return (
    apiUserErrorMessages[spec.id]?.[status] ??
    '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'
  )
}

// 결제 시각 포맷 HH:mm
function formatPaymentTime(isoString: string): string {
  const d = new Date(isoString)
  const hh = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${min}`
}

// 최근 결제 표시 건수
const PAYMENT_LIMIT = 4

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
function ChevronRightIcon({ className }: { className?: string }) {
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
  { label: '정산 내역', icon: PieChartIcon, path: '/merchant/settlement/history' },
  { label: '매출 분석', icon: BarChartIcon, path: '/merchant/analytics' },
] as const

// 최근 결제 목록 (Suspense 전용, 오류는 상위 ErrorBoundary 위임)
function RecentPaymentList() {
  const navigate = useNavigate()

  const { data } = useSuspenseQuery({
    queryKey: ['merchant', 'payments', PAYMENT_LIMIT],
    queryFn: () => fetchMerchantPayments(PAYMENT_LIMIT),
    retry: false,
  })

  const payments = data.content

  if (payments.length === 0) {
    return <EmptyState message="최근 결제 내역이 없습니다." />
  }

  // CSS: 결제 행 세로 나열 / 행마다 시각·상품명·금액 가로 정렬 / 마지막 행 제외 하단 구분선
  return (
    <div className="flex flex-col">
      {payments.map((payment, idx) => (
        <div
          key={payment.paymentId}
          className="flex items-center gap-3 py-3"
          style={{
            borderBottom: idx < payments.length - 1 ? '1px solid #F9FAFB' : 'none',
          }}
        >
          {/* 시각: 고정 너비로 세로 정렬 맞춤 */}
          <span className="w-10 shrink-0 text-[13px] text-[#9CA3AF]">
            {formatPaymentTime(payment.paidAt)}
          </span>
          {/* 상품명: 남은 공간 차지 */}
          <span className="flex-1 text-[14px] font-bold text-[#111827]">
            {payment.itemName || '결제'}
          </span>
          {/* 금액 + 상세 이동 화살표 */}
          <div className="flex items-center gap-1">
            <span className="text-[14px] font-bold text-[#111827]">
              {formatWon(payment.amount)}
            </span>
            <ChevronRightIcon
              className="h-3.5 w-3.5 cursor-pointer text-[#9CA3AF]"
              onClick={() => navigate(`/merchant/payments/${payment.paymentId}`)}
            />
          </div>
        </div>
      ))}
    </div>
  )
}

export function MerchantHomePage() {
  const navigate = useNavigate()
  const { currentUser } = useCurrentUser()

  // 오늘 매출 요약 조회
  const dashboardQuery = useQuery({
    queryKey: ['merchant', 'dashboard'],
    queryFn: fetchMerchantDashboard,
    retry: false,
  })

  const todaySales = dashboardQuery.data?.todaySales ?? 0
  const todayPaymentCount = dashboardQuery.data?.todayPaymentCount ?? 0

  // 대시보드 오류는 배너로만 표시 (결제 내역 오류는 ErrorBoundary 위임)
  const dashboardError = dashboardQuery.error
    ? buildErrorMessage(API_SPEC.MERCHANT_001, dashboardQuery.error)
    : null

  // CSS: 페이지 전체 — 세로 스크롤 flex 컨테이너
  return (
    <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto pb-4">
      {/* 헤더: 가맹점명 — 좌측 정렬, 상단 여백 */}
      <header className="flex items-center justify-between pt-1">
        <h1 className="text-2xl font-bold text-[#111827]">{currentUser?.name ?? '가맹점'}</h1>
      </header>

      {/* API 오류 안내 */}
      {dashboardError && (
        <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm font-medium text-destructive">
          {dashboardError}
        </div>
      )}

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
              {formatWon(todaySales)}
            </p>
          </div>
          {/* 수직 구분선 */}
          <div className="w-px self-stretch bg-[#F3F4F6]" />
          {/* 오늘 결제 건수 */}
          <div className="flex-1 pl-5">
            <p className="text-sm text-[#9CA3AF]">오늘 결제</p>
            <p className="mt-1 text-[22px] font-bold leading-tight text-[#111827]">
              {todayPaymentCount}건
            </p>
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
          className="flex h-[120px] flex-col justify-between rounded-2xl bg-[#1E3A8A] p-4 text-left transition-opacity active:opacity-90"
        >
          {/* 원화 기호 원형 뱃지 */}
          <div className="flex h-8 w-8 items-center justify-center rounded-full border-2 border-white/40">
            <span className="font-bold text-white/40">₩</span>
          </div>
          <div className="flex items-end justify-between">
            <span className="text-[15px] font-bold text-white">정산받기</span>
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

      {/* 최근 결제 내역: 흰 카드 / 헤더(제목+전체보기) + 목록 */}
      <div className="rounded-2xl bg-white p-5 shadow-sm">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-base font-bold text-[#111827]">최근 결제 내역</h2>
          <button
            type="button"
            onClick={() => navigate('/merchant/payments')}
            className="text-xs font-medium text-[#9CA3AF]"
          >
            전체보기 &gt;
          </button>
        </div>

        {/* TODO: 로딩 중 텍스트 → 실제 레이아웃 모양의 회색 박스(스켈레톤 UI)로 교체 */}
        <ErrorBoundary fallback={<EmptyState message="결제 내역을 불러올 수 없습니다." />}>
          <Suspense
            fallback={
              <div className="py-4 text-center text-sm text-muted-foreground">불러오는 중…</div>
            }
          >
            <RecentPaymentList />
          </Suspense>
        </ErrorBoundary>
      </div>
    </div>
  )
}
