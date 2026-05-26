import { apiFetch } from './client'

// 정산 내역 단건 (BE: MerchantSettlementHistoryItem)
export interface MerchantSettlementItem {
  settlementId: number
  amount: number
  settlementStatus: string
  settlementStatusText: string
  requestedAt: string
  completedAt: string | null
}

// 정산 내역 목록 응답 (BE: CursorPageResponse<MerchantSettlementHistoryItem>)
export interface MerchantSettlementPage {
  content: MerchantSettlementItem[]
  nextCursorCreatedAt: string | null
  nextCursorId: number | null
  hasNext: boolean
}

// 정산 내역 조회 → GET /api/v1/merchant/settlements
export function fetchMerchantSettlements(size = 4): Promise<MerchantSettlementPage> {
  return apiFetch<MerchantSettlementPage>(`/merchant/settlements?size=${size}`)
}
