import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { PageHeader, PinEntry, Toast, type ToastState } from '@/components/common'
import { resolveBackDestination } from '@/lib/navigation'

interface LocationState {
  nextRoute: string
  cancelRoute?: string
  error?: string
  [key: string]: unknown
}

const PIN_LENGTH = 6

export function PinPage() {
  const location = useLocation()

  return <PinPageContent key={location.key} />
}

function PinPageContent() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as LocationState | null
  const backPath = resolveBackDestination(location.pathname, location.state)

  const [pin, setPin] = useState('')
  const [toast, setToast] = useState<ToastState | null>(
    state?.error ? { message: state.error, variant: 'error' } : null
  )

  useEffect(() => {
    if (!toast) return
    const id = window.setTimeout(() => setToast(null), 3000)
    return () => window.clearTimeout(id)
  }, [toast])

  function handlePinChange(value: string) {
    const next = value.slice(0, PIN_LENGTH)
    setPin(next)

    if (next.length !== PIN_LENGTH || !state) return

    const { nextRoute, ...rest } = state
    delete rest.error
    navigate(nextRoute, {
      state: {
        ...rest,
        pin: next,
        submitToken: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
      },
    })
  }

  function handleCancel() {
    navigate(backPath, { replace: true })
  }

  return (
    <div className="relative flex h-dvh flex-col bg-background">
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
      <PinEntry pin={pin} length={PIN_LENGTH} onChange={handlePinChange} onForgot={() => {}} />
      <Toast open={toast !== null} message={toast?.message ?? ''} variant={toast?.variant} />
    </div>
  )
}
