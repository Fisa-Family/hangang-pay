import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Button,
  PageHeader,
  ProcessingState,
  ResultState,
  Toast,
  type ToastState,
} from '@/components/common'
import { ApiError } from '@/api/client'
import { getChargeDetail, type ChargeDetail } from '@/api/charges'
import icon from '@/components/common/icons/icon.png'

const formatWon = (value: number) => `${value.toLocaleString('ko-KR')}원`

const formatDateTime = (value: string) =>
  new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
    .format(new Date(value))
    .replace(/\. /g, '-')
    .replace('.', '')

const shortHash = (hash?: string) => (hash ? `${hash.slice(0, 14)}...${hash.slice(-4)}` : '-')

export function ChargeDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [detail, setDetail] = useState<ChargeDetail | null>(null)
  const [error, setError] = useState('')
  const [toast, setToast] = useState<ToastState | null>(null)

  useEffect(() => {
    if (!id) return

    getChargeDetail(id)
      .then(setDetail)
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : '충전 상세 조회에 실패했습니다.')
      })
  }, [id])

  useEffect(() => {
    if (!toast) return

    const timeoutId = window.setTimeout(() => {
      setToast(null)
    }, 2000)

    return () => window.clearTimeout(timeoutId)
  }, [toast])

  if (!detail && !error) {
    return (
      <div className="flex h-full flex-col">
        <PageHeader title="충전 상세" onBack={() => navigate(-1)} />
        <ProcessingState status="loading" loadingText="상세 조회중" errorTitle="" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex h-full flex-col">
        <PageHeader title="충전 상세" onBack={() => navigate(-1)} />
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
  if (!detail) return null

  const charge = detail

  return (
    <div className="flex h-full flex-col bg-background">
      <PageHeader title="충전 상세" onBack={() => navigate(-1)} />

      <main className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto pb-4 pt-5">
        <section className="rounded-2xl border border-border bg-card px-5 py-5 shadow-sm">
          <div className="flex min-h-[72px] items-center gap-3">
            <div className="h-12 w-12 shrink-0 overflow-hidden rounded-xl">
              <img src={icon} alt="한강사랑상품권" className="h-full w-full object-cover" />
            </div>

            <div className="min-w-0 flex-1">
              <p className="text-[16px] font-semibold leading-none text-foreground">
                한강사랑상품권
              </p>
            </div>

            <div className="shrink-0 text-right">
              <p className="text-xs text-muted-foreground">충전 금액</p>
              <p className="mt-1.5 text-[24px] font-bold leading-none text-foreground">
                {formatWon(charge.amount)}
              </p>
            </div>
          </div>
          <div className="mt-4 border-t border-border/70 pt-2">
            {[
              {
                label: '할인 금액',
                value: (
                  <span className="text-destructive">{`-${formatWon(charge.discountAmount)}`}</span>
                ),
              },
              {
                label: '결제 금액',
                value: formatWon(charge.actualPaidAmount),
              },
              {
                label: '결제 계좌',
                value: `${charge.bankName} ${charge.accountNumber}`,
              },
              {
                label: '충전 일시',
                value: formatDateTime(charge.createdAt),
              },
              {
                label: '트랜잭션 해시',
                value: (
                  <button
                    type="button"
                    className="max-w-[230px] truncate text-right text-[14px] font-medium text-foreground"
                    onClick={() => {
                      if (!charge.txHash) return

                      void navigator.clipboard
                        .writeText(charge.txHash)
                        .then(() => {
                          setToast({
                            message: '트랜잭션 해시가 복사되었습니다.',
                            variant: 'success',
                          })
                        })
                        .catch(() => {
                          setToast({
                            message: '복사에 실패했습니다.',
                            variant: 'error',
                          })
                        })
                    }}
                  >
                    {shortHash(charge.txHash)} ⧉
                  </button>
                ),
              },
            ].map((row, index) => (
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
