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

// ────────────────────────────────────────────────────────────────────────────
// 정규화 레이어: historyType별 raw 응답을 단일 표시 모델로 변환
// ────────────────────────────────────────────────────────────────────────────

export type HistoryTab = 'ALL' | 'PAYMENT' | 'CHARGE' | 'EXCHANGE'
export type DisplayHistoryType = 'PAYMENT' | 'CANCEL' | 'CHARGE' | 'EXCHANGE'

export interface HistoryListItem {
  id: string
  historyId?: number
  displayType: DisplayHistoryType
  counterpartName: string
  amount: number
  sign: '+' | '-'
  createdAt: string
}

export interface HistoryPage {
  items: HistoryListItem[]
  nextCursor: { cursorCreatedAt: string; cursorId: number } | null
}

export interface FetchHistoriesParams {
  tab: HistoryTab
  size?: number
  cursorCreatedAt?: string
  cursorId?: number
}

interface RawPaymentItem {
  paymentId: string
  merchantName: string
  amount: number
  paidAt: string
  status: string
  historyType: 'PAYMENT' | 'CANCEL'
  cursorCreatedAt: string
  cursorId: number
}

interface RawChargeItem {
  id: number
  amount: number
  discountAmount: number
  discountRate: number
  status: string
  historyType: 'CHARGE'
  chargedAt: string
}

interface RawExchangeItem {
  id: number
  amount: number
  status: string
  historyType: 'EXCHANGE'
  exchangedAt: string
}

type RawHistoryItem = RawPaymentItem | RawChargeItem | RawExchangeItem

interface RawHistoryPage {
  historyType: string
  response: {
    content: RawHistoryItem[]
    nextCursorCreatedAt: string | null
    nextCursorId: number | null
    hasNext: boolean
  }
}

const HANGANG_SYSTEM_NAME = '한강사랑상품권'

export function normalizeHistoryItem(raw: RawHistoryItem): HistoryListItem {
  switch (raw.historyType) {
    case 'PAYMENT':
      return {
        id: raw.paymentId,
        displayType: 'PAYMENT',
        counterpartName: raw.merchantName,
        amount: raw.amount,
        sign: '-',
        createdAt: raw.paidAt,
      }
    case 'CANCEL':
      return {
        id: raw.paymentId,
        displayType: 'CANCEL',
        counterpartName: raw.merchantName,
        amount: raw.amount,
        sign: '+',
        createdAt: raw.paidAt,
      }
    case 'CHARGE':
      return {
        id: `CHARGE-${raw.id}`,
        historyId: raw.id,
        displayType: 'CHARGE',
        counterpartName: HANGANG_SYSTEM_NAME,
        amount: raw.amount,
        sign: '+',
        createdAt: raw.chargedAt,
      }
    case 'EXCHANGE':
      return {
        id: `EXCHANGE-${raw.id}`,
        historyId: raw.id,
        displayType: 'EXCHANGE',
        counterpartName: HANGANG_SYSTEM_NAME,
        amount: raw.amount,
        sign: '-',
        createdAt: raw.exchangedAt,
      }
  }
}

export async function fetchUserHistoriesNormalized(
  params: FetchHistoriesParams
): Promise<HistoryPage> {
  const query = new URLSearchParams({
    historyType: params.tab,
    size: String(params.size ?? 20),
    ...(params.cursorCreatedAt ? { cursorCreatedAt: params.cursorCreatedAt } : {}),
    ...(params.cursorId != null ? { cursorId: String(params.cursorId) } : {}),
  })

  const raw = await apiFetch<RawHistoryPage>(`/users/histories?${query}`)
  const items = raw.response.content.map(normalizeHistoryItem)

  const { hasNext, nextCursorCreatedAt, nextCursorId } = raw.response
  const nextCursor =
    hasNext && nextCursorCreatedAt && nextCursorId != null
      ? { cursorCreatedAt: nextCursorCreatedAt, cursorId: nextCursorId }
      : null

  return { items, nextCursor }
}
