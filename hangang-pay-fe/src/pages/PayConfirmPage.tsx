import { useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { createPaymentIntent, fetchMerchantForPayment } from '@/api/payment'
import { fetchWalletBalance } from '@/api/wallet'
import { ApiError } from '@/api/client'
import { formatWon } from '@/lib/format'
import { BackspaceIcon, Button, PageHeader } from '@/components/common'

interface LocationState {
  merchantId: string
}

const PAD_KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '00', '0', '⌫']

export function PayConfirmPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const params = useParams<{ merchantId: string }>()
  const merchantId = params.merchantId ?? (location.state as LocationState | null)?.merchantId ?? ''

  const [amountStr, setAmountStr] = useState('')

  const merchantQuery = useQuery({
    queryKey: ['merchant', 'payment-target', merchantId],
    queryFn: () => fetchMerchantForPayment(merchantId),
    enabled: !!merchantId,
    retry: false,
  })

  const balanceQuery = useQuery({
    queryKey: ['wallet', 'balance'],
    queryFn: fetchWalletBalance,
    retry: false,
  })

  const intentMutation = useMutation({
    mutationFn: createPaymentIntent,
    onSuccess: (data) => {
      navigate('/pay/pin', {
        state: {
          nextRoute: '/pay/processing',
          transactionUuid: data.transactionUuid,
          amount: Number(amountStr) || 0,
          merchantName: merchantQuery.data?.merchantName ?? '',
          balance: balanceQuery.data?.balance ?? 0,
        },
      })
    },
  })

  const amount = Number(amountStr) || 0
  const balance = balanceQuery.data?.balance ?? 0
  const overBalance = amount > balance && balance > 0
  const canPay = amount > 0 && !overBalance && !!merchantQuery.data && !intentMutation.isPending

  function handleKey(key: string) {
    if (intentMutation.isPending) return
    if (key === '⌫') {
      setAmountStr((p) => p.slice(0, -1))
      return
    }
    setAmountStr((p) => {
      if (!p && (key === '0' || key === '00')) return ''
      const next = p + key
      if (Number(next) > 100_000_000) return p
      return next
    })
  }

  function handlePay() {
    if (!canPay || !merchantQuery.data) return
    intentMutation.mutate({
      merchantPartyId: merchantQuery.data.partyId,
      amount,
    })
  }

  const merchant = merchantQuery.data
  const merchantInitial = merchant?.merchantName?.[0] ?? 'M'

  return (
    <div className="flex h-dvh flex-col bg-white">
      {/* 헤더 */}
      <PageHeader title="결제" onBack={() => navigate(-1)} className="px-4 pt-14" />

      {/* 가맹점 카드 */}
      <div className="mx-4 rounded-2xl bg-white p-4 shadow-sm ring-1 ring-black/6">
        {merchantQuery.isLoading ? (
          <div className="h-14 animate-pulse rounded-lg bg-muted/40" />
        ) : merchant ? (
          <div className="flex items-center justify-between gap-3">
            <div className="min-w-0">
              <p className="truncate text-[15px] font-bold text-foreground">
                {merchant.merchantName}
              </p>
              {merchant.address && (
                <p className="mt-0.5 truncate text-[13px] text-muted-foreground">
                  {merchant.address}
                </p>
              )}
              <p className="mt-0.5 text-[12px] text-muted-foreground/60">가맹점 ID: {merchantId}</p>
            </div>
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-pink-500 text-sm font-bold text-white">
              {merchantInitial}
            </div>
          </div>
        ) : (
          <p className="text-sm text-destructive">가맹점 정보를 불러올 수 없습니다.</p>
        )}
      </div>

      {/* 현재 잔액 */}
      <div className="flex items-center justify-between px-5 py-3">
        <span className="text-sm text-muted-foreground">현재 잔액</span>
        <span className="text-sm font-semibold tabular-nums text-foreground">
          {balanceQuery.isLoading ? '—' : formatWon(balance)}
        </span>
      </div>

      {/* 금액 표시 */}
      <div className="flex flex-1 flex-col items-center justify-center gap-1.5">
        <p
          className={`text-[42px] font-bold tabular-nums leading-tight ${overBalance ? 'text-destructive' : 'text-foreground'}`}
        >
          {amount > 0 ? formatWon(amount) : '0원'}
        </p>
        <p className="text-[13px] text-muted-foreground">
          {overBalance ? '잔액이 부족합니다' : '잔액 내에서 결제할 수 있어요'}
        </p>
        {intentMutation.error && (
          <p className="text-[13px] text-destructive">
            {intentMutation.error instanceof ApiError
              ? intentMutation.error.message
              : '결제 처리 중 오류가 발생했습니다.'}
          </p>
        )}
      </div>

      {/* 숫자 패드 (1-9, 00, 0, ⌫) */}
      <div className="grid grid-cols-3 border-t border-border/60">
        {PAD_KEYS.map((key) => (
          <button
            key={key}
            type="button"
            onClick={() => handleKey(key)}
            disabled={intentMutation.isPending}
            className="flex h-15 items-center justify-center border-b border-r border-border/60 text-xl font-semibold text-foreground transition-colors active:bg-muted disabled:opacity-40"
          >
            {key === '⌫' ? <BackspaceIcon /> : key}
          </button>
        ))}
      </div>

      {/* 결제하기 버튼 */}
      <div className="px-4 pb-8 pt-3">
        <Button size="lg" className="rounded-2xl" disabled={!canPay} onClick={handlePay}>
          {intentMutation.isPending ? '처리 중…' : '결제하기'}
        </Button>
      </div>
    </div>
  )
}
