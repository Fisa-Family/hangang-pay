import { apiFetch } from './client'

// AUTH-LOGOUT: 세션 만료
export function logout(): Promise<void> {
  return apiFetch<void>('/auth/logout', { method: 'POST' })
}
