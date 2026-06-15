import { apiFetch } from './client'

export interface ExchangeInitResult {
  eligible: boolean
  walletBalance: number
}

// EXCHANGE-002: 환전 의도 생성 응답 (BE: ExchangeIntentResponse)
export interface ExchangeIntentResult {
  transactionUuid: string
  status: string
  amount: number
  accountNumber: string
  bankName: string
  expiresAt: string
}

// EXCHANGE-003: 환전 실행 응답 (BE: ExchangeExecuteResponse)
export interface ExchangeExecuteResult {
  transactionId: number
  transactionUuid: string
  amount: number
  accountNumber: string
  bankName: string
  txHash: string
  approvalNumber: string
  status: string
  exchangedAt: string
}

export function fetchExchangeInit(): Promise<ExchangeInitResult> {
  return apiFetch<ExchangeInitResult>('/exchange/init')
}

// EXCHANGE-002: 환전 의도 생성 (PIN 없음). transactionUuid는 서버가 발급해 응답으로 반환한다.
export function createExchangeIntent(body: {
  amount: number
}): Promise<ExchangeIntentResult> {
  return apiFetch<ExchangeIntentResult>('/exchange/intents', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

// EXCHANGE-003: 환전 실행 (PIN). uuid는 path로 전달.
export function executeExchange(
  transactionUuid: string,
  paymentPin: string
): Promise<ExchangeExecuteResult> {
  return apiFetch<ExchangeExecuteResult>(`/exchange/${transactionUuid}/execute`, {
    method: 'POST',
    body: JSON.stringify({ paymentPin }),
  })
}
