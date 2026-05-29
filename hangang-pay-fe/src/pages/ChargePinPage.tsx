import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { PageHeader, PinEntry } from '@/components/common'

// PIN 화면 진입 상태
interface LocationState {
  transactionUuid: string
  institutionId: number
  accountId: number
  amount: number
  discountRate: number
}

const PIN_LENGTH = 6

// 충전 PIN 입력 화면
export function ChargePinPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as LocationState | null

  const [pin, setPin] = useState('')

  // PIN 6자리 완성 시 처리 화면 이동
  useEffect(() => {
    if (pin.length === PIN_LENGTH && state) {
      navigate('/charge/processing', {
        state: {
          transactionUuid: state.transactionUuid,
          institutionId: state.institutionId,
          accountId: state.accountId,
          amount: state.amount,
          discountRate: state.discountRate,
          pin,
        },
      })
    }
  }, [pin, navigate, state])

  return (
    <div className="flex h-dvh flex-col bg-white">
      <div className="px-5 pt-14">
        <PageHeader
          title="PIN번호 입력"
          rightAction={
            <button
              type="button"
              onClick={() => navigate('/charge/amount', { replace: true })}
              className="text-sm font-medium text-[#8B95A1]"
            >
              취소
            </button>
          }
        />
      </div>
      <PinEntry pin={pin} length={PIN_LENGTH} onChange={setPin} onForgot={() => {}} />
    </div>
  )
}
