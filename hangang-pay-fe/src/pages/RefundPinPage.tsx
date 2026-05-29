// TODO: PinPage를 nextRoute 기반으로 제네릭화하면 이 파일 제거 가능
import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { PageHeader, PinEntry } from '@/components/common'

interface LocationState {
  amount: number
  accountId: number
}

const PIN_LENGTH = 6

export function RefundPinPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as LocationState | null

  const [pin, setPin] = useState('')

  useEffect(() => {
    if (pin.length === PIN_LENGTH && state) {
      navigate('/refund/processing', {
        state: {
          transactionUuid: crypto.randomUUID(),
          amount: state.amount,
          accountId: state.accountId,
          pin,
        },
      })
    }
  }, [pin, navigate, state])

  return (
    <div className="flex h-dvh flex-col bg-white">
      <PageHeader
        title="PIN번호 입력"
        className="px-5 pt-14"
        rightAction={
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="text-sm font-medium text-muted-foreground"
          >
            취소
          </button>
        }
      />

      <PinEntry pin={pin} length={PIN_LENGTH} onChange={setPin} onForgot={() => {}} />
    </div>
  )
}
