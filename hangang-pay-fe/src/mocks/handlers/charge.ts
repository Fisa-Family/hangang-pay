import { http, HttpResponse } from 'msw'
import { mockChargeInit } from '../fixtures/charge'

const ok = <T>(result: T) => HttpResponse.json({ isSuccess: true, result })

const BASE = '/api/v1'

export const chargeHandlers = [
  http.get(`${BASE}/charge/init`, () => ok(mockChargeInit)),

  http.post(`${BASE}/charge`, async ({ request }) => {
    const body = (await request.json()) as { amount: number; discountRate?: number }
    const discountRate = body.discountRate ?? mockChargeInit.discountRate / 100
    const amount = body.amount
    const discountAmount = Math.floor(amount * discountRate)
    return ok({
      partyId: 1,
      chargeId: Date.now(),
      amount,
      finalAmount: amount - discountAmount,
      chargedAt: new Date().toISOString(),
    })
  }),
]
