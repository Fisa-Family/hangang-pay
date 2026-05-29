import { apiFetch } from './client'

export interface ChargeAccount {
  accountId: number
  institutionId: number
  institutionCode: string
  institutionName: string
  accountNumber: string
  isPrimary: boolean
}

export interface ChargeInitData {
  partyId: number
  transactionUuid: string
  balance: number
  monthlyLimit: number
  remainingLimit: number
  discountRate: number
  accounts: ChargeAccount[]
}

export function fetchChargeInit(): Promise<ChargeInitData> {
  return apiFetch<ChargeInitData>('/charge/init')
}

export interface ChargeExecuteRequest {
  transactionUuid: string
  institutionId: number
  accountId: number
  amount: number
  paymentPin: string
}

export interface ChargeExecuteResult {
  partyId: number
  chargeId: number
  amount: number
  finalAmount: number
  chargedAt: string
}

export function executeCharge(body: ChargeExecuteRequest): Promise<ChargeExecuteResult> {
  return apiFetch<ChargeExecuteResult>('/charge', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}
