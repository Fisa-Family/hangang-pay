import { createBrowserRouter, Navigate, useRouteError } from 'react-router-dom'
import { MainLayout } from '@/routes/layouts'
import { RequireAuth, RequireRole } from '@/routes/guards'
import { LoginPage } from '@/pages/LoginPage'
import { PlaceholderPage } from '@/pages/PlaceholderPage'
import { UserHomePage } from '@/pages/UserHomePage'
import { AppShell } from '@/components/common'

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
    element: <Navigate to="/home" replace />,
    errorElement: <RootErrorElement />,
  },
  {
    path: '/login',
    element: <LoginPage />,
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
              { path: '/mypage', element: <PlaceholderPage title="마이페이지" screenId="U-MY" /> },
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
              {
                path: '/merchant/home',
                element: <PlaceholderPage title="가맹점 홈" screenId="M-HOME" />,
              },
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
  // 미매칭 경로 홈으로 리다이렉트
  {
    path: '*',
    element: <Navigate to="/home" replace />,
  },
])
