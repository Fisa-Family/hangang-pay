import { apiFetch } from '@/api/client'

interface ChargeDetailResult {
  historyType: 'CHARGE'
  detail: {
    historyId: number
    amount: number
    discountAmount: number
    discountRate: number
    actualPaidAmount: number
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
}

export interface ChargeDetail {
  historyId: number
  amount: number
  discountAmount: number
  actualPaidAmount: number
  accountNumber: string
  bankName: string
  createdAt: string
  txHash: string
}

export async function getChargeDetail(id: string | number): Promise<ChargeDetail> {
  const response = await apiFetch<ChargeDetailResult>(`/users/histories/${id}?type=CHARGE`)

  const detail = response.detail

  return {
    historyId: detail.historyId,
    amount: detail.amount,
    discountAmount: detail.discountAmount,
    actualPaidAmount: detail.actualPaidAmount,
    accountNumber: detail.accountNumber,
    bankName: detail.bankName,
    createdAt: detail.createdAt,
    txHash: detail.txHash,
  }
}
