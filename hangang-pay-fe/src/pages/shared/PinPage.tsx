import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { PageHeader, PinEntry } from '@/components/common'

interface LocationState {
  nextRoute: string
  cancelRoute?: string
  [key: string]: unknown
}

const PIN_LENGTH = 6

export function PinPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as LocationState | null

  const [pin, setPin] = useState('')

  useEffect(() => {
    if (pin.length !== PIN_LENGTH || !state) return
    const { nextRoute, ...rest } = state

    delete rest.cancelRoute
    navigate(nextRoute, { state: { ...rest, pin } })
  }, [pin, navigate, state])

  function handleCancel() {
    if (state?.cancelRoute) {
      navigate(state.cancelRoute, { replace: true })
    } else {
      navigate(-1)
    }
  }

  return (
    <div className="flex h-dvh flex-col bg-background">
      <PageHeader
        title="PIN번호 입력"
        className="px-5"
        rightAction={
          <button
            type="button"
            onClick={handleCancel}
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
