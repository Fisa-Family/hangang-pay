import { useQuery } from '@tanstack/react-query'
import type { CurrentUser, UserRole } from './types'

interface CurrentUserSnapshot {
  currentUser?: CurrentUser
  isAuthenticated: boolean
  isLoading: boolean
  role?: UserRole
}

const isAuthMockEnabled = import.meta.env.VITE_AUTH_MOCK === 'true'
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'

let mockSessionCreated = false

// TODO: 테스트용 더미아이디, 추후 삭제해야 함
async function createMockSession(role: string): Promise<void> {
  if (mockSessionCreated) return
  try {
    const path = role === 'MERCHANT' ? '/auth/merchants/login' : '/auth/users/login'
    const body =
      role === 'MERCHANT'
        ? { phoneNumber: '01087654321', password: 'test1234' }
        : { phoneNumber: '01012345678', password: 'test1234' }
    await fetch(`${BASE_URL}${path}`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    mockSessionCreated = true
  } catch {
    // BE 미기동 시 무시
  }
}

async function getMockCurrentUser(): Promise<CurrentUser | undefined> {
  if (import.meta.env.VITE_AUTH_MOCK_AUTHENTICATED === 'false') {
    return undefined
  }

  const role = import.meta.env.VITE_AUTH_MOCK_ROLE ?? 'USER'
  await createMockSession(role)

  return {
    id: role === 'MERCHANT' ? 2 : 1,
    name: role === 'MERCHANT' ? '한강상점' : '김한강',
    role,
  }
}

async function fetchCurrentUser(): Promise<CurrentUser | undefined> {
  // TODO: replace this with the BE current-user endpoint after auth API is finalized.
  return undefined
}

export function useCurrentUser(): CurrentUserSnapshot {
  const query = useQuery({
    queryKey: ['currentUser'],
    queryFn: isAuthMockEnabled ? getMockCurrentUser : fetchCurrentUser,
    retry: false,
    staleTime: 1000 * 30,
  })

  return {
    currentUser: query.data,
    isAuthenticated: Boolean(query.data),
    isLoading: query.isLoading,
    role: query.data?.role,
  }
}
