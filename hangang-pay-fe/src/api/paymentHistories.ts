import { apiFetch } from '@/api/client'

interface PaymentHistoryDetailResult {
  historyType: 'PAYMENT'
  detail: {
    historyId: number
    itemName: string
    amount: number
    approvalNumber: string
    paymentStatus: string
    createdAt: string
    txHash: string
  }
}

export interface PaymentHistoryDetail {
  historyId: number
  itemName: string
  amount: number
  approvalNumber: string
  paymentStatus: string
  createdAt: string
  txHash: string
}

export async function getPaymentHistoryDetail(id: string | number): Promise<PaymentHistoryDetail> {
  const response = await apiFetch<PaymentHistoryDetailResult>(`/users/histories/${id}?type=PAYMENT`)

  return response.detail
}
