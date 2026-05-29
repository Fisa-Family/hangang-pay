import { type ReactNode, useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import {
  Button,
  PageHeader,
  ProcessingState,
  ResultState,
  Toast,
  type ToastState,
} from '@/components/common'
import { ApiError } from '@/api/client'
import { getChargeDetail, type ChargeDetail } from '@/api/chargeHistories'
import { getPaymentHistoryDetail, type PaymentHistoryDetail } from '@/api/paymentHistories'
import { getExchangeHistoryDetail, type ExchangeHistoryDetail } from '@/api/exchangeHistories'
import { formatWon, formatDateTime, shortHash } from '@/lib/format'
import icon from '@/components/common/icons/icon.png'

interface DetailRow {
  label: string
  value: ReactNode
}

interface FlowConfig {
  title: string
  defaultError: string
  fetch: (id: string) => Promise<unknown>
  cardTitle: (detail: unknown) => string
  amountLabel: string
  amount: (detail: unknown) => number
  amountPrefix?: string
  rows: (detail: unknown, onCopy: (text: string) => void) => DetailRow[]
}

function TxHashButton({
  hash,
  onCopy,
}: {
  hash?: string
  onCopy: (text: string) => void
}) {
  if (!hash) return <span>{shortHash()}</span>
  return (
    <button
      type="button"
      className="max-w-[230px] truncate text-right text-[14px] font-medium text-foreground"
      onClick={() => onCopy(hash)}
    >
      {shortHash(hash)} ⧉
    </button>
  )
}

const FLOWS: Record<string, FlowConfig> = {
  charges: {
    title: '충전 상세',
    defaultError: '충전 상세 조회에 실패했습니다.',
    fetch: getChargeDetail,
    cardTitle: () => '한강사랑상품권',
    amountLabel: '충전 금액',
    amount: (d) => (d as ChargeDetail).amount,
    rows: (d, onCopy) => {
      const detail = d as ChargeDetail
      return [
        {
          label: '할인금액',
          value: <span className="text-success">-{formatWon(detail.discountAmount)}</span>,
        },
        { label: '결제금액', value: formatWon(detail.actualPaidAmount) },
        { label: '결제계좌', value: `${detail.bankName} ${detail.accountNumber}` },
        { label: '충전일시', value: formatDateTime(detail.createdAt) },
        { label: '트랜잭션 해시', value: <TxHashButton hash={detail.txHash} onCopy={onCopy} /> },
      ]
    },
  },
  payments: {
    title: '결제 상세',
    defaultError: '결제 상세 조회에 실패했습니다.',
    fetch: getPaymentHistoryDetail,
    cardTitle: (d) => (d as PaymentHistoryDetail).itemName,
    amountLabel: '결제 금액',
    amount: (d) => (d as PaymentHistoryDetail).amount,
    amountPrefix: '-',
    rows: (d, onCopy) => {
      const detail = d as PaymentHistoryDetail
      return [
        { label: '승인번호', value: detail.approvalNumber },
        { label: '결제수단', value: '한강사랑상품권' },
        { label: '결제일시', value: formatDateTime(detail.createdAt) },
        { label: '트랜잭션 해시', value: <TxHashButton hash={detail.txHash} onCopy={onCopy} /> },
      ]
    },
  },
  exchanges: {
    title: '환불 상세',
    defaultError: '환불 상세 조회에 실패했습니다.',
    fetch: getExchangeHistoryDetail,
    cardTitle: () => '한강사랑상품권',
    amountLabel: '환불금액',
    amount: (d) => (d as ExchangeHistoryDetail).amount,
    rows: (d, onCopy) => {
      const detail = d as ExchangeHistoryDetail
      return [
        { label: '환불계좌', value: `${detail.bankName} ${detail.accountNumber}` },
        { label: '환불일시', value: formatDateTime(detail.createdAt) },
        { label: '트랜잭션 해시', value: <TxHashButton hash={detail.txHash} onCopy={onCopy} /> },
      ]
    },
  },
}

export function UserHistoryDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const segment = location.pathname.split('/')[3] // 'charges' | 'payments' | 'exchanges'
  const flow = FLOWS[segment]

  const [detail, setDetail] = useState<unknown>(null)
  const [error, setError] = useState('')
  const [toast, setToast] = useState<ToastState | null>(null)

  useEffect(() => {
    if (!id || !flow) return
    flow
      .fetch(id)
      .then(setDetail)
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : flow.defaultError)
      })
  }, [id, flow])

  useEffect(() => {
    if (!toast) return
    const timeoutId = window.setTimeout(() => setToast(null), 2000)
    return () => window.clearTimeout(timeoutId)
  }, [toast])

  function handleCopy(text: string) {
    void navigator.clipboard
      .writeText(text)
      .then(() => setToast({ message: '트랜잭션 해시가 복사되었습니다.', variant: 'success' }))
      .catch(() => setToast({ message: '복사에 실패했습니다.', variant: 'error' }))
  }

  const title = flow?.title ?? '상세'

  if (!detail && !error) {
    return (
      <div className="flex h-full flex-col">
        <PageHeader title={title} onBack={() => navigate(-1)} />
        <ProcessingState status="loading" loadingText="상세 조회중" errorTitle="" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex h-full flex-col">
        <PageHeader title={title} onBack={() => navigate(-1)} />
        <ResultState
          variant="error"
          title="상세 조회 실패"
          description={error}
          primaryText="다시 시도"
          secondaryText="이전으로"
          onPrimary={() => window.location.reload()}
          onSecondary={() => navigate(-1)}
        />
      </div>
    )
  }

  if (!detail || !flow) return null

  const rows = flow.rows(detail, handleCopy)
  const amount = flow.amount(detail)
  const prefix = flow.amountPrefix ?? ''

  return (
    <div className="flex h-full flex-col bg-background">
      <PageHeader title={title} onBack={() => navigate(-1)} />

      <main className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto pb-4 pt-5">
        <section className="rounded-2xl border border-border bg-card px-5 py-5 shadow-sm">
          <div className="flex min-h-[72px] items-center gap-3">
            <div className="h-12 w-12 shrink-0 overflow-hidden rounded-xl">
              <img src={icon} alt="한강사랑상품권" className="h-full w-full object-cover" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-[16px] font-semibold leading-none text-foreground">
                {flow.cardTitle(detail)}
              </p>
            </div>
            <div className="shrink-0 text-right">
              <p className="text-xs text-muted-foreground">{flow.amountLabel}</p>
              <p className="mt-1.5 text-[24px] font-bold leading-none text-foreground">
                {prefix}{formatWon(amount)}
              </p>
            </div>
          </div>

          <div className="mt-4 border-t border-border/70 pt-2">
            {rows.map((row, index) => (
              <div
                key={row.label}
                className={[
                  'flex items-center justify-between gap-4 py-6',
                  index !== 0 ? 'border-t border-border/70' : '',
                ].join(' ')}
              >
                <span className="text-[13px] font-medium text-muted-foreground">{row.label}</span>
                <div className="text-right text-[14px] font-semibold text-foreground">
                  {row.value}
                </div>
              </div>
            ))}
          </div>
        </section>
      </main>

      <footer className="shrink-0 bg-background pt-3 pb-[calc(env(safe-area-inset-bottom)+0.25rem)]">
        <Button size="lg" onClick={() => navigate(-1)}>
          확인
        </Button>
      </footer>

      <Toast open={toast !== null} message={toast?.message ?? ''} variant={toast?.variant} />
    </div>
  )
}
