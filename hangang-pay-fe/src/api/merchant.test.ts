import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fetchMerchantInfo, type MerchantInfoResponse } from './merchant'
import * as client from './client'

describe('fetchMerchantInfo', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('PROP-1: should call apiFetch with /merchant/{id} for various IDs (URL invariant)', async () => {
    const mock: MerchantInfoResponse = {
      merchantId: 1,
      partyId: 100,
      merchantName: 'M',
      address: 'A',
      walletAddress: '0x0',
    }
    const spy = vi.spyOn(client, 'apiFetch').mockResolvedValue(mock)

    await fetchMerchantInfo(1)
    await fetchMerchantInfo(42)
    await fetchMerchantInfo(9999)

    expect(spy).toHaveBeenNthCalledWith(1, '/merchant/1')
    expect(spy).toHaveBeenNthCalledWith(2, '/merchant/42')
    expect(spy).toHaveBeenNthCalledWith(3, '/merchant/9999')
  })

  it('PROP-2: should return BE response verbatim without transformation', async () => {
    const mock: MerchantInfoResponse = {
      merchantId: 42,
      partyId: 314,
      merchantName: '서울식당',
      address: '서울시 성동구 왕십리로 123',
      walletAddress: '0xabcdef1234567890',
    }
    vi.spyOn(client, 'apiFetch').mockResolvedValue(mock)

    const result = await fetchMerchantInfo(42)

    expect(result).toEqual(mock)
    expect(result.walletAddress).toBe('0xabcdef1234567890')
  })

  it('should propagate ApiError from apiFetch', async () => {
    const mockError = new client.ApiError('가맹점을 찾을 수 없습니다.', 404, 'MERCHANT_NOT_FOUND')
    vi.spyOn(client, 'apiFetch').mockRejectedValue(mockError)

    await expect(fetchMerchantInfo(999)).rejects.toThrow('가맹점을 찾을 수 없습니다.')
  })
})
