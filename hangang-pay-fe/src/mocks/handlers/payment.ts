import { http, HttpResponse } from 'msw'
import { mockMerchantInfo, mockPaymentIntent, mockPaymentResult } from '../fixtures/payment'

const ok = <T>(result: T) => HttpResponse.json({ isSuccess: true, result })
const noContent = () => HttpResponse.json({ isSuccess: true })

const BASE = '/api/v1'

export const paymentHandlers = [
  http.post(`${BASE}/payment/intents`, () => ok(mockPaymentIntent)),

  http.post(`${BASE}/payment/:transactionUuid/execute`, () => ok(mockPaymentResult)),

  http.post(`${BASE}/payment/:transactionUuid/recover`, () => noContent()),

  // 결제용 가맹점 조회 — merchant handlers의 /:merchantId보다 먼저 등록되어야 함
  http.get(`${BASE}/merchant/:merchantId`, () =>
    ok({
      ...mockMerchantInfo,
      merchantName: mockMerchantInfo.merchantName,
      address: mockMerchantInfo.address,
    })
  ),
]
