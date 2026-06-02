import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchChargeInit } from '@/api/charge'
import { BackspaceIcon, BackTitleHeader, Button, Toast, type ToastState } from '@/components/common'
import { formatWon } from '@/lib/format'
import { cn } from '@/lib/utils'

const PAD_KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '00', '0', '⌫'] as const

// 계좌번호 마스킹
function maskAccount(num: string): string {
  if (num.length <= 4) return num
  return `${num.slice(0, -4).replace(/\d/g, '*')}${num.slice(-4)}`
}

// 천단위 숫자 포맷
function formatNumber(value: number): string {
  return new Intl.NumberFormat('ko-KR').format(value)
}

// 충전 금액 입력 화면
export function ChargeAmountPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const locationState = location.state as { error?: string; amount?: number } | null
  const chargeError = locationState?.error ?? ''
  const [amountStr, setAmountStr] = useState(
    locationState?.amount ? String(locationState.amount) : ''
  )
  const [toast, setToast] = useState<ToastState | null>(
    chargeError ? { message: chargeError, variant: 'error' } : null
  )

  useEffect(() => {
    if (chargeError) {
      // history.state는 F5 새로고침 후에도 유지되므로 오류 표시 후 즉시 제거
      window.history.replaceState(null, '')
    }
  }, [chargeError])

  useEffect(() => {
    if (!toast) return
    const id = window.setTimeout(() => setToast(null), 3000)
    return () => window.clearTimeout(id)
  }, [toast])
  const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null)

  const initQuery = useQuery({
    queryKey: ['charge', 'init'],
    queryFn: fetchChargeInit,
    retry: false,
    staleTime: 0,
  })

  const data = initQuery.data
  const accounts = useMemo(() => data?.accounts ?? [], [data])
  const discountRate = data?.discountRate ?? 0.1

  // 활성 계좌 ID 결정
  const activeAccountId = useMemo(() => {
    if (selectedAccountId !== null) return selectedAccountId
    const primary = accounts.find((a) => a.isPrimary)
    return primary?.accountId ?? accounts[0]?.accountId ?? null
  }, [accounts, selectedAccountId])

  const selectedAccount = accounts.find((a) => a.accountId === activeAccountId) ?? null

  const amount = Number(amountStr) || 0
  const discountAmount = Math.floor(amount * discountRate)
  const finalAmount = amount - discountAmount
  const remainingLimit = data?.remainingLimit ?? 0
  // 한도 초과 여부 — !!data: 로딩 전 오탐 방지, remainingLimit=0은 정상 감지
  const overLimit = !!data && amount > 0 && amount > remainingLimit

  // 충전 버튼 활성화 조건
  const canCharge =
    amount > 0 && !overLimit && selectedAccount !== null && !!data && !initQuery.isLoading

  // 키패드 입력 처리
  function handleKey(key: string) {
    if (key === '⌫') {
      setAmountStr((prev) => prev.slice(0, -1))
      return
    }
    setAmountStr((prev) => {
      if (!prev && (key === '0' || key === '00')) return ''
      const next = prev + key
      if (Number(next) > 10_000_000) return prev
      return next
    })
  }

  // PIN 입력 화면 이동
  function handleCharge() {
    if (!canCharge || !data || !selectedAccount) return
    navigate('/charge/pin', {
      state: {
        nextRoute: '/charge/processing',
        cancelRoute: '/charge/amount',
        transactionUuid: data.transactionUuid,
        institutionId: selectedAccount.institutionId,
        accountId: selectedAccount.accountId,
        amount,
        discountRate,
      },
    })
  }

  // 계좌 순환 선택
  function cycleAccount() {
    if (accounts.length <= 1) return
    const idx = accounts.findIndex((a) => a.accountId === activeAccountId)
    setSelectedAccountId(accounts[(idx + 1) % accounts.length].accountId)
  }

  return (
    <div className="flex h-dvh flex-col bg-background">
      <BackTitleHeader title="충전" onBack={() => navigate('/home', { replace: true })} />

      {/* 잔액·한도 카드 */}
      <div className="mx-5 mt-2 rounded-2xl bg-card p-5">
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted-foreground">현재 잔액</span>
          {initQuery.isLoading ? (
            <div className="h-5 w-24 animate-pulse rounded bg-muted" />
          ) : (
            <span className="text-[18px] font-bold tabular-nums text-foreground">
              {formatWon(data?.balance ?? 0)}
            </span>
          )}
        </div>
        <div className="mt-3 flex flex-col gap-1 border-t border-border/40 pt-3">
          <div className="flex items-center justify-between">
            <span className="text-xs text-muted-foreground">월 충전 한도</span>
            {initQuery.isLoading ? (
              <div className="h-3.5 w-16 animate-pulse rounded bg-muted" />
            ) : (
              <span className="text-xs tabular-nums text-muted-foreground">
                {formatWon(data?.monthlyLimit ?? 0)}
              </span>
            )}
          </div>
          <div className="flex items-center justify-between">
            <span className="text-xs text-muted-foreground">남은 한도</span>
            {initQuery.isLoading ? (
              <div className="h-3.5 w-16 animate-pulse rounded bg-muted" />
            ) : (
              <span
                className={`text-xs font-semibold tabular-nums ${overLimit ? 'text-destructive' : 'text-primary'}`}
              >
                {formatWon(remainingLimit)}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* 충전 금액 — 우측 정렬, flex-1 */}
      <div className="flex flex-1 flex-col items-end justify-center gap-1.5 px-5">
        <p className="text-sm text-muted-foreground">충전 금액</p>
        <p className="text-[44px] font-bold leading-none">
          <span className={`tabular-nums ${overLimit ? 'text-destructive' : 'text-foreground'}`}>
            {amount > 0 ? formatNumber(amount) : '0'}
          </span>
          <span className="text-muted-foreground">원</span>
        </p>
        {amount > 0 ? (
          <div className="flex items-center gap-2">
            <span className="text-[13px] text-muted-foreground">
              할인 ({Math.round(discountRate * 100)}%)
            </span>
            <span className="text-[13px] font-semibold tabular-nums text-success">
              -{formatWon(discountAmount)}
            </span>
          </div>
        ) : (
          <p className="text-[13px] text-muted-foreground">충전 금액을 입력해주세요</p>
        )}
        {overLimit && <p className="text-[13px] text-destructive">남은 한도를 초과했습니다</p>}
      </div>

      {/* 결제 금액, 계좌 카드 */}
      <div className="mx-5 mb-4 rounded-2xl bg-muted px-5 py-4">
        <div className="flex items-center justify-between gap-3">
          <div className="flex min-w-0 flex-col gap-1">
            <span className="text-sm font-medium text-foreground">결제 금액</span>
            {initQuery.isLoading ? (
              <div className="h-4 w-28 animate-pulse rounded bg-muted/60" />
            ) : selectedAccount ? (
              <button
                type="button"
                onClick={cycleAccount}
                className="flex items-center gap-1 text-left"
              >
                <span className="text-xs text-muted-foreground">
                  {selectedAccount.institutionName} {maskAccount(selectedAccount.accountNumber)}
                </span>
                {accounts.length > 1 && (
                  <span className="shrink-0 text-[10px] font-semibold text-primary">변경 ›</span>
                )}
              </button>
            ) : (
              <span className="text-xs text-muted-foreground">계좌를 먼저 연결해 주세요</span>
            )}
          </div>
          <p className="shrink-0 text-[22px] font-bold tabular-nums text-foreground">
            {amount > 0 ? formatWon(finalAmount) : '0원'}
          </p>
        </div>
      </div>

      {/* 키패드 - 핀테크 결제 스타일, 분리선 기반 */}
      <div className="grid grid-cols-3 border-t border-border">
        {PAD_KEYS.map((key, i) => {
          const isLastCol = (i + 1) % 3 === 0
          const isLastRow = i >= 9
          return (
            <button
              key={key}
              type="button"
              onClick={() => handleKey(key)}
              className={cn(
                'flex h-[64px] items-center justify-center bg-background text-[22px] font-bold text-foreground',
                'transition-colors active:bg-muted',
                !isLastCol && 'border-r border-border',
                !isLastRow && 'border-b border-border'
              )}
            >
              {key === '⌫' ? (
                <BackspaceIcon width={24} height={24} className="text-foreground" />
              ) : (
                key
              )}
            </button>
          )
        })}
      </div>

      {/* 충전하기 버튼 */}
      <div className="px-5 pb-6 pt-2">
        <Button size="lg" className="rounded-2xl" disabled={!canCharge} onClick={handleCharge}>
          {initQuery.isLoading ? '불러오는 중…' : '충전하기'}
        </Button>
      </div>

      <Toast open={toast !== null} message={toast?.message ?? ''} variant={toast?.variant} />
    </div>
  )
}
