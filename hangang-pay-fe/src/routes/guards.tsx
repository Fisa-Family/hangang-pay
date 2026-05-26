import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useCurrentUser } from '@/auth/useCurrentUser'
import type { UserRole } from '@/auth/types'

// 역할별 기본 이동 경로
function getDefaultPathByRole(role: UserRole) {
  return role === 'MERCHANT' ? '/merchant/home' : '/home'
}

// 로그인 여부 확인: 미로그인 시 /login으로 리다이렉트
export function RequireAuth() {
  const location = useLocation()
  const auth = useCurrentUser()

  // 개발 중 인증 우회: 로그인 없이 URL 직접 접근 허용 (로그인 구현 후 아래 두 줄 제거)
  if (import.meta.env.DEV) return <Outlet />

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

  // 개발 중 역할 검사 우회: 모든 화면 접근 허용 (로그인 구현 후 아래 두 줄 제거)
  if (import.meta.env.DEV) return <Outlet />

  if (auth.isLoading) return null
  if (!auth.isAuthenticated) return <Navigate to="/login" replace />
  if (!auth.role) return <Navigate to="/login" replace />
  if (!roles.includes(auth.role)) return <Navigate to={getDefaultPathByRole(auth.role)} replace />
  return <Outlet />
}
