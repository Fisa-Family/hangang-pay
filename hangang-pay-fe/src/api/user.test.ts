import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fetchUserProfile, type UserProfileResponse } from './user'
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
