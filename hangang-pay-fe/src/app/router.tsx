import { createBrowserRouter, Navigate } from 'react-router-dom'
import { MainLayout } from '@/routes/layouts'
import { RequireAuth, RequireRole } from '@/routes/guards'
import { LoginPage } from '@/pages/LoginPage'
import { PlaceholderPage } from '@/pages/PlaceholderPage'
import { UserHomePage } from '@/pages/UserHomePage'
import { RootErrorElement } from '@/app/RootErrorElement'

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
