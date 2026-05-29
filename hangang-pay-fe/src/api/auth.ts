import { apiFetch } from './client'
import type { UserRole } from '@/auth/types'

export interface LoginResult {
  principalId: number
  partyId: number
  role: UserRole
}

export function loginUser(phoneNumber: string, password: string): Promise<LoginResult> {
  return apiFetch<LoginResult>('/auth/users/login', {
    method: 'POST',
    body: JSON.stringify({ phoneNumber, password }),
  })
}

export function loginMerchant(businessNumber: string, password: string): Promise<LoginResult> {
  return apiFetch<LoginResult>('/auth/merchants/login', {
    method: 'POST',
    body: JSON.stringify({ businessNumber, password }),
  })
}

export function logout(): Promise<void> {
  return apiFetch<void>('/auth/logout', { method: 'POST' })
}
