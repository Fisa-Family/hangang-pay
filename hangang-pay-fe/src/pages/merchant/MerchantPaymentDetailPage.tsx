import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '@/api/client'
import {
  cancelMerchantPayment,
  fetchMerchantPaymentDetail,
  recoverMerchantCancel,
} from '@/api/merchant'
import { Store } from 'lucide-react'
import {
  BackTitleHeader,
  Button,
  EmptyState,
  PageHeader,
  PinEntry,
  ProcessingView,
  RetryState,
  Toast,
} from '@/components/common'
import { formatDateTime, formatWon } from '@/lib/format'
import { resolveBackDestination } from '@/lib/navigation'

const PIN_LENGTH = 6

type Step = 'detail' | 'pin' | 'processing' | 'unknown'

export function MerchantPaymentDetailPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const backPath = resolveBackDestination(location.pathname, location.state)
  const { transactionId: rawId } = useParams<{ transactionId: string }>()
  const transactionId = Number(rawId)
  const isValidId = Number.isInteger(transactionId) && transactionId >= 1

  const [step, setStep] = useState<Step>('detail')
  const [pin, setPin] = useState('')
  const [toast, setToast] = useState<string | null>(null)

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
        navigate('/merchant/payments/cancel/complete', {
          replace: true,
          state: {
            amount: res.amount,
            approvalNumber: res.approvalNumber,
            confirmedAt: res.confirmedAt,
          },
        })
      } else {
        // 미확정(UNKNOWN/PROCESSING): 재시도(복구) 단계로 전환
        setPin('')
        setStep('unknown')
      }
    },
    onError: (err) => {
      setToast(err instanceof ApiError ? err.message : '결제를 취소하지 못했습니다.')
      setPin('')
      setStep('detail')
    },
  })

  // UNKNOWN 취소 건을 Bank 상태 조회로 수렴 — recover API 호출
  const recoverMutation = useMutation({
    mutationFn: () => recoverMerchantCancel(transactionId),
    onSuccess: (res) => {
      if (res.status === 'SUCCESS') {
        navigate('/merchant/payments/cancel/complete', {
          replace: true,
          state: {
            amount: res.amount,
            approvalNumber: res.approvalNumber,
            confirmedAt: res.confirmedAt,
          },
        })
      } else if (res.status === 'FAILED') {
        // 실패로 확정 → 토스트 후 상세 복귀
        setToast('취소가 실패로 확정되었습니다.')
        setStep('detail')
      }
      // 그 외(UNKNOWN/PROCESSING): unknown 단계 유지
    },
    onError: (err) => {
      setToast(err instanceof ApiError ? err.message : '취소 상태를 확인하지 못했습니다.')
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
  const amount = detail?.amount ?? 0

  // 취소 처리중 (recover 대기 포함)
  if (step === 'processing' || recoverMutation.isPending) {
    return (
      <ProcessingView
        title={recoverMutation.isPending ? '취소 상태를 확인하고 있어요' : '결제를 취소하고 있어요'}
        amount={amount}
      />
    )
  }

  // 취소 미확정(UNKNOWN) → 재시도(복구) 화면
  if (step === 'unknown') {
    return (
      <RetryState
        title="취소 상태를 확인 중이에요"
        description={
          '네트워크 지연으로 취소 결과가 아직 확정되지 않았어요.\n다시 확인해 취소 상태를 조회해 주세요.\n같은 취소가 중복 처리되지는 않습니다.'
        }
        primaryText="다시 확인"
        onPrimary={() => recoverMutation.mutate()}
        secondaryText="닫기"
        onSecondary={() => setStep('detail')}
      />
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
      <BackTitleHeader title="결제 상세" onBack={() => navigate(backPath, { replace: true })} />

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
          <Button
            variant="danger"
            size="lg"
            onClick={() => {
              setPin('')
              setStep('pin')
            }}
          >
            결제 취소하기
          </Button>
        </footer>
      )}

      <Toast open={!!toast} message={toast ?? ''} variant="error" />
    </div>
  )
}
