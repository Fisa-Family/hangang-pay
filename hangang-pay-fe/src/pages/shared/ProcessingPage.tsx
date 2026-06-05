import { useEffect, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import { executePayment, recoverPayment } from '@/api/payment'
import { executeCharge } from '@/api/charge'
import { createExchangeIntent, executeExchange } from '@/api/exchange'
import { registerUser, registerMerchant } from '@/api/auth'
import { ApiError } from '@/api/client'
import { ProcessingView } from '@/components/common'

type FlowState = Record<string, unknown>

interface FlowConfig {
  title: string
  caption?: (state: FlowState) => string | undefined
  completePath: string
  errorPath: string
  defaultError: string
  run: (state: FlowState) => Promise<unknown>
  buildErrorState?: (state: FlowState, message: string) => Record<string, unknown>
  onComplete?: (queryClient: QueryClient) => void
}

function invalidateUserTransactionQueries(queryClient: QueryClient) {
  void queryClient.invalidateQueries({ queryKey: ['wallet', 'balance'] })
  void queryClient.invalidateQueries({ queryKey: ['users', 'recent-histories'] })
  void queryClient.invalidateQueries({ queryKey: ['users', 'histories'] })
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
        return await executePayment(state.transactionUuid as string, state.pin as string)
      } catch (err) {
        try {
          await recoverPayment(state.transactionUuid as string)
        } catch {
          // recover는 best-effort, 실패해도 원래 에러를 그대로 throw
        }
        throw err
      }
    },
    onComplete: (queryClient) => {
      invalidateUserTransactionQueries(queryClient)
    },
  },
  '/charge/processing': {
    title: '충전을 처리하고 있어요',
    completePath: '/charge/complete',
    errorPath: '/charge/amount',
    defaultError: '충전 처리 중 오류가 발생했습니다.',
    run: (state) =>
      executeCharge({
        transactionUuid: state.transactionUuid as string,
        institutionId: state.institutionId as number,
        accountId: state.accountId as number,
        amount: state.amount as number,
        paymentPin: state.pin as string,
      }),
    onComplete: (queryClient) => {
      invalidateUserTransactionQueries(queryClient)
      void queryClient.invalidateQueries({ queryKey: ['charge', 'init'] })
    },
  },
  '/refund/processing': {
    title: '환불을 신청하고 있어요',
    completePath: '/refund/complete',
    errorPath: '/refund/check',
    defaultError: '환불 처리 중 오류가 발생했습니다.',
    async run(state) {
      // 1) intent 생성(PENDING 커밋, PIN 없음) → 2) 실행(PIN). bank 실패 시 intent가 남아 복구된다.
      await createExchangeIntent({
        transactionUuid: state.transactionUuid as string,
        amount: state.amount as number,
      })
      return executeExchange(state.transactionUuid as string, state.pin as string)
    },
    onComplete: (queryClient) => {
      invalidateUserTransactionQueries(queryClient)
    },
  },
  '/register/processing': {
    title: '회원가입을 처리하고 있어요',
    completePath: '/register/complete',
    errorPath: '/register/account',
    defaultError: '회원가입 처리 중 오류가 발생했습니다.',
    run: (state) =>
      registerUser({
        name: state.name as string,
        birthDate: `${(state.birthDate as string).slice(0, 4)}-${(state.birthDate as string).slice(4, 6)}-${(state.birthDate as string).slice(6, 8)}`,
        phoneNumber: state.phoneNumber as string,
        password: state.password as string,
        paymentPin: state.paymentPin as string,
        institutionId: state.institutionId as number,
        accountNumber: state.accountNumber as string,
        termsAgreed: state.termsAgreed as {
          serviceTerms: boolean
          privacyTerms: boolean
          electronicFinanceTerms: boolean
          localCurrencyTerms: boolean
        },
      }),
    buildErrorState: (state, message) => ({ ...state, error: message }),
  },
  '/merchant/register/processing': {
    title: '회원가입을 처리하고 있어요',
    completePath: '/merchant/register/complete',
    errorPath: '/merchant/register/account',
    defaultError: '회원가입 처리 중 오류가 발생했습니다.',
    run: (state) =>
      registerMerchant({
        businessNumber: state.businessNumber as string,
        username: state.phoneNumber as string,
        password: state.password as string,
        paymentPin: state.paymentPin as string,
        institutionId: state.institutionId as number,
        accountNumber: state.accountNumber as string,
        phoneNumber: state.phoneNumber as string,
        termsAgreed: state.termsAgreed as {
          serviceTerms: boolean
          privacyTerms: boolean
          electronicFinanceTerms: boolean
          localCurrencyTerms: boolean
        },
      }),
    buildErrorState: (state, message) => ({ ...state, error: message }),
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
        const errorState = flow.buildErrorState
          ? flow.buildErrorState(state, message)
          : { error: message, amount: state.amount }
        navigate(flow.errorPath, { state: errorState, replace: true })
      })
  }, [state, flow, navigate, queryClient])

  return (
    <div className="h-dvh bg-background">
      <ProcessingView
        title={flow?.title ?? '처리하고 있어요'}
        amount={state?.amount as number | undefined}
        caption={state ? flow?.caption?.(state) : undefined}
      />
    </div>
  )
}
