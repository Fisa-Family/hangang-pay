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
export function fetchMerchantSettlements(
  paramsOrSize:
    | number
    | {
        size?: number

        cursorCreatedAt?: string

        cursorId?: number
      } = 4
): Promise<MerchantSettlementPage> {
  const params = typeof paramsOrSize === 'number' ? { size: paramsOrSize } : paramsOrSize

  const q = new URLSearchParams({
    size: String(params.size ?? 20),
  })

  if (params.cursorCreatedAt) q.set('cursorCreatedAt', params.cursorCreatedAt)

  if (params.cursorId != null) q.set('cursorId', String(params.cursorId))

  return apiFetch<MerchantSettlementPage>(`/merchant/settlements?${q}`)
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

// ── 가맹점 결제 내역 / 상세 / 취소 (MERCHANT-002 / 003 / 004) ──

export type MerchantTransactionType = 'PAYMENT' | 'CANCEL'

// MERCHANT-002: 결제 내역 단건 (PAYMENT + CANCEL 혼합 스트림, SUCCESS만)
export interface MerchantPaymentHistoryItem {
  transactionId: number
  approvalNumber: string
  payerName: string // 마스킹됨 (예: 김*영)
  amount: number
  transactionType: MerchantTransactionType
  createdAt: string
}

export interface MerchantPaymentPage {
  content: MerchantPaymentHistoryItem[]
  nextCursorCreatedAt: string | null
  nextCursorId: number | null
  hasNext: boolean
}

// GET /api/v1/merchant/payments
export function fetchMerchantPayments(params: {
  size?: number
  cursorCreatedAt?: string
  cursorId?: number
}): Promise<MerchantPaymentPage> {
  const q = new URLSearchParams({ size: String(params.size ?? 20) })
  if (params.cursorCreatedAt) q.set('cursorCreatedAt', params.cursorCreatedAt)
  if (params.cursorId != null) q.set('cursorId', String(params.cursorId))
  return apiFetch<MerchantPaymentPage>(`/merchant/payments?${q}`)
}

// MERCHANT-003: 결제 상세
export interface MerchantPaymentDetail {
  transactionId: number
  transactionType: MerchantTransactionType
  amount: number
  payerName: string
  approvalNumber: string
  paymentStatus: string
  createdAt: string
  cancelAvailable: boolean // 취소 버튼 노출 기준
}

export interface MerchantPaymentDetailResponse {
  transactionType: MerchantTransactionType
  detail: MerchantPaymentDetail
}

// GET /api/v1/merchant/payments/{transactionId}
export function fetchMerchantPaymentDetail(
  transactionId: number
): Promise<MerchantPaymentDetailResponse> {
  return apiFetch<MerchantPaymentDetailResponse>(`/merchant/payments/${transactionId}`)
}

// MERCHANT-004: 결제 취소 (PIN 인증)
export interface PaymentCancelResult {
  transactionUuid: string
  status: string // 'SUCCESS' | 'UNKNOWN' — SUCCESS만 확정 취소
  approvalNumber: string
  txHash: string
  amount: number
  confirmedAt: string
}

// POST /api/v1/merchant/payments/{transactionId}/cancel
export function cancelMerchantPayment(
  transactionId: number,
  paymentPin: string
): Promise<PaymentCancelResult> {
  return apiFetch<PaymentCancelResult>(`/merchant/payments/${transactionId}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ paymentPin }),
  })
}

// ── 가맹점 출금/환전 (MERCHANT-006 / 007) ──

// MERCHANT-006: 출금 신청 조회 — 출금 가능 잔액 + 정산 계좌
export interface MerchantRedeemInit {
  availableAmount: number
  settlementAccount: {
    institutionName: string
    accountNumber: string
    accountType: string
  }
}

// MERCHANT-007: 출금 신청 결과 — status === 'SUCCESS'만 확정 출금
export interface MerchantRedeemResult {
  transactionId: number
  transactionUuid: string
  amount: number
  accountNumber: string
  bankName: string
  txHash: string
  status: string // 'SUCCESS' | 'PENDING' | 'PROCESSING' | 'UNKNOWN' | 'FAILED' | 'EXPIRED'
  exchangedAt: string
}

// GET /api/v1/merchant/redeem
export function fetchMerchantRedeemInit(): Promise<MerchantRedeemInit> {
  return apiFetch<MerchantRedeemInit>('/merchant/redeem')
}

// POST /api/v1/merchant/redeem — transactionUuid는 클라 생성 멱등키, amount는 전액
export function executeMerchantRedeem(input: {
  transactionUuid: string
  amount: number
  paymentPin: string
}): Promise<MerchantRedeemResult> {
  return apiFetch<MerchantRedeemResult>('/merchant/redeem', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}
