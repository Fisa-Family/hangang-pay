import { http, HttpResponse } from 'msw'
import { mockExchangeInit, mockExchangeIntent, mockExchangeResult } from '../fixtures/exchange'

const ok = <T>(result: T) => HttpResponse.json({ isSuccess: true, result })

const BASE = '/api/v1'

export const exchangeHandlers = [
  http.get(`${BASE}/exchange/init`, () => ok(mockExchangeInit)),

  http.post(`${BASE}/exchange/intents`, () => ok(mockExchangeIntent)),

  http.post(`${BASE}/exchange/:transactionUuid/execute`, () => ok(mockExchangeResult)),
]
