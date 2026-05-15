import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useCurrentUser } from '@/auth/useCurrentUser'
import type { UserRole } from '@/auth/types'

function getDefaultPathByRole(role: UserRole) {
  return role === 'MERCHANT' ? '/merchant/home' : '/home'
}

export function RequireAuth() {
  const location = useLocation()
  const auth = useCurrentUser()

  if (auth.isLoading) {
    return null
  }

  if (!auth.isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return <Outlet />
}

interface RequireRoleProps {
  roles: UserRole[]
}

export function RequireRole({ roles }: RequireRoleProps) {
  const auth = useCurrentUser()

  if (auth.isLoading) {
    return null
  }

  if (!auth.isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  if (!auth.role) {
    return <Navigate to="/login" replace />
  }

  if (!roles.includes(auth.role)) {
    return <Navigate to={getDefaultPathByRole(auth.role)} replace />
  }

  return <Outlet />
}
