import { apiFetch } from './client'

// PAY-001: QR 가맹점 정보 조회
export interface MerchantPaymentTarget {
  merchantPartyId: number
  merchantName: string
  address: string
}

export function fetchMerchantForPayment(merchantId: string): Promise<MerchantPaymentTarget> {
  return apiFetch<MerchantPaymentTarget>(`/merchant/${merchantId}`)
}

// PAY-002: 결제 의도 생성
export interface PaymentIntentResponse {
  transactionUuid: string
}

export function createPaymentIntent(body: {
  merchantPartyId: number
  amount: number
}): Promise<PaymentIntentResponse> {
  return apiFetch<PaymentIntentResponse>('/payment/intents', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

// PAY-003: 결제 실행
export interface PaymentResult {
  approvalNumber: string
  amount: number
  remainingBalance: number
  merchantName: string
  paidAt: string
}

export function executePayment(body: {
  transactionUuid: string
  pin: string
}): Promise<PaymentResult> {
  return apiFetch<PaymentResult>('/payment/execute', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

// PAY-004: 결제 상태 복구
export function recoverPayment(transactionUuid: string): Promise<void> {
  return apiFetch<void>(`/payment/${transactionUuid}/recover`, { method: 'POST' })
}
