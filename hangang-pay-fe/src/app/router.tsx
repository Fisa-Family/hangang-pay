/* eslint-disable react-refresh/only-export-components */
import {
  createBrowserRouter,
  Navigate,
  useLocation,
  useNavigate,
  useRouteError,
} from 'react-router-dom'
import { useEffect } from 'react'
import { useCurrentUser } from '@/auth/useCurrentUser'
import { MainLayout } from '@/routes/layouts'
import { RequireAuth, RequireRole } from '@/routes/guards'
import { LoginPage } from '@/pages/LoginPage'
import { PlaceholderPage } from '@/pages/PlaceholderPage'
import { UserHomePage } from '@/pages/UserHomePage'
import { UserMyPage } from '@/pages/UserMyPage'
import { MerchantHomePage } from '@/pages/MerchantHomePage'
import { AccountManagementPage } from '@/pages/AccountManagementPage'
import { AddAccountPage } from '@/pages/AddAccountPage'
import { AppShell } from '@/components/common'

// 미등록 경로 접근 시 경로 기반으로 해당 영역 홈으로 교체
function GoBack() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  useEffect(() => {
    const home = pathname.startsWith('/merchant/') ? '/merchant/home' : '/home'
    navigate(home, { replace: true })
  }, [pathname, navigate])
  return null
}

// 진입점(/) 에서 역할에 맞는 홈으로 리다이렉트
function RoleRedirect() {
  const { role, isLoading } = useCurrentUser()
  if (isLoading) return null
  return <Navigate to={role === 'MERCHANT' ? '/merchant/home' : '/home'} replace />
}

// 라우트 레벨 에러 fallback (예상치 못한 에러 전체 포착)
function RootErrorElement() {
  const error = useRouteError()
  const message = error instanceof Error ? error.message : '알 수 없는 오류가 발생했습니다.'
  return (
    <AppShell>
      <section className="flex h-full flex-col items-center justify-center gap-4 text-center">
        <p className="text-sm font-semibold text-destructive">{message}</p>
        <button
          type="button"
          className="text-sm font-semibold text-primary"
          onClick={() => window.location.replace('/')}
        >
          홈으로 돌아가기
        </button>
      </section>
    </AppShell>
  )
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <RoleRedirect />,
    errorElement: <RootErrorElement />,
  },
  {
    path: '/login',
    element: <LoginPage />,
  },
  // /merchant 단축 진입점 (홈으로 리다이렉트)
  {
    path: '/merchant',
    element: <Navigate to="/merchant/home" replace />,
  },
  {
    element: <RequireAuth />,
    children: [
      // 소비자 화면 (USER 권한)
      {
        element: <RequireRole roles={['USER']} />,
        children: [
          {
            element: <MainLayout navType="user" />,
            children: [
              { path: '/home', element: <UserHomePage /> },
              {
                path: '/pay/scan',
                element: <PlaceholderPage title="QR 스캔" screenId="U-PAY-SCAN" />,
              },
              {
                path: '/mypage/payments',
                element: <PlaceholderPage title="결제내역" screenId="U-MY-PAYMENTS" />,
              },
              {
                path: '/mypage/accounts',
                element: <PlaceholderPage title="계좌 관리" screenId="U-MY-ACCOUNTS" />,
              },
              { path: '/mypage', element: <UserMyPage /> },
            ],
          },
          { path: '/mypage/accounts', element: <AccountManagementPage /> },
          { path: '/mypage/accounts/add', element: <AddAccountPage /> },
          {
            path: '/mypage/accounts/new',
            element: <AddAccountPage />,
          },
        ],
      },
      // 가맹점 화면 (MERCHANT 권한)
      {
        element: <RequireRole roles={['MERCHANT']} />,
        children: [
          {
            element: <MainLayout navType="merchant" />,
            children: [
              { path: '/merchant/home', element: <MerchantHomePage /> },
              {
                path: '/merchant/payments',
                element: <PlaceholderPage title="결제 내역" screenId="M-PAY" />,
              },
              {
                path: '/merchant/mypage',
                element: <PlaceholderPage title="가맹점 마이" screenId="M-MY" />,
              },
            ],
          },
        ],
      },
    ],
  },
  // 미매칭 경로 → 이전 페이지 유지
  {
    path: '*',
    element: <GoBack />,
  },
])
