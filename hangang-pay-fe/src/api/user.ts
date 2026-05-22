import { apiFetch } from './client'

export type HistoryType = 'PAYMENT' | 'CHARGE' | 'EXCHANGE' | (string & {})
export type HistoryStatus = 'COMPLETED' | 'PENDING' | 'FAILED' | 'CANCELLED' | (string & {})

export interface UserHistoryItem {
  id: number
  type: HistoryType
  counterpartName: string
  amount: number
  status: HistoryStatus
  createdAt: string
}

// BE 응답: 커서 페이지네이션 래퍼
export interface UserHistoryPage<T> {
  historyType: HistoryType
  page: {
    content: T[]
    nextCursor: string | null
    hasNext: boolean
  }
}

export interface UserHistoryParams {
  historyType: HistoryType
  size?: number
  cursor?: string
}

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
