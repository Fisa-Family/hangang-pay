import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useCurrentUser } from '@/auth/useCurrentUser'
import type { UserRole } from '@/auth/types'

function getDefaultPathByRole(role: UserRole) {
  return role === 'MERCHANT' ? '/merchant/home' : '/home'
}

// 로그인 여부 확인: 미로그인 시 /login으로 리다이렉트
export function RequireAuth() {
  const location = useLocation()
  const auth = useCurrentUser()

  if (auth.isLoading) return null
  if (!auth.isAuthenticated) return <Navigate to="/login" replace state={{ from: location }} />
  return <Outlet />
}

interface RequireRoleProps {
  roles: UserRole[]
}

// 역할 확인: 권한 없는 역할 접근 시 해당 역할 기본 홈으로 리다이렉트
export function RequireRole({ roles }: RequireRoleProps) {
  const auth = useCurrentUser()

  if (auth.isLoading) return null
  if (!auth.isAuthenticated) return <Navigate to="/login" replace />
  if (!auth.role) return <Navigate to="/login" replace />
  if (!roles.includes(auth.role)) return <Navigate to={getDefaultPathByRole(auth.role)} replace />
  return <Outlet />
}

// 이미 로그인된 사용자가 /login 접근 시 role에 따라 home으로 리다이렉트
export function RedirectIfAuth() {
  const auth = useCurrentUser()

  if (auth.isLoading) return null
  if (auth.isAuthenticated && auth.role) {
    return <Navigate to={getDefaultPathByRole(auth.role)} replace />
  }
  return <Outlet />
}
