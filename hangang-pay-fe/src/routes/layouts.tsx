import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { AppShell, BottomNav } from '@/components/common'

type MainNavType = 'user' | 'merchant'

interface MainLayoutProps {
  navType: MainNavType
}

const userActiveByPath: Record<string, string> = {
  '/home': 'home',
  '/pay/scan': 'scan',
  '/mypage/payments': 'payments',
  '/mypage': 'mypage',
}

const merchantActiveByPath: Record<string, string> = {
  '/merchant/home': 'home',
  '/merchant/payments': 'payments',
  '/merchant/mypage': 'mypage',
}

export function MainLayout({ navType }: MainLayoutProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const activeMap = navType === 'user' ? userActiveByPath : merchantActiveByPath
  const active = activeMap[location.pathname] ?? 'home'

  return (
    <AppShell bottomNav={<BottomNav type={navType} active={active} onNavigate={navigate} />}>
      <Outlet />
    </AppShell>
  )
}

interface FullscreenLayoutProps {
  fullBleed?: boolean
}

export function FullscreenLayout({ fullBleed = false }: FullscreenLayoutProps) {
  return (
    <AppShell fullBleed={fullBleed}>
      <Outlet />
    </AppShell>
  )
}
