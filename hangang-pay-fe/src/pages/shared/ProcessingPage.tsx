import { useEffect, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import { executePayment, recoverPayment } from '@/api/payment'
import { executeCharge } from '@/api/charge'
import { executeExchange } from '@/api/exchange'
import { ApiError } from '@/api/client'
import { ProcessingView } from '@/components/common'

type FlowState = Record<string, unknown> & {
  transactionUuid: string
  pin: string
  amount: number
}

interface FlowConfig {
  title: string
  caption?: (state: FlowState) => string | undefined
  completePath: string
  errorPath: string
  defaultError: string
  run: (state: FlowState) => Promise<unknown>
  onComplete?: (queryClient: QueryClient) => void
}

const FLOWS: Record<string, FlowConfig> = {
  '/pay/processing': {
    title: '결제를 처리하고 있어요',
    caption: (s) => s.merchantName as string | undefined,
    completePath: '/pay/complete',
    errorPath: '/pay/confirm',
    defaultError: '결제 처리 중 오류가 발생했습니다.',
    async run(state) {
      try {
        return await executePayment(state.transactionUuid, state.pin)
      } catch (err) {
        try {
          await recoverPayment(state.transactionUuid)
        } catch {
          // recover는 best-effort, 실패해도 원래 에러를 그대로 throw
        }
        throw err
      }
    },
    onComplete: (queryClient) => {
      void queryClient.invalidateQueries({ queryKey: ['charge', 'init'] })
      void queryClient.invalidateQueries({ queryKey: ['users', 'recent-histories'] })
    },
  },
  '/charge/processing': {
    title: '충전을 처리하고 있어요',
    completePath: '/charge/complete',
    errorPath: '/charge/amount',
    defaultError: '충전 처리 중 오류가 발생했습니다.',
    run: (state) =>
      executeCharge({
        transactionUuid: state.transactionUuid,
        institutionId: state.institutionId as number,
        accountId: state.accountId as number,
        amount: state.amount,
        paymentPin: state.pin,
      }),
  },
  '/refund/processing': {
    title: '환불을 신청하고 있어요',
    completePath: '/refund/complete',
    errorPath: '/refund/check',
    defaultError: '환불 처리 중 오류가 발생했습니다.',
    run: (state) =>
      executeExchange({
        transactionUuid: state.transactionUuid,
        amount: state.amount,
        paymentPin: state.pin,
        accountId: state.accountId as number,
      }),
  },
}

export function ProcessingPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const state = location.state as FlowState | null
  const calledRef = useRef(false)
  const flow = FLOWS[location.pathname]

  useEffect(() => {
    if (!state || !flow || calledRef.current) return
    calledRef.current = true

    flow
      .run(state)
      .then((result) => {
        flow.onComplete?.(queryClient)
        navigate(flow.completePath, { state: result, replace: true })
      })
      .catch((err) => {
        const message = err instanceof ApiError ? err.message : flow.defaultError
        navigate(flow.errorPath, { state: { error: message }, replace: true })
      })
  }, [state, flow, navigate, queryClient])

  return (
    <div className="h-dvh bg-white">
      <ProcessingView
        title={flow?.title ?? '처리하고 있어요'}
        amount={state?.amount}
        caption={state ? flow?.caption?.(state) : undefined}
      />
    </div>
  )
}
