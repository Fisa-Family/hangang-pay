import { apiFetch } from '@/api/client'

interface ExchangeHistoryDetailResult {
  historyType: 'EXCHANGE'
  detail: ExchangeHistoryDetail
}

export interface ExchangeHistoryDetail {
  historyId: number
  amount: number

  transferStatus: string
  transferType: string

  accountNumber: string
  bankName: string

  walletAddress: string

  createdAt: string
  updatedAt: string

  txHash: string
  blockchainStatus: string
}

export async function getExchangeHistoryDetail(
  id: string | number
): Promise<ExchangeHistoryDetail> {
  const response = await apiFetch<ExchangeHistoryDetailResult>(
    `/users/histories/${id}?type=EXCHANGE`
  )

  return response.detail
}