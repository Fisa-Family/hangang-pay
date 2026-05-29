import { useQuery } from '@tanstack/react-query'
import type { CurrentUser, UserRole } from './types'

interface CurrentUserSnapshot {
  currentUser?: CurrentUser
  isAuthenticated: boolean
  isLoading: boolean
  role?: UserRole
}

// VITE_AUTH_MOCK=true 일 때 mock 모드 활성화. 실제 로그인 없이 개발 가능.
const isAuthMockEnabled = import.meta.env.VITE_AUTH_MOCK === 'true'
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'

// mock 세션은 앱 생애주기 동안 한 번만 생성한다.
let mockSessionCreated = false

// TODO: mock 코드 — 실제 로그인 API 검증 완료 후 createMockSession, getMockCurrentUser 제거
// mock 모드에서 BE에 실제 세션을 만들어 도메인 API 호출이 정상 동작하도록 한다.
async function createMockSession(role: string): Promise<void> {
  if (mockSessionCreated) return
  try {
    const path = role === 'MERCHANT' ? '/auth/merchants/login' : '/auth/users/login'
    const body =
      role === 'MERCHANT'
        ? { businessNumber: '1234567890', password: 'password' }
        : { phoneNumber: '01012345678', password: 'password' }
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

// localStorage의 role 값으로 인증 상태를 복원한다.
// 로그인 성공 시 localStorage.setItem('role', ...), 로그아웃·401 시 removeItem('role').
// id/name은 현재 FE에서 사용하지 않아 빈 값으로 채운다.
// TODO: GET /auth/me 구현 후 이 함수를 apiFetch('/auth/me') 호출로 교체하고 localStorage 의존 제거
function fetchCurrentUser(): CurrentUser | undefined {
  const role = localStorage.getItem('role') as UserRole | null
  if (role !== 'USER' && role !== 'MERCHANT') return undefined
  return { id: 0, name: '', role }
}

export function useCurrentUser(): CurrentUserSnapshot {
  const query = useQuery({
    queryKey: ['currentUser'],
    queryFn: isAuthMockEnabled ? getMockCurrentUser : fetchCurrentUser,
    retry: false,
    staleTime: Infinity,
  })

  return {
    currentUser: query.data,
    isAuthenticated: Boolean(query.data),
    isLoading: query.isLoading,
    role: query.data?.role,
  }
}
