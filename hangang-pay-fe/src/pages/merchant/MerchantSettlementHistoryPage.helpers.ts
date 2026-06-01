import type { MerchantSettlementItem, MerchantSettlementPage } from '@/api/merchant'
import type { HistoryListItem } from '@/api/user'

export type MerchantSettlementsCursor = { cursorCreatedAt?: string; cursorId?: number }

export function toDisplaySettlementItem(raw: MerchantSettlementItem): HistoryListItem {
  return {
    id: String(raw.settlementId),
    displayType: 'SETTLEMENT',
    counterpartName: raw.settlementStatusText,
    amount: raw.amount,
    sign: '-',
    createdAt: raw.requestedAt,
  }
}

export function getNextSettlementPageParam(
  last?: MerchantSettlementPage
): MerchantSettlementsCursor | undefined {
  if (!last) return undefined

  if (last.hasNext && last.nextCursorCreatedAt && last.nextCursorId != null) {
    return {
      cursorCreatedAt: last.nextCursorCreatedAt,
      cursorId: last.nextCursorId,
    }
  }

  return undefined
}
