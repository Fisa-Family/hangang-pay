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

// 가맹점 본인 QR 응답 (BE: MerchantQrResponse)
export interface MerchantQrResponse {
  /** data URL 형태 ("data:image/png;base64,...") — img src에 그대로 사용 가능 */
  qrImageBase64: string
}

// 가맹점 QR 조회 → GET /api/v1/merchant/qr
export function fetchMerchantQr(): Promise<MerchantQrResponse> {
  return apiFetch<MerchantQrResponse>('/merchant/qr')
}

// PAY-001: 가맹점 정보 조회
export interface MerchantInfoResponse {
  merchantId: number
  partyId: number
  merchantName: string
  address: string
  walletAddress: string
}

// GET /api/v1/merchant/{merchantId}
export function fetchMerchantInfo(merchantId: number): Promise<MerchantInfoResponse> {
  return apiFetch<MerchantInfoResponse>(`/merchant/${merchantId}`)
}

// MERCHANT-009: 가맹점 마이페이지 조회 — 정산 계좌 정보
export interface MerchantSettlementAccountItem {
  accountId: number
  institutionName: string
  accountNumber: string
  accountType: string
}

// MERCHANT-009: 가맹점 마이페이지 조회 — 응답 (BE: MerchantMyPageResponse)
export interface MerchantMyPageResponse {
  merchantId: number
  partyId: number
  merchantName: string
  businessNumber: string
  ownerName: string
  phoneNumber: string
  address: string
  settlementAccount: MerchantSettlementAccountItem
}

// MERCHANT-009: 가맹점 마이페이지 조회 → GET /api/v1/merchant/mypage
// ⚠️ docs/rest_api.md는 /api/v2 명시, 실제 BE 구현은 /api/v1 (불일치)
export function fetchMerchantMyPage(): Promise<MerchantMyPageResponse> {
  return apiFetch<MerchantMyPageResponse>('/merchant/mypage')
}
