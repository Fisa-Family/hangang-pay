import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ApiError } from '@/api/client'
import { apiErrorMessages, isApiErrorCode } from '@/api/errorCodes'
import { fetchMerchantInfo } from '@/api/merchant'
import { fetchWalletBalance } from '@/api/wallet'
import { AmountInput, BackTitleHeader, EmptyState, NumberPad } from '@/components/common'
import { appendAmountDigit, formatWon, removeAmountDigit } from '@/lib/format'
import { cn } from '@/lib/utils'

const API_SPEC = {
  PAY_001: { id: 'PAY-001' },
  WALLET_001: { id: 'WALLET-001' },
} as const

function buildErrorMessage(spec: (typeof API_SPEC)[keyof typeof API_SPEC], error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code && isApiErrorCode(error.code)) return apiErrorMessages[error.code]
    return error.message
  }
  return `${spec.id} 요청에 실패했습니다. 네트워크 연결을 확인해 주세요.`
}

export function UserPayAmountPage() {
  const navigate = useNavigate()
  const { merchantId: rawMerchantId } = useParams<{ merchantId: string }>()
  const merchantId = Number(rawMerchantId)
  const isValidId = Number.isInteger(merchantId) && merchantId >= 1

  const merchantQuery = useQuery({
    queryKey: ['merchant', merchantId],
    queryFn: () => fetchMerchantInfo(merchantId),
    retry: false,
    enabled: isValidId,
  })

  const balanceQuery = useQuery({
    queryKey: ['wallet', 'balance'],
    queryFn: fetchWalletBalance,
    retry: false,
  })

  const [amount, setAmount] = useState(0)

  if (!isValidId) {
    return (
      <div className="flex h-full flex-col gap-4">
        <BackTitleHeader title="결제" onBack={() => navigate(-1)} />
        <EmptyState
          message="잘못된 가맹점 정보입니다."
          action={
            <button
              type="button"
              onClick={() => navigate('/home', { replace: true })}
              className="text-sm font-semibold text-primary"
            >
              홈으로 가기
            </button>
          }
        />
      </div>
    )
  }

  const merchant = merchantQuery.data
  const balance = balanceQuery.data?.balance ?? 0
  const remainingAfter = balance - amount
  const overBalance = amount > balance
  const balanceErrorMessage = balanceQuery.error
    ? buildErrorMessage(API_SPEC.WALLET_001, balanceQuery.error)
    : null

  return (
    <div className="flex h-full flex-col gap-4 overflow-y-auto">
      <BackTitleHeader title="결제" onBack={() => navigate(-1)} />

      {merchantQuery.isLoading && (
        <div aria-hidden className="h-[72px] animate-pulse rounded-2xl bg-muted/40" />
      )}
      {merchantQuery.error && <EmptyState message="가맹점 정보를 불러올 수 없습니다." />}
      {merchant && (
        <section className="rounded-2xl border border-border/40 bg-card p-4 shadow-sm">
          <p className="text-base font-bold text-foreground">{merchant.merchantName}</p>
          <p className="mt-1 text-sm text-muted-foreground">{merchant.address}</p>
        </section>
      )}

      {balanceQuery.isLoading && (
        <div aria-hidden className="h-[88px] animate-pulse rounded-2xl bg-muted/40" />
      )}
      {balanceErrorMessage && (
        <section className="flex items-center justify-between gap-3 rounded-2xl border border-destructive/30 bg-destructive/5 p-4 shadow-sm">
          <p className="text-sm font-medium text-destructive">{balanceErrorMessage}</p>
          <button
            type="button"
            onClick={() => void balanceQuery.refetch()}
            className="shrink-0 text-sm font-semibold text-primary"
          >
            다시 시도
          </button>
        </section>
      )}
      {balanceQuery.data && (
        <section className="rounded-2xl border border-border/40 bg-card p-4 shadow-sm">
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm text-muted-foreground">현재 잔액</p>
            <p className="text-base font-bold tabular-nums text-foreground">{formatWon(balance)}</p>
          </div>
          <div className="mt-2 flex items-center justify-between gap-3">
            <p className="text-sm text-muted-foreground">결제 후 잔액</p>
            <p
              className={cn(
                'text-base font-bold tabular-nums',
                remainingAfter < 0 ? 'text-destructive' : 'text-foreground'
              )}
            >
              {formatWon(remainingAfter)}
            </p>
          </div>
        </section>
      )}

      <AmountInput value={amount} placeholder="0원" onClick={() => {}} />

      <p
        className={cn(
          'text-center text-sm',
          overBalance ? 'text-destructive' : 'text-muted-foreground'
        )}
      >
        {overBalance ? '잔액이 부족합니다' : '잔액 내에서 결제할 수 있어요'}
      </p>

      <NumberPad
        onDigit={(d) => setAmount((v) => appendAmountDigit(v, d))}
        onBackspace={() => setAmount(removeAmountDigit)}
        onClear={() => setAmount(0)}
      />

      <button
        type="button"
        disabled={amount <= 0 || overBalance}
        onClick={() => {
          /* U-PAY-03 범위 외 */
        }}
        className="h-12 w-full rounded-2xl bg-primary text-base font-semibold text-primary-foreground disabled:opacity-40"
      >
        결제하기
      </button>
    </div>
  )
}
