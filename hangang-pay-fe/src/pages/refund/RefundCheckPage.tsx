import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { fetchExchangeInit } from '@/api/exchange'
import type { ExchangeInitResult } from '@/api/exchange'
import { fetchAccounts } from '@/api/accounts'
import { ApiError } from '@/api/client'
import { apiErrorMessages, isApiErrorCode } from '@/api/errorCodes'
import { AccountRow, BackTitleHeader, Button, StatusBadge } from '@/components/common'
import { formatWon } from '@/lib/format'

function buildErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code && isApiErrorCode(error.code)) return apiErrorMessages[error.code]
    return error.message
  }
  return '환불 정보를 불러오지 못했습니다. 네트워크 연결을 확인해 주세요.'
}

export function RefundCheckPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const processingError = (location.state as { error?: string } | null)?.error
  const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null)

  const [exchange, setExchange] = useState<{
    data: ExchangeInitResult | null
    loading: boolean
    error: unknown
  }>({ data: null, loading: true, error: null })

  useEffect(() => {
    fetchExchangeInit()
      .then((data) => setExchange({ data, loading: false, error: null }))
      .catch((error) => setExchange({ data: null, loading: false, error }))
  }, [])

  const accountsQuery = useQuery({
    queryKey: ['accounts'],
    queryFn: fetchAccounts,
    select: (accounts) => {
      return [...accounts].sort((a) => (a.accountType === 'PRIMARY' ? -1 : 1))
    },
  })

  // PRIMARY 계좌를 기본 선택
  const accounts = accountsQuery.data ?? []
  const effectiveSelectedId =
    selectedAccountId ?? accounts.find((a) => a.accountType === 'PRIMARY')?.accountId ?? null

  const isLoading = exchange.loading || accountsQuery.isLoading
  const isError = !!exchange.error || accountsQuery.isError
  const error = exchange.error ?? accountsQuery.error

  const data = exchange.data
  const canSubmit = data?.eligible === true && effectiveSelectedId !== null

  const handleSubmit = () => {
    if (!data || !effectiveSelectedId) return
    navigate('/refund/pin', {
      state: {
        nextRoute: '/refund/processing',
        cancelRoute: '/refund/check',
        amount: data.walletBalance,
        accountId: effectiveSelectedId,
      },
    })
  }

  return (
    <div className="flex h-dvh flex-col bg-background">
      <BackTitleHeader title="환불" onBack={() => navigate('/home')} />

      <div className="flex flex-1 flex-col gap-3 overflow-y-auto px-5 pt-4 pb-6">
        {isLoading && (
          <div className="flex flex-1 items-center justify-center">
            <p className="text-sm text-muted-foreground">환불 정보를 확인하고 있어요</p>
          </div>
        )}

        {processingError && (
          <div className="rounded-2xl bg-card p-5">
            <p className="text-sm text-destructive">{processingError}</p>
          </div>
        )}

        {isError && (
          <div className="rounded-2xl bg-card p-5">
            <p className="text-sm text-destructive">{buildErrorMessage(error)}</p>
          </div>
        )}

        {!isLoading && !isError && data && (
          <>
            {/* 현재 잔액 카드 */}
            <div className="flex items-center justify-between rounded-2xl bg-card px-5 py-6">
              <span className="text-sm text-muted-foreground">현재 잔액</span>
              <span className="text-[28px] font-bold tabular-nums text-foreground">
                {formatWon(data.walletBalance)}
              </span>
            </div>

            {/* 환불 가능 여부 배너 */}
            <div
              className={
                data.eligible
                  ? 'flex items-start gap-3 rounded-2xl bg-success/10 px-3 py-4'
                  : 'flex items-start gap-3 rounded-2xl bg-muted px-3 py-4'
              }
            >
              <StatusBadge variant={data.eligible ? 'success' : 'neutral'}>
                {data.eligible ? '환불 가능' : '환불 불가'}
              </StatusBadge>
              <p className="text-sm leading-snug text-foreground">
                {data.eligible
                  ? '충전금의 60% 이상을 사용하셨습니다.'
                  : '충전금의 60% 이상 사용 후 환불 가능합니다.'}
              </p>
            </div>

            {/* 입금 계좌 선택 */}
            {accounts.length > 0 && (
              <div className="rounded-2xl bg-card px-5 py-5">
                <p className="mb-4 text-base font-bold text-foreground">입금 계좌</p>
                <div className="flex flex-col gap-3">
                  {accounts.map((account) => (
                    <button
                      key={account.accountId}
                      type="button"
                      className="w-full text-left"
                      onClick={() => setSelectedAccountId(account.accountId)}
                    >
                      <AccountRow
                        mode="select"
                        bankName={account.bankName}
                        maskedAccountNumber={account.maskedAccountNumber}
                        primary={account.accountType === 'PRIMARY'}
                        selected={effectiveSelectedId === account.accountId}
                      />
                    </button>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>

      <div className="px-5 py-4">
        <Button
          size="lg"
          className="w-full rounded-2xl"
          disabled={!canSubmit}
          onClick={handleSubmit}
        >
          환불 신청하기
        </Button>
      </div>
    </div>
  )
}
