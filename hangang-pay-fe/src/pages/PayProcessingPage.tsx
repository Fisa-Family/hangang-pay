import { useEffect, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { executePayment, recoverPayment } from '@/api/payment'
import { ApiError } from '@/api/client'
import { formatWon } from '@/lib/format'

interface LocationState {
  transactionUuid: string
  pin: string
  amount: number
  merchantName: string
}

export function PayProcessingPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as LocationState | null
  const calledRef = useRef(false)

  useEffect(() => {
    if (!state || calledRef.current) return
    calledRef.current = true

    // DEV: 서버 없이 2초 후 완료 화면으로 이동
    if (import.meta.env.DEV) {
      const timer = setTimeout(() => {
        navigate('/pay/complete', {
          state: {
            approvalNumber: 'APV-2026-00000001',
            amount: state.amount,
            remainingBalance: 121500,
            merchantName: state.merchantName,
            paidAt: new Date().toISOString(),
          },
          replace: true,
        })
      }, 2000)
      return () => clearTimeout(timer)
    }

    executePayment({
      transactionUuid: state.transactionUuid,
      pin: state.pin,
    })
      .then((result) => {
        navigate('/pay/complete', { state: result, replace: true })
      })
      .catch(async (err) => {
        // PAY-004: 결제 실패 시 상태 복구
        try {
          await recoverPayment(state.transactionUuid)
        } catch {
          // 복구 실패는 무시하고 에러 화면 이동
        }
        const message =
          err instanceof ApiError ? err.message : '결제 처리 중 오류가 발생했습니다.'
        navigate('/pay/confirm', {
          state: { error: message },
          replace: true,
        })
      })
  }, [state, navigate])

  return (
    <div className="flex h-dvh flex-col items-center justify-center gap-6 bg-white">
      {/* 동글뱅이 스피너 */}
      <div
        className="h-18 w-18 animate-spin rounded-full border-4 border-gray-200 border-t-blue-500"
        style={{ animationDuration: '0.9s' }}
      />

      {/* 안내 문구 */}
      <div className="flex flex-col items-center gap-2 text-center">
        <p className="text-base font-medium text-muted-foreground">결제를 처리하고 있어요</p>
        {state && (
          <>
            <p className="text-[28px] font-bold tabular-nums text-foreground">
              {formatWon(state.amount)}
            </p>
            <p className="text-sm text-muted-foreground">{state.merchantName}</p>
          </>
        )}
      </div>

      {import.meta.env.DEV && (
        <button
          type="button"
          onClick={() =>
            navigate('/pay/complete', {
              state: {
                approvalNumber: 'APV-2026-00000001',
                amount: state?.amount ?? 6500,
                remainingBalance: 121500,
                merchantName: state?.merchantName ?? '[DEV] 카페 드롭탑 강남점',
                paidAt: new Date().toISOString(),
              },
              replace: true,
            })
          }
          className="rounded-full bg-blue-600 px-8 py-2 text-sm font-bold text-white"
        >
          [DEV] 완료 화면으로
        </button>
      )}
    </div>
  )
}
