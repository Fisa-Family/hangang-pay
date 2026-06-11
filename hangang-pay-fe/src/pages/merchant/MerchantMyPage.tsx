import { useState } from 'react'
import { Bell, Building2, ChevronRight, Headphones, Info, LogOut, Store } from 'lucide-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { ConfirmDialog, SettingsMenuCard } from '@/components/common'
import { fetchMerchantMyPage, type MerchantMyPageResponse } from '@/api/merchant'
import { logout } from '@/api/auth'
import { ApiError } from '@/api/client'
import { apiErrorMessages, isApiErrorCode } from '@/api/errorCodes'
import { formatMaskedAccount, formatPhoneNumber } from '@/lib/format'

// 1. API 스펙 상수 정의
const API_SPEC = {
  MERCHANT_009: { id: 'MERCHANT-009' },
  AUTH_LOGOUT: { id: 'AUTH-LOGOUT' },
} as const

// 2. API 오류 → 사용자 메시지 변환
function buildErrorMessage(spec: (typeof API_SPEC)[keyof typeof API_SPEC], error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code && isApiErrorCode(error.code)) return apiErrorMessages[error.code]
    return error.message
  }
  return `${spec.id} 요청에 실패했습니다. 네트워크 연결을 확인해 주세요.`
}

// 3. API 로드 실패 시 폴백 데이터
const EMPTY_DATA: MerchantMyPageResponse = {
  merchantId: 0,
  partyId: 0,
  merchantName: '-',
  businessNumber: '-',
  ownerName: '-',
  phoneNumber: '',
  address: '-',
  settlementAccount: {
    accountId: 0,
    institutionName: '',
    accountNumber: '',
    accountType: '',
  },
}

const noop = () => {}

// 8. 가맹점 마이페이지 메인 컴포넌트
export function MerchantMyPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  // 9. 로그아웃 다이얼로그 상태
  const [logoutDialogOpen, setLogoutDialogOpen] = useState(false)
  const [logoutError, setLogoutError] = useState<string | null>(null)

  // 10. 가맹점 마이페이지 데이터 조회 (MERCHANT-009)
  const profileQuery = useQuery({
    queryKey: ['merchant', 'mypage'],
    queryFn: fetchMerchantMyPage,
    retry: false,
  })

  // 11. 로그아웃 mutation
  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: () => {
      localStorage.removeItem('role')
      queryClient.clear()
      navigate('/login', { replace: true })
    },
    onError: (error) => {
      setLogoutError(buildErrorMessage(API_SPEC.AUTH_LOGOUT, error))
    },
  })

  const profileErrorMessage = profileQuery.error
    ? buildErrorMessage(API_SPEC.MERCHANT_009, profileQuery.error)
    : null

  // 12. API 응답 또는 폴백 데이터
  const data = profileQuery.data ?? EMPTY_DATA
  const accountDescription = formatMaskedAccount(
    data.settlementAccount.institutionName,
    data.settlementAccount.accountNumber
  )

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto pb-4">
      {/* 13. API 오류 배너 */}
      {profileErrorMessage && (
        <div
          role="alert"
          className="rounded-2xl border border-destructive/30 bg-destructive/5 p-4 text-sm font-medium text-destructive"
        >
          {profileErrorMessage}
        </div>
      )}

      {/* 14. 가맹점 정보 카드 (로딩 중 스켈레톤) */}
      {profileQuery.isLoading ? (
        <div
          aria-hidden
          className="h-[88px] animate-pulse rounded-2xl border border-border bg-muted/40"
        />
      ) : (
        <button
          type="button"
          onClick={noop}
          className="flex w-full items-center gap-4 rounded-2xl border border-border/40 bg-card p-5 text-left shadow-sm transition-colors active:bg-surface"
        >
          {/* 15. 상호 아이콘 */}
          <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground">
            <Store className="h-8 w-8" aria-hidden />
          </div>
          {/* 16. 상호명 + 사업자 정보 */}
          <div className="min-w-0 flex-1">
            <p className="text-lg font-bold text-foreground">{data.merchantName}</p>
            <div className="mt-1.5 flex flex-col gap-1">
              <p className="flex gap-2 text-xs">
                <span className="w-16 shrink-0 text-muted-foreground/70">전화번호</span>
                <span className="text-muted-foreground">{formatPhoneNumber(data.phoneNumber)}</span>
              </p>
              <p className="flex gap-2 text-xs">
                <span className="w-16 shrink-0 text-muted-foreground/70">주소</span>
                <span className="truncate text-muted-foreground">{data.address}</span>
              </p>
              <p className="flex gap-2 text-xs">
                <span className="w-16 shrink-0 text-muted-foreground/70">사업자번호</span>
                <span className="text-muted-foreground">{data.businessNumber}</span>
              </p>
            </div>
          </div>
          <ChevronRight className="h-5 w-5 shrink-0 text-muted-foreground" aria-hidden />
        </button>
      )}

      {/* 17-1. 정산 계좌 관리 — 핵심 업무 메뉴로 별도 분리 */}
      <section className="mt-6 flex flex-col gap-2">
        <SettingsMenuCard
          items={[
            {
              id: 'settlement-account',
              label: '정산 계좌 관리',
              icon: <Building2 className="h-6 w-6 text-primary" aria-hidden />,
              description: accountDescription || undefined,
              // 미구현 — 추후 /merchant/mypage/accounts 연결 예정
              onClick: () => {},
              disabled: profileQuery.isLoading,
            },
          ]}
        />
      </section>

      {/* 17-2. 설정 메뉴 */}
      <section className="mt-1 flex flex-col gap-2">
        <SettingsMenuCard
          items={[
            {
              id: 'notifications',
              label: '알림 설정',
              icon: <Bell className="h-6 w-6 text-yellow-500" aria-hidden />,
              onClick: noop,
            },
            {
              id: 'support',
              label: '고객센터',
              icon: <Headphones className="h-6 w-6 text-green-500" aria-hidden />,
              onClick: noop,
            },
            {
              id: 'about',
              label: '앱 정보',
              icon: <Info className="h-6 w-6 text-gray-500" aria-hidden />,
              onClick: noop,
            },
          ]}
        />

        {/* 18. 설정 메뉴 — 로그아웃 */}
        <SettingsMenuCard
          items={[
            {
              id: 'logout',
              label: '로그아웃',
              icon: <LogOut className="h-6 w-6 text-destructive" aria-hidden />,
              variant: 'danger',
              onClick: () => setLogoutDialogOpen(true),
              disabled: logoutMutation.isPending,
            },
          ]}
        />
      </section>

      {/* 19. 로그아웃 오류 배너 */}
      {logoutError && (
        <div
          role="alert"
          className="rounded-2xl border border-destructive/30 bg-destructive/5 p-4 text-sm font-medium text-destructive"
        >
          {logoutError}
        </div>
      )}

      {/* 20. 로그아웃 확인 다이얼로그 */}
      <ConfirmDialog
        open={logoutDialogOpen}
        title="로그아웃"
        description="정말 로그아웃하시겠습니까?"
        confirmText="로그아웃"
        cancelText="취소"
        variant="danger"
        onConfirm={() => {
          setLogoutDialogOpen(false)
          setLogoutError(null)
          logoutMutation.mutate()
        }}
        onCancel={() => setLogoutDialogOpen(false)}
      />
    </div>
  )
}
