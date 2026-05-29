import { apiFetch } from './client'

// 충전 가능 계좌
export interface ChargeAccount {
  accountId: number
  institutionId: number
  institutionCode: string
  institutionName: string
  accountNumber: string
  isPrimary: boolean
}

// 충전 초기화 응답
export interface ChargeInitData {
  partyId: number
  transactionUuid: string
  balance: number
  monthlyLimit: number
  remainingLimit: number
  discountRate: number
  accounts: ChargeAccount[]
}

// 충전 초기 데이터 조회
export function fetchChargeInit(): Promise<ChargeInitData> {
  return apiFetch<ChargeInitData>('/charge/init')
}

// 충전 실행 요청 바디
export interface ChargeExecuteRequest {
  transactionUuid: string
  institutionId: number
  accountId: number
  amount: number
  paymentPin: string
}

// 충전 실행 결과
export interface ChargeExecuteResult {
  partyId: number
  chargeId: number
  amount: number
  finalAmount: number
  chargedAt: string
}

// 충전 실행 요청
export function executeCharge(body: ChargeExecuteRequest): Promise<ChargeExecuteResult> {
  return apiFetch<ChargeExecuteResult>('/charge', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}
