import { useQuery, useSuspenseQuery } from '@tanstack/react-query'
import { Suspense } from 'react'
import { useNavigate } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useCurrentUser } from '@/auth/useCurrentUser'
import { BalanceCard, EmptyState, ErrorBoundary, ListItem } from '@/components/common'
import { fetchWalletBalance } from '@/api/wallet'
import { fetchUserHistories, type UserHistoryItem, type HistoryType } from '@/api/user'
import { ApiError, type ApiError as ApiErrorType } from '@/api/client'
import { apiErrorMessages, isApiErrorCode, apiUserErrorMessages } from '@/api/errorCodes'
import { formatWon } from '@/lib/format'
import { cn } from '@/lib/utils'

// rest_api.md 기준 API 명세
const API_SPEC = {
  WALLET_001: { id: 'WALLET-001', path: 'GET /wallet/balance', role: 'USER 또는 MERCHANT' },
  MY_002:     { id: 'MY-002',     path: 'GET /users/histories', role: 'USER' },
} as const

// errorCodes.ts 기반 오류 메시지 반환
function buildErrorMessage(spec: (typeof API_SPEC)[keyof typeof API_SPEC], error: unknown): string {
  if (!(error instanceof ApiError)) {
    return apiUserErrorMessages[spec.id]?.[0] ?? '서비스에 연결할 수 없습니다. 네트워크 연결을 확인해 주세요.'
  }

  const { status, code } = error as ApiErrorType

  if (code && isApiErrorCode(code)) return apiErrorMessages[code]

  return apiUserErrorMessages[spec.id]?.[status] ?? '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'
}

// 최대 조회 수
const HISTORY_LIMIT = 5

// 거래 유형 라벨
const TYPE_LABEL: Record<string, string> = {
  PAYMENT: '결제',
  CHARGE: '충전',
  EXCHANGE: '환불',
}

// 날짜 포맷 MM.DD HH:mm
function formatHistoryDate(isoString: string): string {
  const d = new Date(isoString)
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${mm}.${dd} ${hh}:${min}`
}

// 입금 여부 판별
function isCredit(type: HistoryType): boolean {
  return type === 'CHARGE' || type === 'EXCHANGE'
}

// 상대방 이름, 없으면 거래 유형으로 대체
function resolveTitle(tx: UserHistoryItem): string {
  return tx.counterpartName || TYPE_LABEL[tx.type] || tx.type
}

// 빠른 실행 버튼
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

// 아이콘 (BottomNav.tsx 동일 스타일)
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

// 금액 표시 (입금 초록, 지출 빨강)
function HistoryAmount({ type, amount }: { type: HistoryType; amount: number }) {
  const credit = isCredit(type)
  return (
    <span
      className={cn(
        'shrink-0 text-right text-sm font-bold tabular-nums',
        credit ? 'text-success' : 'text-destructive'
      )}
    >
      {credit ? '+' : '-'}
      {formatWon(amount)}
    </span>
  )
}

// 최근 거래 목록 (Suspense 전용, 오류는 상위 ErrorBoundary 위임)
function RecentTransactionList() {
  const { data } = useSuspenseQuery({
    queryKey: ['users', 'histories', 'PAYMENT'],
    queryFn: () => fetchUserHistories({ historyType: 'PAYMENT', size: HISTORY_LIMIT }),
    retry: false,
  })

  const histories = data.response.content

  if (histories.length === 0) {
    return <EmptyState message="최근 거래 내역이 없습니다." />
  }

  return (
    <div className="flex flex-col gap-2">
      {histories.map((tx) => (
        <ListItem
          key={tx.id}
          title={resolveTitle(tx)}
          description={formatHistoryDate(tx.createdAt)}
          rightAction={<HistoryAmount type={tx.type} amount={tx.amount} />}
        />
      ))}
    </div>
  )
}

export function UserHomePage() {
  const navigate = useNavigate()
  const { currentUser } = useCurrentUser()

  // 잔액 조회
  const balanceQuery = useQuery({
    queryKey: ['wallet', 'balance'],
    queryFn: fetchWalletBalance,
    retry: false,
  })

  const balance = balanceQuery.data?.balance ?? 0

  // 내역 오류는 ErrorBoundary로 위임하므로 잔액 오류만 처리
  const activeErrorMessage = balanceQuery.error
    ? buildErrorMessage(API_SPEC.WALLET_001, balanceQuery.error)
    : null

  return (
    <div className="flex-1 min-h-0 overflow-y-auto flex flex-col gap-5 pb-4">
      {/* 인사 */}
      <header className="pt-1">
        <h1 className="text-xl font-bold text-foreground">
          {currentUser?.name ?? '사용자'}님
        </h1>
      </header>

      {/* API 오류 안내 */}
      {activeErrorMessage && (
        <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm font-medium text-destructive">
          {activeErrorMessage}
        </div>
      )}

      {/* 잔액 카드 */}
      <BalanceCard
        balance={balance}
        refreshing={balanceQuery.isFetching}
        onRefresh={() => void balanceQuery.refetch()}
      />

      {/* 빠른 실행 */}
      <section aria-label="빠른 실행">
        <div className="grid grid-cols-3 gap-3">
          <QuickAction
            label="QR 결제"
            icon={<QrIcon />}
            onClick={() => navigate('/pay/scan')}
          />
          <QuickAction
            label="충전"
            icon={<PlusIcon />}
            onClick={() => navigate('/charge/amount')}
          />
          <QuickAction
            label="환불"
            icon={<UndoIcon />}
            onClick={() => navigate('/refund/check')}
          />
        </div>
      </section>

      {/* 최근 거래 */}
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

        {/* TODO: 로딩 중 텍스트 → 실제 레이아웃 모양의 회색 박스(스켈레톤 UI)로 교체 */}
        <ErrorBoundary fallback={<EmptyState message="최근 거래 내역이 없습니다." />}>
          <Suspense
            fallback={
              <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
                불러오는 중…
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
