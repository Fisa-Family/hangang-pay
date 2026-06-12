/* eslint-disable react-refresh/only-export-components */
import {
  createBrowserRouter,
  Navigate,
  useLocation,
  useNavigate,
  useRouteError,
} from 'react-router-dom'
import { lazy, Suspense, useEffect, type ComponentType } from 'react'
import { useCurrentUser } from '@/auth/useCurrentUser'
import { FullscreenLayout, MainLayout } from '@/routes/layouts'
import { RequireAuth, RequireRole, RedirectIfAuth } from '@/routes/guards'
import { AppShell } from '@/components/common'

// 페이지 단위 lazy import: 초기 청크에는 layout/guard만 포함하고
// 역할(USER/MERCHANT)·플로우(AUTH/PAYMENT 등)별 화면은 진입 시점에 로드한다.
const LoginPage = lazy(() =>
  import('@/pages/auth/LoginPage').then((m) => ({ default: m.LoginPage }))
)
const RegisterTermsPage = lazy(() =>
  import('@/pages/register/RegisterTermsPage').then((m) => ({ default: m.RegisterTermsPage }))
)
const RegisterVerifyPage = lazy(() =>
  import('@/pages/register/RegisterVerifyPage').then((m) => ({ default: m.RegisterVerifyPage }))
)
const RegisterPasswordPage = lazy(() =>
  import('@/pages/register/RegisterPasswordPage').then((m) => ({
    default: m.RegisterPasswordPage,
  }))
)
const RegisterAccountPage = lazy(() =>
  import('@/pages/register/RegisterAccountPage').then((m) => ({ default: m.RegisterAccountPage }))
)
const RegisterPinPage = lazy(() =>
  import('@/pages/register/RegisterPinPage').then((m) => ({ default: m.RegisterPinPage }))
)
const MerchantRegisterBusinessPage = lazy(() =>
  import('@/pages/register/MerchantRegisterBusinessPage').then((m) => ({
    default: m.MerchantRegisterBusinessPage,
  }))
)
const UserHomePage = lazy(() =>
  import('@/pages/user/UserHomePage').then((m) => ({ default: m.UserHomePage }))
)
const UserMyPage = lazy(() =>
  import('@/pages/user/UserMyPage').then((m) => ({ default: m.UserMyPage }))
)
const UserHistoryPage = lazy(() =>
  import('@/pages/history/UserHistoryPage').then((m) => ({ default: m.UserHistoryPage }))
)
const UserHistoryDetailPage = lazy(() =>
  import('@/pages/history/UserHistoryDetailPage').then((m) => ({
    default: m.UserHistoryDetailPage,
  }))
)
const UserPayScanPage = lazy(() =>
  import('@/pages/payment/UserPayScanPage').then((m) => ({ default: m.UserPayScanPage }))
)
const PayConfirmPage = lazy(() =>
  import('@/pages/payment/PayConfirmPage').then((m) => ({ default: m.PayConfirmPage }))
)
const PinPage = lazy(() => import('@/pages/shared/PinPage').then((m) => ({ default: m.PinPage })))
const ProcessingPage = lazy(() =>
  import('@/pages/shared/ProcessingPage').then((m) => ({ default: m.ProcessingPage }))
)
const CompletePage = lazy(() =>
  import('@/pages/shared/CompletePage').then((m) => ({ default: m.CompletePage }))
)
const ChargeAmountPage = lazy(() =>
  import('@/pages/charge/ChargeAmountPage').then((m) => ({ default: m.ChargeAmountPage }))
)
const RefundCheckPage = lazy(() =>
  import('@/pages/refund/RefundCheckPage').then((m) => ({ default: m.RefundCheckPage }))
)
const AccountManagementPage = lazy(() =>
  import('@/pages/account/AccountManagementPage').then((m) => ({
    default: m.AccountManagementPage,
  }))
)
const AddAccountPage = lazy(() =>
  import('@/pages/account/AddAccountPage').then((m) => ({ default: m.AddAccountPage }))
)
const MerchantHomePage = lazy(() =>
  import('@/pages/merchant/MerchantHomePage').then((m) => ({ default: m.MerchantHomePage }))
)
const MerchantQrPage = lazy(() =>
  import('@/pages/merchant/MerchantQrPage').then((m) => ({ default: m.MerchantQrPage }))
)
const MerchantPaymentsPage = lazy(() =>
  import('@/pages/merchant/MerchantPaymentsPage').then((m) => ({
    default: m.MerchantPaymentsPage,
  }))
)
const MerchantPaymentDetailPage = lazy(() =>
  import('@/pages/merchant/MerchantPaymentDetailPage').then((m) => ({
    default: m.MerchantPaymentDetailPage,
  }))
)
const MerchantMyPage = lazy(() =>
  import('@/pages/merchant/MerchantMyPage').then((m) => ({ default: m.MerchantMyPage }))
)
const MerchantSettlementPage = lazy(() =>
  import('@/pages/merchant/MerchantSettlementPage').then((m) => ({
    default: m.MerchantSettlementPage,
  }))
)
const MerchantSettlementHistoryPage = lazy(() =>
  import('@/pages/merchant/MerchantSettlementHistoryPage').then((m) => ({
    default: m.MerchantSettlementHistoryPage,
  }))
)
const LandingPage = lazy(() =>
  import('@/pages/landing/LandingPage').then((m) => ({ default: m.LandingPage }))
)

// lazy 페이지 전환 시 표시할 최소 fallback
function PageFallback() {
  return (
    <div className="flex h-full w-full items-center justify-center">
      <div className="size-10 animate-spin rounded-full border-4 border-muted border-t-primary" />
    </div>
  )
}

// lazy 컴포넌트를 Suspense로 감싸 route element로 사용
function page(Component: ComponentType) {
  return (
    <Suspense fallback={<PageFallback />}>
      <Component />
    </Suspense>
  )
}

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

// 진입점(/) 에서 역할에 맞는 홈으로 리다이렉트, 미인증 시 시작화면 표시
function RoleRedirect() {
  const { role, isLoading, isAuthenticated } = useCurrentUser()
  if (isLoading) return null
  if (!isAuthenticated) return page(LandingPage)
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
    element: <RedirectIfAuth />,
    children: [
      { path: '/login', element: page(LoginPage) },
      { path: '/register/terms', element: page(RegisterTermsPage) },
      { path: '/register/verify', element: page(RegisterVerifyPage) },
      { path: '/register/password', element: page(RegisterPasswordPage) },
      { path: '/register/account', element: page(RegisterAccountPage) },
      { path: '/register/pin', element: page(RegisterPinPage) },
      { path: '/register/processing', element: page(ProcessingPage) },
      { path: '/register/complete', element: page(CompletePage) },
      { path: '/merchant/register/terms', element: page(RegisterTermsPage) },
      { path: '/merchant/register/verify', element: page(RegisterVerifyPage) },
      { path: '/merchant/register/password', element: page(RegisterPasswordPage) },
      { path: '/merchant/register/business', element: page(MerchantRegisterBusinessPage) },
      { path: '/merchant/register/account', element: page(RegisterAccountPage) },
      { path: '/merchant/register/pin', element: page(RegisterPinPage) },
      { path: '/merchant/register/processing', element: page(ProcessingPage) },
      { path: '/merchant/register/complete', element: page(CompletePage) },
    ],
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
              { path: '/home', element: page(UserHomePage) },
              { path: '/mypage/payments', element: page(UserHistoryPage) },
              { path: '/mypage', element: page(UserMyPage) },
            ],
          },
          { path: '/mypage/accounts', element: page(AccountManagementPage) },
          { path: '/mypage/accounts/add', element: page(AddAccountPage) },
          // 결제 플로우 (하단 네비 없음)
          {
            element: <FullscreenLayout fullBleed />,
            children: [{ path: '/pay/scan', element: page(UserPayScanPage) }],
          },
          {
            element: <FullscreenLayout />,
            children: [
              { path: '/pay/amount/:merchantId', element: page(PayConfirmPage) },
              { path: '/pay/confirm', element: page(PayConfirmPage) },
              { path: '/pay/pin', element: page(PinPage) },
              { path: '/pay/processing', element: page(ProcessingPage) },
              { path: '/pay/complete', element: page(CompletePage) },
              { path: '/charge/amount', element: page(ChargeAmountPage) },
              { path: '/charge/pin', element: page(PinPage) },
              { path: '/charge/processing', element: page(ProcessingPage) },
              { path: '/charge/complete', element: page(CompletePage) },
              { path: '/mypage/history/charges/:id', element: page(UserHistoryDetailPage) },
              { path: '/mypage/history/exchanges/:id', element: page(UserHistoryDetailPage) },
              { path: '/mypage/history/payments/:id', element: page(UserHistoryDetailPage) },
              { path: '/refund/check', element: page(RefundCheckPage) },
              { path: '/refund/pin', element: page(PinPage) },
              { path: '/refund/processing', element: page(ProcessingPage) },
              { path: '/refund/complete', element: page(CompletePage) },
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
              { path: '/merchant/home', element: page(MerchantHomePage) },
              { path: '/merchant/qr', element: page(MerchantQrPage) },
              { path: '/merchant/payments', element: page(MerchantPaymentsPage) },
              { path: '/merchant/mypage', element: page(MerchantMyPage) },
              {
                path: '/merchant/settlements',
                element: page(MerchantSettlementHistoryPage),
              },
            ],
          },
          {
            element: <FullscreenLayout />,
            children: [
              {
                path: '/merchant/payments/:transactionId',
                element: page(MerchantPaymentDetailPage),
              },
              { path: '/merchant/settlement', element: page(MerchantSettlementPage) },
              { path: '/merchant/settlement/complete', element: page(CompletePage) },
              { path: '/merchant/payments/cancel/complete', element: page(CompletePage) },
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
