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
import { FullscreenLayout, MainLayout } from '@/routes/layouts'
import { RequireAuth, RequireRole } from '@/routes/guards'
import { LoginPage } from '@/pages/LoginPage'
import { PlaceholderPage } from '@/pages/PlaceholderPage'
import { UserHistoryPage } from '@/pages/UserHistoryPage'
import { UserHomePage } from '@/pages/UserHomePage'
import { UserMyPage } from '@/pages/UserMyPage'
import { UserPayScanPage } from '@/pages/UserPayScanPage'
import { MerchantHomePage } from '@/pages/MerchantHomePage'
import { AccountManagementPage } from '@/pages/AccountManagementPage'
import { AddAccountPage } from '@/pages/AddAccountPage'
import { MerchantQrPage } from '@/pages/MerchantQrPage'
import { MerchantPaymentsPage } from '@/pages/MerchantPaymentsPage'
import { MerchantPaymentDetailPage } from '@/pages/MerchantPaymentDetailPage'
import { MerchantMyPage } from '@/pages/MerchantMyPage'
import { PayConfirmPage } from '@/pages/PayConfirmPage'
import { PinPage } from '@/pages/PinPage.tsx'
import { ProcessingPage } from '@/pages/ProcessingPage.tsx'
import { PayCompletePage } from '@/pages/PayCompletePage'
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
          // 하단 네비 있는 메인 레이아웃
          {
            element: <MainLayout navType="user" />,
            children: [
              { path: '/home', element: <UserHomePage /> },
              { path: '/mypage/payments', element: <UserHistoryPage /> },
              { path: '/mypage', element: <UserMyPage /> },
            ],
          },
          { path: '/mypage/accounts', element: <AccountManagementPage /> },
          { path: '/mypage/accounts/add', element: <AddAccountPage /> },
          // 결제 플로우 (하단 네비 없음)
          {
            element: <FullscreenLayout fullBleed />,
            children: [{ path: '/pay/scan', element: <UserPayScanPage /> }],
          },
          {
            element: <FullscreenLayout />,
            children: [
              { path: '/pay/amount/:merchantId', element: <PayConfirmPage /> },
              { path: '/pay/confirm', element: <PayConfirmPage /> },
              { path: '/pay/pin', element: <PinPage /> },
              { path: '/pay/processing', element: <ProcessingPage /> },
              { path: '/pay/complete', element: <PayCompletePage /> },
            ],
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
              { path: '/merchant/qr', element: <MerchantQrPage /> },
              { path: '/merchant/payments', element: <MerchantPaymentsPage /> },
              { path: '/merchant/mypage', element: <MerchantMyPage />
              },
            ],
          },
          {
            element: <FullscreenLayout />,
            children: [
              {
                path: '/merchant/payments/:transactionId',
                element: <MerchantPaymentDetailPage />,
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
