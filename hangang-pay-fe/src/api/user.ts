import { apiFetch } from './client'

// 사용자 프로필 응답
export interface UserProfileResponse {
  userId: number
  partyId: number
  username: string
  phoneNumber: string
  birthDate: string | null
  region: string | null
}

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

// 커서 페이지네이션 응답
export interface UserHistoryPage<T> {
  historyType: HistoryType
  response: {
    content: T[]
    nextCursorCreatedAt: string | null
    nextCursorId: number | null
    hasNext: boolean
  }
}

// 내역 조회 파라미터
export interface UserHistoryParams {
  historyType: HistoryType
  size?: number
  cursorCreatedAt?: string
  cursorId?: number
}

// 내역 조회
export function fetchUserHistories(
  params: UserHistoryParams
): Promise<UserHistoryPage<UserHistoryItem>> {
  const query = new URLSearchParams({
    historyType: params.historyType,
    size: String(params.size ?? 20),
    ...(params.cursorCreatedAt ? { cursorCreatedAt: params.cursorCreatedAt } : {}),
    ...(params.cursorId != null ? { cursorId: String(params.cursorId) } : {}),
  })
  return apiFetch<UserHistoryPage<UserHistoryItem>>(`/users/histories?${query}`)
}

// 사용자 프로필 조회
export function fetchUserProfile(): Promise<UserProfileResponse> {
  return apiFetch<UserProfileResponse>('/users/profile')
}
