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

// CHARGE-002: 충전 의도 생성 응답 (BE: ChargeIntentResponse)
export interface ChargeIntentResult {
  transactionUuid: string
  status: string
  amount: number
  finalAmount: number
  accountNumber: string
  bankName: string
  expiresAt: string
}

// 충전 실행 결과 (BE: ChargeExecuteResponse)
export interface ChargeExecuteResult {
  partyId: number
  chargeId: number
  amount: number
  finalAmount: number
  chargedAt: string
}

// CHARGE-002: 충전 의도 생성 (PIN 없음). transactionUuid는 클라 생성 멱등키.
export function createChargeIntent(body: {
  transactionUuid: string
  institutionId: number
  accountId: number
  amount: number
}): Promise<ChargeIntentResult> {
  return apiFetch<ChargeIntentResult>('/charge/intents', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

// CHARGE-003: 충전 실행 (PIN). uuid는 path로 전달.
export function executeCharge(
  transactionUuid: string,
  paymentPin: string
): Promise<ChargeExecuteResult> {
  return apiFetch<ChargeExecuteResult>(`/charge/${transactionUuid}/execute`, {
    method: 'POST',
    body: JSON.stringify({ paymentPin }),
  })
}
