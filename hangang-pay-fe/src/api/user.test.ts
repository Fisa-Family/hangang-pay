import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as fc from 'fast-check'
import {
  fetchUserHistoriesNormalized,
  fetchUserProfile,
  normalizeHistoryItem,
  type UserProfileResponse,
} from './user'
import { groupByDate } from '@/pages/UserHistoryPage.helpers'
import * as client from './client'

describe('fetchUserProfile', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should call apiFetch with correct endpoint', async () => {
    const mockProfile: UserProfileResponse = {
      userId: 1,
      partyId: 123,
      username: '홍길동',
      phoneNumber: '01012345678',
      birthDate: '1990-01-01',
      region: '성동구',
    }

    const apiFetchSpy = vi.spyOn(client, 'apiFetch').mockResolvedValue(mockProfile)

    const result = await fetchUserProfile()

    expect(apiFetchSpy).toHaveBeenCalledWith('/users/profile')
    expect(result).toEqual(mockProfile)
  })

  it('should handle profile with null birthDate and region', async () => {
    const mockProfile: UserProfileResponse = {
      userId: 2,
      partyId: 456,
      username: '김철수',
      phoneNumber: '01098765432',
      birthDate: null,
      region: null,
    }

    vi.spyOn(client, 'apiFetch').mockResolvedValue(mockProfile)

    const result = await fetchUserProfile()

    expect(result.birthDate).toBeNull()
    expect(result.region).toBeNull()
  })

  it('should propagate API errors', async () => {
    const mockError = new client.ApiError('User not found', 404, 'USER_NOT_FOUND')

    vi.spyOn(client, 'apiFetch').mockRejectedValue(mockError)

    await expect(fetchUserProfile()).rejects.toThrow('User not found')
  })
})

// ────────────────────────────────────────────────────────────────────────────
// normalizeHistoryItem
// ────────────────────────────────────────────────────────────────────────────

type RawArg = Parameters<typeof normalizeHistoryItem>[0]

describe('normalizeHistoryItem', () => {
  describe('Example-Based Tests', () => {
    it('PAYMENT raw → sign "-" + merchantName + paymentId', () => {
      const raw: RawArg = {
        paymentId: 'pay-abc',
        merchantName: '서울식당',
        amount: 12000,
        paidAt: '2026-05-14T10:30:00Z',
        status: 'COMPLETED',
        historyType: 'PAYMENT',
        cursorCreatedAt: '2026-05-14T10:30:00Z',
        cursorId: 1,
      }
      expect(normalizeHistoryItem(raw)).toEqual({
        id: 'pay-abc',
        displayType: 'PAYMENT',
        counterpartName: '서울식당',
        amount: 12000,
        sign: '-',
        createdAt: '2026-05-14T10:30:00Z',
      })
    })

    it('CANCEL raw → sign "+" + merchantName + paymentId', () => {
      const raw: RawArg = {
        paymentId: 'pay-xyz',
        merchantName: '서울식당',
        amount: 12000,
        paidAt: '2026-05-15T11:00:00Z',
        status: 'CANCELLED',
        historyType: 'CANCEL',
        cursorCreatedAt: '2026-05-15T11:00:00Z',
        cursorId: 2,
      }
      expect(normalizeHistoryItem(raw)).toEqual({
        id: 'pay-xyz',
        displayType: 'CANCEL',
        counterpartName: '서울식당',
        amount: 12000,
        sign: '+',
        createdAt: '2026-05-15T11:00:00Z',
      })
    })

    it('CHARGE raw → sign "+" + 한강사랑상품권 + CHARGE-${id}', () => {
      const raw: RawArg = {
        id: 7,
        amount: 100000,
        discountAmount: 10000,
        discountRate: 10,
        status: 'COMPLETED',
        historyType: 'CHARGE',
        chargedAt: '2026-05-16T09:00:00Z',
      }
      expect(normalizeHistoryItem(raw)).toEqual({
        id: 'CHARGE-7',
        displayType: 'CHARGE',
        counterpartName: '한강사랑상품권',
        amount: 100000,
        sign: '+',
        createdAt: '2026-05-16T09:00:00Z',
      })
    })

    it('EXCHANGE raw → sign "-" + 한강사랑상품권 + EXCHANGE-${id}', () => {
      const raw: RawArg = {
        id: 3,
        amount: 50000,
        status: 'COMPLETED',
        historyType: 'EXCHANGE',
        exchangedAt: '2026-05-17T14:00:00Z',
      }
      expect(normalizeHistoryItem(raw)).toEqual({
        id: 'EXCHANGE-3',
        displayType: 'EXCHANGE',
        counterpartName: '한강사랑상품권',
        amount: 50000,
        sign: '-',
        createdAt: '2026-05-17T14:00:00Z',
      })
    })
  })

  describe('Property-Based Tests', () => {
    const rawPayment = fc.record({
      paymentId: fc.string({ minLength: 1 }),
      merchantName: fc.string({ minLength: 1 }),
      amount: fc.nat(),
      paidAt: fc.constantFrom('2026-05-14T10:30:00Z', '2026-05-15T18:50:00Z'),
      status: fc.constantFrom('COMPLETED', 'PENDING', 'FAILED'),
      historyType: fc.constantFrom('PAYMENT' as const, 'CANCEL' as const),
      cursorCreatedAt: fc.string(),
      cursorId: fc.nat(),
    })

    const rawCharge = fc.record({
      id: fc.nat({ max: 100000 }).map((n) => n + 1),
      amount: fc.nat(),
      discountAmount: fc.nat(),
      discountRate: fc.nat({ max: 100 }),
      status: fc.constantFrom('COMPLETED', 'PENDING', 'FAILED'),
      historyType: fc.constant('CHARGE' as const),
      chargedAt: fc.constantFrom('2026-05-14T10:30:00Z', '2026-05-15T18:50:00Z'),
    })

    const rawExchange = fc.record({
      id: fc.nat({ max: 100000 }).map((n) => n + 1),
      amount: fc.nat(),
      status: fc.constantFrom('COMPLETED', 'PENDING', 'FAILED'),
      historyType: fc.constant('EXCHANGE' as const),
      exchangedAt: fc.constantFrom('2026-05-14T10:30:00Z', '2026-05-15T18:50:00Z'),
    })

    const rawAny = fc.oneof(rawPayment, rawCharge, rawExchange)

    it('Property PROP-1: sign / amount mapping - **Validates: Requirements REQ-3**', () => {
      fc.assert(
        fc.property(rawAny, (raw) => {
          const r = normalizeHistoryItem(raw)
          expect(r.amount).toBe(raw.amount)
          expect(r.amount).toBeGreaterThanOrEqual(0)
          if (r.displayType === 'CHARGE' || r.displayType === 'CANCEL') {
            expect(r.sign).toBe('+')
          } else {
            expect(r.sign).toBe('-')
          }
        })
      )
    })

    it('Property PROP-2: counterpartName mapping - **Validates: Requirements REQ-3**', () => {
      fc.assert(
        fc.property(rawAny, (raw) => {
          const r = normalizeHistoryItem(raw)
          if (raw.historyType === 'PAYMENT' || raw.historyType === 'CANCEL') {
            expect(r.counterpartName).toBe(raw.merchantName)
          } else {
            expect(r.counterpartName).toBe('한강사랑상품권')
          }
        })
      )
    })
  })
})

// ────────────────────────────────────────────────────────────────────────────
// fetchUserHistoriesNormalized
// ────────────────────────────────────────────────────────────────────────────

describe('fetchUserHistoriesNormalized', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const emptyResponse = {
    historyType: 'PAYMENT',
    response: {
      content: [],
      nextCursorCreatedAt: null,
      nextCursorId: null,
      hasNext: false,
    },
  }

  it('includes historyType and default size=20 in the query string', async () => {
    const spy = vi.spyOn(client, 'apiFetch').mockResolvedValue(emptyResponse)
    await fetchUserHistoriesNormalized({ tab: 'PAYMENT' })
    const [path] = spy.mock.calls[0]
    expect(path).toContain('historyType=PAYMENT')
    expect(path).toContain('size=20')
    expect(path).not.toContain('cursorCreatedAt')
    expect(path).not.toContain('cursorId')
  })

  it('includes cursor params when provided', async () => {
    const spy = vi.spyOn(client, 'apiFetch').mockResolvedValue(emptyResponse)
    await fetchUserHistoriesNormalized({
      tab: 'CHARGE',
      size: 10,
      cursorCreatedAt: '2026-05-14T10:30:00Z',
      cursorId: 42,
    })
    const [path] = spy.mock.calls[0]
    expect(path).toContain('historyType=CHARGE')
    expect(path).toContain('size=10')
    expect(path).toContain('cursorCreatedAt=')
    expect(path).toContain('cursorId=42')
  })

  it('returns nextCursor when hasNext=true and both cursor fields are present', async () => {
    vi.spyOn(client, 'apiFetch').mockResolvedValue({
      historyType: 'PAYMENT',
      response: {
        content: [],
        nextCursorCreatedAt: '2026-05-14T10:30:00Z',
        nextCursorId: 5,
        hasNext: true,
      },
    })
    const result = await fetchUserHistoriesNormalized({ tab: 'PAYMENT' })
    expect(result.nextCursor).toEqual({
      cursorCreatedAt: '2026-05-14T10:30:00Z',
      cursorId: 5,
    })
  })

  it('returns nextCursor=null when hasNext=false', async () => {
    vi.spyOn(client, 'apiFetch').mockResolvedValue({
      historyType: 'PAYMENT',
      response: {
        content: [],
        nextCursorCreatedAt: '2026-05-14T10:30:00Z',
        nextCursorId: 5,
        hasNext: false,
      },
    })
    const result = await fetchUserHistoriesNormalized({ tab: 'PAYMENT' })
    expect(result.nextCursor).toBeNull()
  })

  it('returns nextCursor=null when hasNext=true but cursor fields missing', async () => {
    vi.spyOn(client, 'apiFetch').mockResolvedValue({
      historyType: 'PAYMENT',
      response: {
        content: [],
        nextCursorCreatedAt: null,
        nextCursorId: null,
        hasNext: true,
      },
    })
    const result = await fetchUserHistoriesNormalized({ tab: 'PAYMENT' })
    expect(result.nextCursor).toBeNull()
  })

  it('normalizes content into HistoryListItem array', async () => {
    vi.spyOn(client, 'apiFetch').mockResolvedValue({
      historyType: 'PAYMENT',
      response: {
        content: [
          {
            paymentId: 'pay-1',
            merchantName: '서울식당',
            amount: 12000,
            paidAt: '2026-05-14T10:30:00Z',
            status: 'COMPLETED',
            historyType: 'PAYMENT',
            cursorCreatedAt: '2026-05-14T10:30:00Z',
            cursorId: 1,
          },
        ],
        nextCursorCreatedAt: null,
        nextCursorId: null,
        hasNext: false,
      },
    })
    const result = await fetchUserHistoriesNormalized({ tab: 'PAYMENT' })
    expect(result.items).toHaveLength(1)
    expect(result.items[0]).toMatchObject({
      id: 'pay-1',
      displayType: 'PAYMENT',
      counterpartName: '서울식당',
      sign: '-',
      amount: 12000,
    })
  })
})

// ────────────────────────────────────────────────────────────────────────────
// groupByDate (Property PROP-4)
// ────────────────────────────────────────────────────────────────────────────

describe('groupByDate', () => {
  it('Property PROP-4: order preserved & lossless partition - **Validates: Requirements REQ-4**', () => {
    const itemArb = fc.record({
      id: fc.string({ minLength: 1 }),
      displayType: fc.constantFrom(
        'PAYMENT' as const,
        'CANCEL' as const,
        'CHARGE' as const,
        'EXCHANGE' as const
      ),
      counterpartName: fc.string(),
      amount: fc.nat(),
      sign: fc.constantFrom('+' as const, '-' as const),
      createdAt: fc.constantFrom(
        '2026-05-14T10:00:00Z',
        '2026-05-14T18:00:00Z',
        '2026-05-15T09:00:00Z',
        '2026-05-13T22:30:00Z'
      ),
    })

    fc.assert(
      fc.property(fc.array(itemArb), (items) => {
        const groups = groupByDate(items)

        // 1. flatten == input (order/value preserved)
        const flattened = groups.flatMap((g) => g.items)
        expect(flattened).toEqual(items)

        // 2. within each group, every item's date prefix matches
        for (const group of groups) {
          for (const item of group.items) {
            expect(item.createdAt.slice(0, 10)).toBe(group.date)
          }
        }

        // 3. adjacent groups have different dates
        for (let i = 0; i < groups.length - 1; i += 1) {
          expect(groups[i].date).not.toBe(groups[i + 1].date)
        }
      })
    )
  })

  it('handles empty array', () => {
    expect(groupByDate([])).toEqual([])
  })

  it('groups consecutive same-date items together', () => {
    const items = [
      {
        id: 'a',
        displayType: 'PAYMENT' as const,
        counterpartName: 'X',
        amount: 1,
        sign: '-' as const,
        createdAt: '2026-05-14T10:00:00Z',
      },
      {
        id: 'b',
        displayType: 'PAYMENT' as const,
        counterpartName: 'Y',
        amount: 2,
        sign: '-' as const,
        createdAt: '2026-05-14T18:00:00Z',
      },
      {
        id: 'c',
        displayType: 'PAYMENT' as const,
        counterpartName: 'Z',
        amount: 3,
        sign: '-' as const,
        createdAt: '2026-05-13T09:00:00Z',
      },
    ]
    const groups = groupByDate(items)
    expect(groups).toHaveLength(2)
    expect(groups[0].date).toBe('2026-05-14')
    expect(groups[0].items).toHaveLength(2)
    expect(groups[1].date).toBe('2026-05-13')
    expect(groups[1].items).toHaveLength(1)
  })
})
