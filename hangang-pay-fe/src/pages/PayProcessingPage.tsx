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
        const message = err instanceof ApiError ? err.message : '결제 처리 중 오류가 발생했습니다.'
        navigate('/pay/confirm', {
          state: { error: message },
          replace: true,
        })
      })
  }, [state, navigate])

  return (
    <div className="flex h-dvh flex-col items-center justify-center gap-6 bg-white">
      {/* 스피너 */}
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
    </div>
  )
}
