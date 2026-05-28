import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { PageHeader, PinEntry } from '@/components/common'

interface LocationState {
  transactionUuid: string
  amount: number
  merchantName: string
  balance: number
}

const PIN_LENGTH = 6

export function PinPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as LocationState | null

  const [pin, setPin] = useState('')

  useEffect(() => {
    if (pin.length === PIN_LENGTH && state) {
      navigate('/pay/processing', {
        state: {
          transactionUuid: state.transactionUuid,
          pin,
          amount: state.amount,
          merchantName: state.merchantName,
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
