import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '@/api/client'
import {
  cancelMerchantPayment,
  fetchMerchantPaymentDetail,
  type PaymentCancelResult,
} from '@/api/merchant'
import { Store } from 'lucide-react'
import {
  BackTitleHeader,
  Button,
  ConfirmDialog,
  EmptyState,
  PageHeader,
  PinEntry,
  ProcessingView,
  ResultState,
  Toast,
} from '@/components/common'
import { formatDateTime, formatWon } from '@/lib/format'

const PIN_LENGTH = 6

type Step = 'detail' | 'pin' | 'processing' | 'result'

export function MerchantPaymentDetailPage() {
  const navigate = useNavigate()
  const { transactionId: rawId } = useParams<{ transactionId: string }>()
  const transactionId = Number(rawId)
  const isValidId = Number.isInteger(transactionId) && transactionId >= 1

  const [step, setStep] = useState<Step>('detail')
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [pin, setPin] = useState('')
  const [toast, setToast] = useState<string | null>(null)
  const [result, setResult] = useState<PaymentCancelResult | null>(null)

  const detailQuery = useQuery({
    queryKey: ['merchant', 'payment', transactionId],
    queryFn: () => fetchMerchantPaymentDetail(transactionId),
    enabled: isValidId,
    retry: false,
  })

  const cancelMutation = useMutation({
    mutationFn: (paymentPin: string) => cancelMerchantPayment(transactionId, paymentPin),
    onSuccess: (res) => {
      if (res.status === 'SUCCESS') {
        setResult(res)
        setStep('result')
      } else {
        // 미확정(UNKNOWN): 토스트 후 상세 복귀
        setToast('취소 상태를 확인하지 못했습니다. 잠시 후 다시 시도해주세요.')
        setPin('')
        setStep('detail')
      }
    },
    onError: (err) => {
      setToast(err instanceof ApiError ? err.message : '결제를 취소하지 못했습니다.')
      setPin('')
      setStep('detail')
    },
  })

  // 토스트 자동 소멸
  useEffect(() => {
    if (!toast) return
    const t = setTimeout(() => setToast(null), 2500)
    return () => clearTimeout(t)
  }, [toast])

  // PIN 입력이 6자리에 도달하면 취소 요청 — effect 내 setState를 피하려 이벤트 핸들러에서 직접 트리거
  const submitPin = (value: string) => {
    const next = value.slice(0, PIN_LENGTH)
    setPin(next)
    if (next.length === PIN_LENGTH && !cancelMutation.isPending) {
      setStep('processing')
      cancelMutation.mutate(next)
    }
  }

  if (!isValidId) {
    return (
      <div className="flex h-full flex-col">
        <BackTitleHeader title="결제 상세" onBack={() => navigate('/merchant/payments')} />
        <EmptyState message="잘못된 결제 정보입니다." />
      </div>
    )
  }

  const detail = detailQuery.data?.detail
  const amount = result?.amount ?? detail?.amount ?? 0

  // 취소 처리중
  if (step === 'processing') {
    return <ProcessingView title="결제를 취소하고 있어요" amount={amount} />
  }

  // 취소 완료
  if (step === 'result' && result) {
    return (
      <div className="flex h-full items-center justify-center">
        <ResultState
          variant="success"
          title={formatWon(result.amount)}
          description="취소 완료"
          details={
            <span className="break-all font-mono text-xs text-muted-foreground">
              {result.txHash}
            </span>
          }
          primaryText="홈으로"
          onPrimary={() => navigate('/merchant/home')}
        />
      </div>
    )
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
                setStep('detail')
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

  // 결제 상세 (기본)
  const canCancel = detail?.transactionType === 'PAYMENT' && detail?.cancelAvailable === true

  return (
    <div className="flex h-full flex-col bg-background">
      <BackTitleHeader title="결제 상세" onBack={() => navigate(-1)} />

      <main className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto pb-4 pt-5">
        {detailQuery.isLoading && (
          <div aria-hidden className="h-40 animate-pulse rounded-2xl bg-muted/40" />
        )}
        {detailQuery.error && <EmptyState message="결제 정보를 불러올 수 없습니다." />}

        {detail && (
          <section className="rounded-2xl border border-border bg-card px-5 py-5 shadow-sm">
            <div className="flex min-h-18 items-center gap-3">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-muted">
                <Store size={24} className="text-muted-foreground" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-[16px] font-semibold leading-none text-foreground">
                  {detail.payerName}
                </p>
              </div>
              <div className="shrink-0 text-right">
                <p className="text-xs text-muted-foreground">결제 금액</p>
                <p className="mt-1.5 text-[24px] font-bold leading-none text-foreground">
                  {formatWon(detail.amount)}
                </p>
              </div>
            </div>

            <div className="mt-4 border-t border-border/70 pt-2">
              {[
                { label: '승인번호', value: detail.approvalNumber },
                { label: '결제수단', value: '한강사랑상품권' },
                { label: '결제일시', value: formatDateTime(detail.createdAt) },
              ].map((row, index) => (
                <div
                  key={row.label}
                  className={[
                    'flex items-center justify-between gap-4 py-6',
                    index !== 0 ? 'border-t border-border/70' : '',
                  ].join(' ')}
                >
                  <span className="text-[13px] font-medium text-muted-foreground">{row.label}</span>
                  <span className="text-right text-[14px] font-semibold text-foreground">
                    {row.value}
                  </span>
                </div>
              ))}
            </div>
          </section>
        )}
      </main>

      {canCancel && (
        <footer className="shrink-0 bg-background pt-3 pb-[calc(env(safe-area-inset-bottom)+0.25rem)]">
          <Button variant="danger" size="lg" onClick={() => setConfirmOpen(true)}>
            결제 취소하기
          </Button>
        </footer>
      )}

      <ConfirmDialog
        open={confirmOpen}
        variant="danger"
        reverseButtons
        title="이 결제를 취소하시겠습니까?"
        confirmText="예"
        cancelText="아니요"
        onConfirm={() => {
          setConfirmOpen(false)
          setPin('')
          setStep('pin')
        }}
        onCancel={() => setConfirmOpen(false)}
      />

      <Toast open={!!toast} message={toast ?? ''} variant="error" />
    </div>
  )
}
