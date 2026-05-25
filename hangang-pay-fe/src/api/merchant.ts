import { apiFetch } from './client'

// 오늘 매출 요약 응답
export interface MerchantDashboardResult {
  todaySales: number
  todayPaymentCount: number
}

// 결제 내역 단건
export type MerchantPaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | (string & {})

export interface MerchantPaymentItem {
  paymentId: string
  itemName: string
  amount: number
  paidAt: string
  status: MerchantPaymentStatus
}

// 결제 내역 목록 응답 (커서 페이지네이션)
export interface MerchantPaymentPage {
  content: MerchantPaymentItem[]
  nextCursor: string | null
  hasNext: boolean
}

// 오늘 매출 요약 조회
export function fetchMerchantDashboard(): Promise<MerchantDashboardResult> {
  return apiFetch<MerchantDashboardResult>('/merchant/dashboard')
}

// 결제 내역 조회
export function fetchMerchantPayments(size = 4): Promise<MerchantPaymentPage> {
  return apiFetch<MerchantPaymentPage>(`/merchant/payments?size=${size}`)
}
