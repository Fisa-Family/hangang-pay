import { http, HttpResponse } from 'msw'

const ok = <T>(result: T) => HttpResponse.json({ isSuccess: true, result })
const noContent = () => HttpResponse.json({ isSuccess: true })

const BASE = '/api/v1'

export const authHandlers = [
  http.post(`${BASE}/auth/users/login`, () => ok({ principalId: 1, partyId: 1, role: 'USER' })),

  http.post(`${BASE}/auth/merchants/login`, () =>
    ok({ principalId: 2, partyId: 2, role: 'MERCHANT' })
  ),

  http.post(`${BASE}/auth/logout`, () => noContent()),

  http.post(`${BASE}/auth/account/send`, () => ok({ code: '123456' })),

  http.post(`${BASE}/auth/account/verify`, () => noContent()),
]
