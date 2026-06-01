import { useQuery, useSuspenseQuery } from '@tanstack/react-query'
import { Suspense } from 'react'
import { useNavigate } from 'react-router-dom'
import type { ReactNode } from 'react'
import { QrCode, Plus, Undo2 } from 'lucide-react'
import { BalanceCard, EmptyState, ErrorBoundary } from '@/components/common'
import { fetchRecentTransactions, useUserProfile, type HistoryListItem } from '@/api/user'
import { fetchWalletBalance } from '@/api/wallet'
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

const HISTORY_FETCH_LIMIT = 6

function formatHistoryDate(isoString: string): string {
  const d = new Date(isoString)
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${mm}.${dd}`
}

interface QuickActionProps {
  label: string
  icon: ReactNode
  onClick: () => void
  tint?: 'primary' | 'success' | 'warning'
}

const tintClasses: Record<NonNullable<QuickActionProps['tint']>, string> = {
  primary: 'bg-primary/15 text-primary',
  success: 'bg-success/15 text-success',
  warning: 'bg-warning/15 text-warning',
}

function QuickAction({ label, icon, onClick, tint = 'primary' }: QuickActionProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'flex flex-col items-center gap-2 rounded-xl px-2 py-4 text-sm font-semibold shadow-sm transition-opacity active:opacity-70',
        tintClasses[tint]
      )}
    >
      {icon}
      <span>{label}</span>
    </button>
  )
}

function historyAmountColor(displayType: HistoryListItem['displayType']): string {
  switch (displayType) {
    case 'CHARGE':
    case 'CANCEL':
      return 'text-primary'
    case 'EXCHANGE':
      return 'text-warning'
    default:
      return 'text-foreground'
  }
}

function HistoryAmount({
  sign,
  amount,
  displayType,
}: {
  sign: '+' | '-'
  amount: number
  displayType: HistoryListItem['displayType']
}) {
  return (
    <span
      className={cn(
        'shrink-0 text-right text-sm font-bold tabular-nums',
        historyAmountColor(displayType)
      )}
    >
      {sign}
      {formatWon(amount)}
    </span>
  )
}

function RecentTransactionList() {
  const { data } = useSuspenseQuery({
    queryKey: ['users', 'recent-histories'],
    queryFn: () => fetchRecentTransactions(HISTORY_FETCH_LIMIT),
    retry: false,
  })

  const histories = data

  if (histories.length === 0) {
    return <EmptyState message="최근 거래 내역이 없습니다." />
  }

  return (
    <div className="flex flex-col">
      {histories.map((tx: HistoryListItem, idx) => (
        <div
          key={tx.id}
          className="flex items-center gap-3 py-4.5"
          style={{ borderBottom: idx < histories.length - 1 ? '1px solid var(--border)' : 'none' }}
        >
          <span className="w-10 shrink-0 text-[13px] text-muted-foreground">
            {formatHistoryDate(tx.createdAt)}
          </span>
          <span className="flex-1 truncate text-[14px] font-semibold text-foreground">
            {tx.counterpartName}
          </span>
          <HistoryAmount sign={tx.sign} amount={tx.amount} displayType={tx.displayType} />
        </div>
      ))}
    </div>
  )
}

export function UserHomePage() {
  const navigate = useNavigate()

  const profileQuery = useUserProfile()

  const balanceQuery = useQuery({
    queryKey: ['wallet', 'balance'],
    queryFn: fetchWalletBalance,
    retry: false,
  })

  const balance = balanceQuery.data?.balance ?? 0
  const activeErrorMessage = balanceQuery.error
    ? buildErrorMessage(API_SPEC.WALLET_001, balanceQuery.error)
    : null

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto px-3 pt-5 pb-6">
      <header>
        <h1 className="text-2xl font-bold text-foreground">
          {profileQuery.data?.username ?? '사용자'}님
        </h1>
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
          <QuickAction
            label="QR 결제"
            icon={<QrCode size={24} aria-hidden />}
            onClick={() => navigate('/pay/scan')}
          />
          <QuickAction
            label="충전"
            icon={<Plus size={24} aria-hidden />}
            onClick={() => navigate('/charge/amount')}
            tint="success"
          />
          <QuickAction
            label="환불"
            icon={<Undo2 size={24} aria-hidden />}
            onClick={() => navigate('/refund/check')}
            tint="warning"
          />
        </div>
      </section>

      <div className="rounded-2xl bg-card p-5 shadow-sm">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-base font-bold text-foreground">최근 거래</h2>
          <button
            type="button"
            onClick={() => navigate('/mypage/payments')}
            className="text-xs font-medium text-muted-foreground"
          >
            전체보기
          </button>
        </div>

        <ErrorBoundary fallback={<EmptyState message="최근 거래 내역을 불러올 수 없습니다." />}>
          <Suspense
            fallback={
              <div className="py-4 text-center text-sm text-muted-foreground">불러오는 중…</div>
            }
          >
            <RecentTransactionList />
          </Suspense>
        </ErrorBoundary>
      </div>
    </div>
  )
}
