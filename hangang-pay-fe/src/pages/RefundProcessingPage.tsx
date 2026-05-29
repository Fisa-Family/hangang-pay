// TODO: ProcessingPage를 flow 타입 기반으로 제네릭화하면 이 파일 제거 가능
import { useEffect, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { executeExchange } from '@/api/exchange'
import { ApiError } from '@/api/client'
import { ProcessingView } from '@/components/common'

interface LocationState {
  transactionUuid: string
  amount: number
  accountId: number
  pin: string
}

export function RefundProcessingPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as LocationState | null
  const calledRef = useRef(false)

  useEffect(() => {
    if (!state || calledRef.current) return
    calledRef.current = true

    executeExchange({
      transactionUuid: state.transactionUuid,
      amount: state.amount,
      paymentPin: state.pin,
      accountId: state.accountId,
    })
      .then((result) => {
        navigate('/refund/complete', { state: result, replace: true })
      })
      .catch((err) => {
        const message = err instanceof ApiError ? err.message : '환불 처리 중 오류가 발생했습니다.'
        navigate('/refund/check', { state: { error: message }, replace: true })
      })
  }, [state, navigate])

  return (
    <div className="h-dvh bg-white">
      <ProcessingView title="환불을 신청하고 있어요" amount={state?.amount} />
    </div>
  )
}
