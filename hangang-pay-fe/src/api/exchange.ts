import { apiFetch } from './client'

export interface ExchangeInitResult {
  eligible: boolean
  walletBalance: number
}

export interface ExchangeExecuteBody {
  transactionUuid: string
  amount: number
  paymentPin: string
  // TODO: BE ExchangeExecuteRequest에 accountId 추가 후 활성화
  accountId?: number
}

export interface ExchangeExecuteResult {
  transactionId: number
  transactionUuid: string
  amount: number
  accountNumber: string
  bankName: string
  txHash: string
  status: string
  exchangedAt: string
}

export function fetchExchangeInit(): Promise<ExchangeInitResult> {
  return apiFetch<ExchangeInitResult>('/exchange/init')
}

export function executeExchange(body: ExchangeExecuteBody): Promise<ExchangeExecuteResult> {
  return apiFetch<ExchangeExecuteResult>('/exchange/execute', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}
