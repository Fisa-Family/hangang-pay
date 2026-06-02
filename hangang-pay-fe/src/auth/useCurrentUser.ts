import { useQuery } from '@tanstack/react-query'
import type { CurrentUser, UserRole } from './types'

interface CurrentUserSnapshot {
  currentUser?: CurrentUser
  isAuthenticated: boolean
  isLoading: boolean
  role?: UserRole
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
    queryFn: fetchCurrentUser,
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
