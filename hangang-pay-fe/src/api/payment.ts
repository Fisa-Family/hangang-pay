import { apiFetch } from './client'

// PAY-001: QR 가맹점 정보 조회 (BE: MerchantInfoResponse)
export interface MerchantPaymentTarget {
  partyId: number
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

// PAY-003: 결제 실행 (BE: PaymentExecutionResponse)
export interface PaymentResult {
  transactionUuid: string
  status: string
  approvalNumber: string
  txHash: string
  amount: number
  merchantName: string
  confirmedAt: string
}

export function executePayment(
  transactionUuid: string,
  paymentPin: string
): Promise<PaymentResult> {
  return apiFetch<PaymentResult>(`/payment/${transactionUuid}/execute`, {
    method: 'POST',
    body: JSON.stringify({ paymentPin }),
  })
}

// PAY-004: 결제 상태 복구
export function recoverPayment(transactionUuid: string): Promise<void> {
  return apiFetch<void>(`/payment/${transactionUuid}/recover`, { method: 'POST' })
}
