import { http, HttpResponse } from 'msw'
import { mockUserProfile, mockAccounts, mockWalletBalance } from '../fixtures/user'
import {
  mockAllHistoryPage,
  mockPaymentHistoryPage,
  mockChargeHistoryPage,
  mockExchangeHistoryPage,
  mockPaymentDetail,
  mockChargeDetail,
  mockExchangeDetail,
} from '../fixtures/history'

const ok = <T>(result: T) => HttpResponse.json({ isSuccess: true, result })
const noContent = () => HttpResponse.json({ isSuccess: true })

const BASE = '/api/v1'

export const userHandlers = [
  http.get(`${BASE}/users/profile`, () => ok(mockUserProfile)),

  http.get(`${BASE}/wallet/balance`, () => ok(mockWalletBalance)),

  http.get(`${BASE}/users/histories`, ({ request }) => {
    const url = new URL(request.url)
    const type = url.searchParams.get('type')
    switch (type) {
      case 'PAYMENT':
        return ok(mockPaymentHistoryPage)
      case 'CHARGE':
        return ok(mockChargeHistoryPage)
      case 'EXCHANGE':
        return ok(mockExchangeHistoryPage)
      default:
        return ok(mockAllHistoryPage)
    }
  }),

  http.get(`${BASE}/users/histories/:id`, ({ request }) => {
    const url = new URL(request.url)
    const type = url.searchParams.get('type')
    switch (type) {
      case 'CHARGE':
        return ok(mockChargeDetail)
      case 'EXCHANGE':
        return ok(mockExchangeDetail)
      default:
        return ok(mockPaymentDetail)
    }
  }),

  http.get(`${BASE}/accounts`, () => ok({ accounts: mockAccounts })),

  http.post(`${BASE}/accounts`, () =>
    ok({
      accountId: 3,
      institutionCode: '020',
      bankName: '우리은행',
      maskedAccountNumber: '****-****-9999',
      accountType: 'SECONDARY',
    })
  ),

  http.delete(`${BASE}/accounts/:accountId`, () => noContent()),

  http.patch(`${BASE}/accounts/:accountId/primary`, ({ params }) =>
    ok({ accountId: Number(params.accountId) })
  ),
]
