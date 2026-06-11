import { useQuery } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router-dom'
import { useCurrentUser } from '@/auth/useCurrentUser'
import { PageHeader } from '@/components/common'
import { fetchMerchantQr } from '@/api/merchant'
import { ApiError } from '@/api/client'
import { apiErrorMessages, isApiErrorCode } from '@/api/errorCodes'
import { resolveBackDestination } from '@/lib/navigation'

const API_SPEC = {
  MERCHANT_QR: { id: 'MERCHANT-QR' },
} as const

function buildErrorMessage(spec: (typeof API_SPEC)[keyof typeof API_SPEC], error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code && isApiErrorCode(error.code)) return apiErrorMessages[error.code]
    return error.message
  }
  return `${spec.id} 요청에 실패했습니다. 네트워크 연결을 확인해 주세요.`
}

const QR_STALE_TIME_MS = 1000 * 60 * 60

export function MerchantQrPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { currentUser } = useCurrentUser()
  const backPath = resolveBackDestination(location.pathname, location.state)

  const qrQuery = useQuery({
    queryKey: ['merchant', 'qr'],
    queryFn: fetchMerchantQr,
    retry: false,
    staleTime: QR_STALE_TIME_MS,
  })

  const errorMessage = qrQuery.error ? buildErrorMessage(API_SPEC.MERCHANT_QR, qrQuery.error) : null

  return (
    <div className="flex h-full flex-col">
      <PageHeader title="내 QR 코드" onBack={() => navigate(backPath, { replace: true })} />

      <div className="flex flex-1 flex-col items-center justify-center gap-6 px-6 pb-8">
        <p className="text-base font-semibold text-foreground">{currentUser?.name ?? '가맹점'}</p>

        <div className="flex aspect-square w-full max-w-[280px] items-center justify-center rounded-2xl border border-border/40 bg-card p-4 shadow-sm">
          {qrQuery.isLoading && (
            <div aria-hidden className="h-full w-full animate-pulse rounded-xl bg-muted/40" />
          )}
          {qrQuery.error && (
            <p role="alert" className="px-4 text-center text-sm font-medium text-destructive">
              {errorMessage}
            </p>
          )}
          {qrQuery.data && (
            <img
              src={qrQuery.data.qrImageBase64}
              alt="가맹점 결제 QR"
              className="h-full w-full object-contain"
            />
          )}
        </div>

        <p className="text-center text-sm text-muted-foreground">
          고객에게 QR 코드를 보여주세요.
          <br />
          스캔하면 결제가 진행됩니다.
        </p>

        {qrQuery.error && (
          <button
            type="button"
            onClick={() => qrQuery.refetch()}
            className="text-sm font-semibold text-primary"
          >
            다시 시도
          </button>
        )}
      </div>
    </div>
  )
}
