import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '@/api/client'
import {
  cancelMerchantPayment,
  fetchMerchantPaymentDetail,
  recoverMerchantCancel,
  type PaymentCancelResult,
} from '@/api/merchant'
import {
  BackTitleHeader,
  Button,
  ConfirmDialog,
  EmptyState,
  PageHeader,
  PinEntry,
  ProcessingView,
  ResultState,
  SummaryCard,
  Toast,
} from '@/components/common'
import { formatDateTime, formatWon } from '@/lib/format'

const PIN_LENGTH = 6

type Step = 'detail' | 'pin' | 'processing' | 'unknown' | 'result'

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
        setResult(res)
        setStep('result')
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
  const amount = result?.amount ?? detail?.amount ?? 0

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
      <div className="flex h-full items-center justify-center px-5">
        <ResultState
          variant="info"
          title="취소 상태를 확인 중이에요"
          description={
            '네트워크 지연으로 취소 결과가 아직 확정되지 않았어요.\n다시 확인해 취소 상태를 조회해 주세요.\n같은 취소가 중복 처리되지는 않습니다.'
          }
          primaryText="다시 확인"
          onPrimary={() => recoverMutation.mutate()}
          secondaryText="닫기"
          onSecondary={() => setStep('detail')}
        />
      </div>
    )
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
    <div className="flex h-full flex-col">
      <BackTitleHeader title="결제 상세" onBack={() => navigate(-1)} />

      <div className="flex-1 space-y-4 overflow-y-auto">
        {detailQuery.isLoading && (
          <div aria-hidden className="h-40 animate-pulse rounded-2xl bg-muted/40" />
        )}
        {detailQuery.error && <EmptyState message="결제 정보를 불러올 수 없습니다." />}

        {detail && (
          <>
            <section className="rounded-2xl border border-border/40 bg-card p-5 shadow-sm">
              <p className="text-center text-sm text-muted-foreground">결제 금액</p>
              <p className="mt-1 text-center text-3xl font-bold tabular-nums text-foreground">
                ₩ {new Intl.NumberFormat('ko-KR').format(detail.amount)}
              </p>
            </section>

            <SummaryCard
              divided
              rows={[
                { label: '고객', value: detail.payerName },
                { label: '승인번호', value: detail.approvalNumber },
                { label: '결제 일시', value: formatDateTime(detail.createdAt) },
              ]}
            />
          </>
        )}
      </div>

      {canCancel && (
        <div className="pt-3">
          <Button variant="danger" size="lg" onClick={() => setConfirmOpen(true)}>
            결제 취소하기
          </Button>
        </div>
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
