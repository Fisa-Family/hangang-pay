import { http, HttpResponse } from 'msw'
import {
  mockMerchantMyPage,
  mockMerchantQr,
  mockMerchantPayments,
  mockMerchantPaymentDetail,
  mockMerchantSettlements,
  mockMerchantRedeemInit,
  mockMerchantRedeemResult,
  mockPaymentCancelResult,
} from '../fixtures/merchant'

const ok = <T>(result: T) => HttpResponse.json({ isSuccess: true, result })

const BASE = '/api/v1'

export const merchantHandlers = [
  http.get(`${BASE}/merchant/mypage`, () => ok(mockMerchantMyPage)),

  http.get(`${BASE}/merchant/qr`, () => ok(mockMerchantQr)),

  http.get(`${BASE}/merchant/settlements`, () => ok(mockMerchantSettlements)),

  http.get(`${BASE}/merchant/payments`, () => ok(mockMerchantPayments)),

  http.get(`${BASE}/merchant/payments/:transactionId`, () => ok(mockMerchantPaymentDetail)),

  http.post(`${BASE}/merchant/payments/:transactionId/cancel`, () => ok(mockPaymentCancelResult)),

  http.get(`${BASE}/merchant/redeem`, () => ok(mockMerchantRedeemInit)),

  http.post(`${BASE}/merchant/redeem`, () => ok(mockMerchantRedeemResult)),
]
