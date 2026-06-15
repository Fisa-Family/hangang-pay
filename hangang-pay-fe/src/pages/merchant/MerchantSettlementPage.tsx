import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Info } from 'lucide-react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '@/api/client'
import {
  createMerchantRedeemIntent,
  executeMerchantRedeem,
  fetchMerchantRedeemInit,
} from '@/api/merchant'
import {
  BackTitleHeader,
  Button,
  EmptyState,
  PageHeader,
  PinEntry,
  ProcessingView,
  SummaryCard,
  Toast,
} from '@/components/common'
import { formatMaskedAccount, formatWon } from '@/lib/format'

const PIN_LENGTH = 6

type Step = 'main' | 'pin' | 'processing'

export function MerchantSettlementPage() {
  const navigate = useNavigate()

  const [step, setStep] = useState<Step>('main')
  const [pin, setPin] = useState('')
  const [toast, setToast] = useState<string | null>(null)

  const initQuery = useQuery({
    queryKey: ['merchant', 'redeem'],
    queryFn: fetchMerchantRedeemInit,
    retry: false,
  })

  const redeemMutation = useMutation({
    // 1) intent 생성(PENDING 커밋, PIN 없음) → 2) 실행(PIN). bank 실패 시 intent가 남아 복구된다.
    // transactionUuid는 서버가 intent 응답으로 발급한 값을 그대로 execute에 사용한다.
    mutationFn: async (input: { amount: number; paymentPin: string }) => {
      const intent = await createMerchantRedeemIntent({ amount: input.amount })
      return executeMerchantRedeem(intent.transactionUuid, input.paymentPin)
    },
    onSuccess: (res) => {
      if (res.status === 'SUCCESS') {
        navigate('/merchant/settlement/complete', {
          replace: true,
          state: {
            amount: res.amount,
            approvalNumber: res.approvalNumber,
            exchangedAt: res.exchangedAt,
          },
        })
      } else {
        // 미확정(PENDING/UNKNOWN 등): 토스트 후 출금하기 복귀
        setToast('출금 상태를 확인하지 못했습니다. 잠시 후 다시 시도해주세요.')
        setPin('')
        setStep('main')
      }
    },
    onError: (err) => {
      setToast(err instanceof ApiError ? err.message : '출금을 신청하지 못했습니다.')
      setPin('')
      setStep('main')
    },
  })

  // 토스트 자동 소멸
  useEffect(() => {
    if (!toast) return
    const t = setTimeout(() => setToast(null), 2500)
    return () => clearTimeout(t)
  }, [toast])

  const availableAmount = initQuery.data?.availableAmount ?? 0

  // PIN이 6자리에 도달하면 출금 실행 — effect 내 setState를 피해 핸들러에서 직접 트리거
  const submitPin = (value: string) => {
    const next = value.slice(0, PIN_LENGTH)
    setPin(next)
    if (next.length === PIN_LENGTH && !redeemMutation.isPending) {
      setStep('processing')
      redeemMutation.mutate({
        amount: availableAmount,
        paymentPin: next,
      })
    }
  }

  // 출금 처리중
  if (step === 'processing') {
    return <ProcessingView title="출금을 신청하고 있어요" amount={availableAmount} />
  }

  // PIN 입력
  if (step === 'pin') {
    return (
      <div className="flex h-full flex-col">
        <PageHeader
          title="PIN번호 입력"
          rightAction={
            <button
              type="button"
              onClick={() => {
                setPin('')
                setStep('main')
              }}
              className="text-sm font-medium text-muted-foreground"
            >
              취소
            </button>
          }
        />
        <PinEntry pin={pin} length={PIN_LENGTH} onChange={submitPin} />
      </div>
    )
  }

  // 출금하기 (기본)
  return (
    <div className="flex h-full flex-col">
      <BackTitleHeader title="출금하기" onBack={() => navigate('/merchant/home')} />

      <div className="flex-1 space-y-4 overflow-y-auto">
        {initQuery.isLoading && (
          <div aria-hidden className="h-40 animate-pulse rounded-2xl bg-muted/40" />
        )}
        {initQuery.error && <EmptyState message="출금 정보를 불러올 수 없습니다." />}

        {initQuery.data && (
          <>
            <section className="rounded-2xl border border-border bg-card px-5 py-5 shadow-sm">
              <div className="flex items-center justify-between">
                <p className="text-sm text-muted-foreground">출금 가능 금액</p>
                <p className="text-[28px] font-bold tabular-nums text-foreground">
                  {formatWon(availableAmount)}
                </p>
              </div>
            </section>

            <SummaryCard
              divided
              rows={[
                {
                  label: '출금 계좌',
                  value: formatMaskedAccount(
                    initQuery.data.settlementAccount.institutionName,
                    initQuery.data.settlementAccount.accountNumber
                  ),
                },
              ]}
            />

            <section className="flex gap-3 rounded-2xl border border-primary/20 bg-primary/5 px-4 py-4">
              <Info size={16} className="mt-0.5 shrink-0 text-primary" />
              <div>
                <p className="text-sm font-semibold text-primary">
                  지역화폐 결제금, 바로 내 계좌로
                </p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  출금 신청 즉시 등록된 은행 계좌로 입금됩니다.
                </p>
              </div>
            </section>
          </>
        )}
      </div>

      {initQuery.data && (
        <div className="pt-3">
          <Button
            size="lg"
            disabled={availableAmount <= 0}
            onClick={() => {
              setPin('')
              setStep('pin')
            }}
          >
            출금 신청
          </Button>
        </div>
      )}

      <Toast open={!!toast} message={toast ?? ''} variant="error" />
    </div>
  )
}
