import { useQuery, useSuspenseQuery } from '@tanstack/react-query'
import { Suspense } from 'react'
import { useNavigate } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useCurrentUser } from '@/auth/useCurrentUser'
import { BalanceCard, EmptyState, ErrorBoundary, ListItem } from '@/components/common'
import { fetchChargeInit } from '@/api/charge'
import { fetchRecentTransactions, type HistoryListItem } from '@/api/user'
import { ApiError } from '@/api/client'
import { apiErrorMessages, isApiErrorCode } from '@/api/errorCodes'
import { formatWon } from '@/lib/format'
import { cn } from '@/lib/utils'

const API_SPEC = {
  WALLET_001: { id: 'WALLET-001' },
  MY_002: { id: 'MY-002' },
} as const

function buildErrorMessage(spec: (typeof API_SPEC)[keyof typeof API_SPEC], error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code && isApiErrorCode(error.code)) return apiErrorMessages[error.code]
    return error.message
  }

  return `${spec.id} 요청에 실패했습니다. 네트워크 연결을 확인해 주세요.`
}

const HISTORY_LIMIT = 5

function formatHistoryDate(isoString: string): string {
  const d = new Date(isoString)
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${mm}.${dd} ${hh}:${min}`
}

interface QuickActionProps {
  label: string
  icon: ReactNode
  onClick: () => void
}

function QuickAction({ label, icon, onClick }: QuickActionProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex flex-col items-center gap-2.5 rounded-xl border border-border bg-card px-2 py-4 text-sm font-semibold text-foreground transition-colors active:bg-muted"
    >
      <span className="flex h-11 w-11 items-center justify-center rounded-full bg-primary text-primary-foreground">
        {icon}
      </span>
      <span>{label}</span>
    </button>
  )
}

function QrIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M3 3h8v8H3V3zm2 2v4h4V5H5zm8-2h8v8h-8V3zm2 2v4h4V5h-4zM3 13h8v8H3v-8zm2 2v4h4v-4H5zm8-1h2v2h-2v-2zm2 2h2v2h-2v-2zm-2 2h2v2h-2v-2zm2 2h2v2h-2v-2zm-4-6h2v2h-2v-2z" />
    </svg>
  )
}

function PlusIcon() {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
      strokeLinecap="round"
      aria-hidden
    >
      <path d="M12 5v14M5 12h14" />
    </svg>
  )
}

function UndoIcon() {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <path d="M9 10L5 14l4 4" />
      <path d="M5 14h9a5 5 0 000-10H3" />
    </svg>
  )
}

// 거래 금액 표시 — 부호(+/-)에 따라 수입은 초록, 지출은 빨강으로 색상 구분
function HistoryAmount({ sign, amount }: { sign: '+' | '-'; amount: number }) {
  return (
    <span
      className={cn(
        'shrink-0 text-right text-sm font-bold tabular-nums',
        sign === '+' ? 'text-success' : 'text-destructive'
      )}
    >
      {sign}
      {formatWon(amount)}
    </span>
  )
}

// 홈 화면 최근 거래 목록 — 결제, 충전, 환불 3종을 병렬 조회 후 최신순 5건 표시
function RecentTransactionList() {
  const { data } = useSuspenseQuery({
    queryKey: ['users', 'recent-histories'],
    queryFn: () => fetchRecentTransactions(HISTORY_LIMIT),
    retry: false,
  })

  const histories = data

  if (histories.length === 0) {
    return <EmptyState message="최근 거래 내역이 없습니다." />
  }

  return (
    <div className="flex flex-col gap-2">
      {histories.map((tx: HistoryListItem) => (
        <ListItem
          key={tx.id}
          title={tx.counterpartName}
          description={formatHistoryDate(tx.createdAt)}
          rightAction={<HistoryAmount sign={tx.sign} amount={tx.amount} />}
        />
      ))}
    </div>
  )
}

export function UserHomePage() {
  const navigate = useNavigate()
  const { currentUser } = useCurrentUser()

  // TODO: EC2/온프레미스로 bank 서비스 DB 분리 운영 시
  //       queryFn: fetchWalletBalance, queryKey: ['wallet', 'balance'] 로 변경
  const balanceQuery = useQuery({
    queryKey: ['charge', 'init'],
    queryFn: fetchChargeInit,
    retry: false,
  })

  const balance = balanceQuery.data?.balance ?? 0
  const activeErrorMessage = balanceQuery.error
    ? buildErrorMessage(API_SPEC.WALLET_001, balanceQuery.error)
    : null

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto pb-4">
      <header className="pt-1">
        <h1 className="text-xl font-bold text-foreground">{currentUser?.name ?? '사용자'}님</h1>
      </header>

      {activeErrorMessage && (
        <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm font-medium text-destructive">
          {activeErrorMessage}
        </div>
      )}

      <BalanceCard
        balance={balance}
        refreshing={balanceQuery.isFetching}
        onRefresh={() => void balanceQuery.refetch()}
      />

      <section aria-label="빠른 실행">
        <div className="grid grid-cols-3 gap-3">
          <QuickAction label="QR 결제" icon={<QrIcon />} onClick={() => navigate('/pay/scan')} />
          <QuickAction
            label="충전"
            icon={<PlusIcon />}
            onClick={() => navigate('/charge/amount')}
          />
          <QuickAction label="환불" icon={<UndoIcon />} onClick={() => navigate('/refund/check')} />
        </div>
      </section>

      <section aria-label="최근 거래" className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-bold text-foreground">최근 거래</h2>
          <button
            type="button"
            onClick={() => navigate('/mypage/payments')}
            className="text-xs font-semibold text-primary"
          >
            전체 보기
          </button>
        </div>

        <ErrorBoundary fallback={<EmptyState message="최근 거래 내역을 불러올 수 없습니다." />}>
          <Suspense
            fallback={
              <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
                불러오는 중...
              </div>
            }
          >
            <RecentTransactionList />
          </Suspense>
        </ErrorBoundary>
      </section>
    </div>
  )
}
