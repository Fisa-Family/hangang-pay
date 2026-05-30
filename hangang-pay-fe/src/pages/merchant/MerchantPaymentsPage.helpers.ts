import type { MerchantPaymentHistoryItem, MerchantPaymentPage } from '@/api/merchant'
import type { HistoryListItem } from '@/api/user'

export type MerchantPaymentsCursor = { cursorCreatedAt: string; cursorId: number }

// BE 결제 내역 단건 → 기존 사용자 내역 표시 모델로 변환 (행 렌더 재사용)
export function toDisplayItem(raw: MerchantPaymentHistoryItem): HistoryListItem {
  return {
    id: String(raw.transactionId),
    displayType: raw.transactionType, // 가맹점 관점: PAYMENT(수취) → +, CANCEL(환불 지급) → −
    counterpartName: raw.payerName,
    amount: raw.amount,
    sign: raw.transactionType === 'CANCEL' ? '-' : '+',
    createdAt: raw.createdAt,
  }
}

// hasNext이고 두 커서가 모두 존재할 때만 다음 페이지 파라미터 반환
export function getNextPageParam(last: MerchantPaymentPage): MerchantPaymentsCursor | undefined {
  if (last.hasNext && last.nextCursorCreatedAt && last.nextCursorId != null) {
    return { cursorCreatedAt: last.nextCursorCreatedAt, cursorId: last.nextCursorId }
  }
  return undefined
}
