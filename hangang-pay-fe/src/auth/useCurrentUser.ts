import { useQuery } from '@tanstack/react-query'
import type { CurrentUser, UserRole } from './types'

interface CurrentUserSnapshot {
  currentUser?: CurrentUser
  isAuthenticated: boolean
  isLoading: boolean
  role?: UserRole
}

const isAuthMockEnabled = import.meta.env.VITE_AUTH_MOCK === 'true'

function getMockCurrentUser(): CurrentUser | undefined {
  if (import.meta.env.VITE_AUTH_MOCK_AUTHENTICATED === 'false') {
    return undefined
  }

  const role = import.meta.env.VITE_AUTH_MOCK_ROLE ?? 'USER'

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
