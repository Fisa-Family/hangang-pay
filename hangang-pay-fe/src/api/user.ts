import { apiFetch } from './client'

// 거래 유형 (BE UserHistoryType enum 동일)
export type HistoryType = 'PAYMENT' | 'CHARGE' | 'EXCHANGE' | (string & {})

// 거래 상태
export type HistoryStatus = 'COMPLETED' | 'PENDING' | 'FAILED' | 'CANCELLED' | (string & {})

// 거래 내역 단건
export interface UserHistoryItem {
  id: number
  type: HistoryType
  counterpartName: string
  amount: number
  status: HistoryStatus
  createdAt: string
}

// 커서 페이지네이션 응답 (MY-002)
export interface UserHistoryPage<T> {
  historyType: HistoryType
  page: {
    content: T[]
    nextCursor: string | null
    hasNext: boolean
  }
}

// 내역 조회 파라미터
export interface UserHistoryParams {
  historyType: HistoryType
  size?: number
  cursor?: string
}

// MY-002 내역 조회
export function fetchUserHistories(
  params: UserHistoryParams
): Promise<UserHistoryPage<UserHistoryItem>> {
  const query = new URLSearchParams({
    historyType: params.historyType,
    size: String(params.size ?? 20),
    ...(params.cursor ? { cursor: params.cursor } : {}),
  })
  return apiFetch<UserHistoryPage<UserHistoryItem>>(`/users/histories?${query}`)
}
