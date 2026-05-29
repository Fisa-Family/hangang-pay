import { useEffect, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { executeCharge } from '@/api/charge'
import { ApiError } from '@/api/client'
import { ProcessingView } from '@/components/common'

// 처리 화면 진입 상태
interface LocationState {
  transactionUuid: string
  institutionId: number
  accountId: number
  amount: number
  discountRate: number
  pin: string
}

// 충전 처리 화면
export function ChargeProcessingPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as LocationState | null
  // 마운트 시 API 이중 호출 방지
  const calledRef = useRef(false)

  useEffect(() => {
    if (!state || calledRef.current) return
    calledRef.current = true

    /* TODO:
       현재 충전 처리 로딩 시간은 임시로 2초 고정 상태
       추후 실제 API 응답 완료 시점 기준으로 변경 예정
    */
    const minDelay = new Promise<void>((resolve) => setTimeout(resolve, 2000))

    const apiCall = executeCharge({
      transactionUuid: state.transactionUuid,
      institutionId: state.institutionId,
      accountId: state.accountId,
      amount: state.amount,
      paymentPin: state.pin,
    })

    // allSettled: API 성공, 실패와 무관하게 minDelay(2초)를 반드시 대기
    Promise.allSettled([minDelay, apiCall]).then(([, apiResult]) => {
      if (apiResult.status === 'fulfilled') {
        navigate('/charge/complete', { state: apiResult.value, replace: true })
      } else {
        const err = apiResult.reason
        const message =
          err instanceof ApiError ? err.message : '충전 처리 중 오류가 발생했습니다.'
        navigate('/charge/amount', { state: { error: message }, replace: true })
      }
    })
  }, [state, navigate])

  return (
    <div className="h-dvh bg-white">
      <ProcessingView title="충전을 처리하고 있어요" amount={state?.amount} />
    </div>
  )
}
