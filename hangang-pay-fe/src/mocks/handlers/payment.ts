import { http, HttpResponse } from 'msw'
import { mockMerchantInfo, mockPaymentIntent, mockPaymentResult } from '../fixtures/payment'

const ok = <T>(result: T) => HttpResponse.json({ isSuccess: true, result })

const BASE = '/api/v1'

export const paymentHandlers = [
  http.post(`${BASE}/payment/intents`, () => ok(mockPaymentIntent)),

  http.post(`${BASE}/payment/:transactionUuid/execute`, () => ok(mockPaymentResult)),

  // PAY-004 결제 복구: 상태 재조회 결과 반환
  http.post(`${BASE}/payment/:transactionUuid/recover`, () => ok(mockPaymentResult)),

  // 결제용 가맹점 조회 — merchant handlers의 /:merchantId보다 먼저 등록되어야 함
  http.get(`${BASE}/merchant/:merchantId`, () =>
    ok({
      ...mockMerchantInfo,
      merchantName: mockMerchantInfo.merchantName,
      address: mockMerchantInfo.address,
    })
  ),
]
