import { useState } from 'react'
import { Building2, LogOut } from 'lucide-react'
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

// 7. 가맹점 정보 필드 행 (label + value)
function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-0.5 border-b border-border/40 py-3 last:border-0">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="text-sm font-semibold text-foreground">{value || '-'}</span>
    </div>
  )
}

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
          className="h-52 animate-pulse rounded-2xl border border-border bg-muted/40"
        />
      ) : (
        <section className="rounded-2xl border border-border/40 bg-card px-4 py-3 shadow-sm">
          {/* 15. 상호명 헤더 */}
          <h2 className="mb-2 text-base font-bold text-foreground">{data.merchantName}</h2>
          {/* 16. 사업자 정보 필드 목록 */}
          <InfoRow label="사업자등록번호" value={data.businessNumber} />
          <InfoRow label="대표자명" value={data.ownerName} />
          <InfoRow label="사업장 주소" value={data.address} />
          <InfoRow label="전화번호" value={formatPhoneNumber(data.phoneNumber)} />
        </section>
      )}

      {/* 17. 설정 메뉴 — 정산 계좌 관리 */}
      <section className="mt-1 flex flex-col gap-2">
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
