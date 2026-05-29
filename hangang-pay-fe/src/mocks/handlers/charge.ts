import { http, HttpResponse } from 'msw'
import { mockChargeInit, mockChargeResult } from '../fixtures/charge'

const ok = <T>(result: T) => HttpResponse.json({ isSuccess: true, result })

const BASE = '/api/v1'

export const chargeHandlers = [
  http.get(`${BASE}/charge/init`, () => ok(mockChargeInit)),

  http.post(`${BASE}/charge`, () => ok(mockChargeResult)),
]
