import { useEffect, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { executePayment, recoverPayment } from '@/api/payment'
import { ApiError } from '@/api/client'
import { ProcessingView } from '@/components/common'

interface LocationState {
  transactionUuid: string
  pin: string
  amount: number
  merchantName: string
}

export function ProcessingPage() {
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
    <div className="h-dvh bg-white">
      <ProcessingView
        title="결제를 처리하고 있어요"
        amount={state?.amount}
        caption={state?.merchantName}
      />
    </div>
  )
}
