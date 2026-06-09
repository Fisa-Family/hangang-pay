import { http, HttpResponse } from 'msw'
import {
  mockMerchantDashboard,
  mockMerchantMyPage,
  mockMerchantQr,
  mockMerchantPayments,
  mockMerchantPaymentDetail,
  mockMerchantSettlements,
  mockMerchantRedeemInit,
  mockMerchantRedeemIntent,
  mockMerchantRedeemResult,
  mockPaymentCancelResult,
} from '../fixtures/merchant'

const ok = <T>(result: T) => HttpResponse.json({ isSuccess: true, result })

const BASE = '/api/v1'

export const merchantHandlers = [
  http.get(`${BASE}/merchant/dashboard`, () => ok(mockMerchantDashboard)),

  http.get(`${BASE}/merchant/mypage`, () => ok(mockMerchantMyPage)),

  http.get(`${BASE}/merchant/qr`, () => ok(mockMerchantQr)),

  http.get(`${BASE}/merchant/settlements`, () => ok(mockMerchantSettlements)),

  http.get(`${BASE}/merchant/payments`, () => ok(mockMerchantPayments)),

  http.get(`${BASE}/merchant/payments/:transactionId`, () => ok(mockMerchantPaymentDetail)),

  http.post(`${BASE}/merchant/payments/:transactionId/cancel`, () => ok(mockPaymentCancelResult)),

  // MERCHANT-004-R 결제 취소 복구: 상태 재조회 결과 반환
  http.post(`${BASE}/merchant/payments/:transactionId/cancel/recover`, () =>
    ok(mockPaymentCancelResult)
  ),

  http.get(`${BASE}/merchant/redeem`, () => ok(mockMerchantRedeemInit)),

  http.post(`${BASE}/merchant/redeem/intents`, () => ok(mockMerchantRedeemIntent)),

  http.post(`${BASE}/merchant/redeem/:transactionUuid/execute`, () => ok(mockMerchantRedeemResult)),
]
