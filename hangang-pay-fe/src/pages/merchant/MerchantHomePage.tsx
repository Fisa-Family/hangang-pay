import { useQuery, useSuspenseQuery } from '@tanstack/react-query'
import { Suspense } from 'react'
import {
  QrCode,
  ChevronRight,
  FileText,
  PieChart,
  BarChart2,
  Search,
  Bell,
  Menu,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { EmptyState, ErrorBoundary, GradientCard, HangangPayLogo } from '@/components/common'
import { fetchMerchantDashboard, fetchMerchantMyPage, fetchMerchantPayments } from '@/api/merchant'
import { formatWon } from '@/lib/format'

// 결제 시각 포맷 HH:mm
function formatPaymentTime(isoString: string): string {
  const d = new Date(isoString)
  const hh = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${min}`
}

// 최근 결제 표시 건수
const PAYMENT_LIMIT = 4

// 보조 메뉴 항목과 이동 경로
const secondaryMenuItems = [
  { label: '결제 내역', icon: FileText, path: '/merchant/payments' },
  { label: '출금 내역', icon: PieChart, path: '/merchant/settlements' },
  { label: '매출 분석', icon: BarChart2, path: '/merchant/analytics' },
] as const

// 최근 결제 목록 (Suspense 전용, 오류는 상위 ErrorBoundary 위임)
function RecentPaymentList() {
  const { data } = useSuspenseQuery({
    queryKey: ['merchant', 'payments', PAYMENT_LIMIT],
    queryFn: () => fetchMerchantPayments({ size: PAYMENT_LIMIT }),
    retry: false,
  })

  const items = data.content

  if (items.length === 0) {
    return <EmptyState message="최근 결제 내역이 없습니다." />
  }

  return (
    <div className="flex flex-col">
      {items.map((item, idx) => {
        const sign = item.transactionType === 'CANCEL' ? '-' : '+'
        return (
          <div
            key={item.transactionId}
            className="flex items-center gap-3 py-4.5"
            style={{ borderBottom: idx < items.length - 1 ? '1px solid var(--border)' : 'none' }}
          >
            <span className="w-10 shrink-0 text-[13px] text-muted-foreground">
              {formatPaymentTime(item.createdAt)}
            </span>
            <span className="flex-1 truncate text-[14px] font-semibold text-foreground">
              {item.payerName}
            </span>
            <span
              className={`text-[14px] font-bold tabular-nums ${sign === '+' ? 'text-primary' : 'text-foreground'}`}
            >
              {sign}
              {formatWon(item.amount)}
            </span>
          </div>
        )
      })}
    </div>
  )
}

export function MerchantHomePage() {
  const navigate = useNavigate()

  const mypageQuery = useQuery({
    queryKey: ['merchant', 'mypage'],
    queryFn: fetchMerchantMyPage,
  })

  const dashboardQuery = useQuery({
    queryKey: ['merchant', 'dashboard'],
    queryFn: fetchMerchantDashboard,
  })

  const merchantName = mypageQuery.data?.merchantName ?? '가맹점'
  const todaySales = dashboardQuery.data?.todaySales ?? 0
  const todayCount = dashboardQuery.data?.todayCount ?? 0

  // CSS: 페이지 전체 — 세로 스크롤 flex 컨테이너
  return (
    <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-3 pt-5 pb-6">
      {/* 헤더: 로고 + 아이콘, 구분선, 가맹점명 */}
      <header className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1.5">
            <HangangPayLogo size={26} />
            <span className="text-sm font-bold text-primary">한강페이</span>
          </div>
          <div className="flex items-center gap-1 text-foreground">
            <button
              type="button"
              aria-label="검색"
              onClick={() => {}}
              className="flex h-9 w-9 items-center justify-center rounded-lg hover:bg-muted"
            >
              <Search className="h-5 w-5" aria-hidden />
            </button>
            <button
              type="button"
              aria-label="알림"
              onClick={() => {}}
              className="flex h-9 w-9 items-center justify-center rounded-lg hover:bg-muted"
            >
              <Bell className="h-5 w-5" aria-hidden />
            </button>
            <button
              type="button"
              aria-label="설정"
              onClick={() => {}}
              className="flex h-9 w-9 items-center justify-center rounded-lg hover:bg-muted"
            >
              <Menu className="h-5 w-5" aria-hidden />
            </button>
          </div>
        </div>
        <h1 className="text-2xl font-bold text-foreground">
          {merchantName}
          <span className="font-normal text-muted-foreground">님</span>
        </h1>
      </header>

      {/* 대시보드 카드: 좌→우 블루 그라데이션 / 매출·결제 건수 좌우 분할 */}
      <GradientCard>
        <div className="relative flex p-5">
          {/* 오늘 매출 */}
          <div className="flex-1 pr-5">
            <p className="text-sm text-muted-foreground">오늘 매출</p>
            <p className="mt-1 text-[22px] font-bold leading-tight text-primary">
              {dashboardQuery.isLoading ? (
                <span className="inline-block h-7 w-24 animate-pulse rounded bg-muted" />
              ) : (
                formatWon(todaySales)
              )}
            </p>
          </div>
          {/* 수직 구분선 */}
          <div className="w-px self-stretch bg-muted" />
          {/* 오늘 결제 건수 */}
          <div className="flex-1 pl-5">
            <p className="text-sm text-muted-foreground">오늘 결제</p>
            <p className="mt-1 text-[22px] font-bold leading-tight text-foreground">
              {dashboardQuery.isLoading ? (
                <span className="inline-block h-7 w-16 animate-pulse rounded bg-muted" />
              ) : (
                `${todayCount}건`
              )}
            </p>
          </div>
        </div>
      </GradientCard>

      {/* 주요 버튼: 2열 그리드 / 고정 높이 120px 파란 카드 */}
      <div className="grid grid-cols-2 gap-3">
        <button
          type="button"
          onClick={() => navigate('/merchant/qr')}
          className="flex h-[120px] flex-col justify-between rounded-2xl bg-primary p-4 text-left transition-opacity active:opacity-90"
        >
          <QrCode className="h-6 w-6 text-white" aria-hidden />
          <div className="flex items-end justify-between">
            <span className="text-[15px] font-bold text-white">내 QR 보기</span>
            {/* 원형 배경 화살표 */}
            <div className="flex h-7 w-7 items-center justify-center rounded-full bg-white/20">
              <ChevronRight className="h-4 w-4 text-white" aria-hidden />
            </div>
          </div>
        </button>

        <button
          type="button"
          onClick={() => navigate('/merchant/settlement')}
          className="flex h-[120px] flex-col justify-between rounded-2xl bg-accent-foreground p-4 text-left transition-opacity active:opacity-90"
        >
          {/* 원화 기호 원형 뱃지 */}
          <div className="flex h-8 w-8 items-center justify-center rounded-full border-2 border-white/70">
            <span className="font-bold text-white">₩</span>
          </div>
          <div className="flex items-end justify-between">
            <span className="text-[15px] font-bold text-white">출금하기</span>
            <ChevronRight className="h-4 w-4 text-white" aria-hidden />
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
            className="flex flex-col items-center gap-2.5 rounded-2xl bg-card p-4 shadow-sm transition-colors active:bg-muted"
          >
            <Icon className="h-6 w-6 text-primary" />
            <span className="text-[13px] font-bold text-foreground">
              {label} <span className="font-normal text-muted-foreground">&gt;</span>
            </span>
          </button>
        ))}
      </div>

      {/* 최근 결제 내역: 흰 카드 / 헤더(제목+전체보기) + 목록 */}
      <div className="rounded-2xl bg-card p-5 shadow-sm">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-base font-bold text-foreground">최근 결제 내역</h2>
          <button
            type="button"
            onClick={() => navigate('/merchant/payments')}
            className="text-xs font-medium text-muted-foreground"
          >
            전체보기 &gt;
          </button>
        </div>

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
