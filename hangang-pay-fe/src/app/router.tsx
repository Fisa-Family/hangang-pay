import { createBrowserRouter, Navigate } from 'react-router-dom'
import { MainLayout } from '@/routes/layouts'
import { RequireAuth, RequireRole } from '@/routes/guards'
import { LoginPage } from '@/pages/LoginPage'
import { PlaceholderPage } from '@/pages/PlaceholderPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/home" replace />,
  },
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <RequireRole roles={['USER']} />,
        children: [
          {
            element: <MainLayout navType="user" />,
            children: [
              { path: '/home', element: <PlaceholderPage title="홈" screenId="U-HOME" /> },
              { path: '/pay/scan', element: <PlaceholderPage title="QR 스캔" screenId="U-PAY-SCAN" /> },
              {
                path: '/mypage/payments',
                element: <PlaceholderPage title="결제내역" screenId="U-MY-PAYMENTS" />,
              },
              { path: '/mypage', element: <PlaceholderPage title="마이페이지" screenId="U-MY" /> },
            ],
          },
        ],
      },
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
  {
    path: '*',
    element: <Navigate to="/home" replace />,
  },
])
