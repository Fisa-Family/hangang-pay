import { Navigate, Outlet, useLocation } from 'react-router-dom'

type UserRole = 'USER' | 'MERCHANT'

interface AuthSnapshot {
  isAuthenticated: boolean
  role?: UserRole
}

function useAuthSnapshot(): AuthSnapshot {
  // TODO: replace with useCurrentUser() after the API client and /api/me are added.
  return { isAuthenticated: true }
}

export function RequireAuth() {
  const location = useLocation()
  const auth = useAuthSnapshot()

  if (!auth.isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return <Outlet />
}

interface RequireRoleProps {
  roles: UserRole[]
}

export function RequireRole({ roles }: RequireRoleProps) {
  const auth = useAuthSnapshot()

  if (auth.role && !roles.includes(auth.role)) {
    return <Navigate to="/home" replace />
  }

  return <Outlet />
}
