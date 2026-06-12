import { useState } from 'react'
import { Building2, Bell, Headphones, Info, LogOut } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  ConfirmDialog,
  HistoryEntryCard,
  SettingsMenuCard,
  UserProfileCard,
} from '@/components/common'
import { useUserProfile, type UserProfileResponse } from '@/api/user'
import { logout } from '@/api/auth'
import { ApiError } from '@/api/client'
import { apiErrorMessages, isApiErrorCode } from '@/api/errorCodes'

const EMPTY_PROFILE: UserProfileResponse = {
  userId: 0,
  partyId: 0,
  username: '-',
  phoneNumber: '',
  birthDate: null,
  region: null,
}

const API_SPEC = {
  MY_001: { id: 'MY-001' },
  AUTH_LOGOUT: { id: 'AUTH-LOGOUT' },
} as const

function buildErrorMessage(spec: (typeof API_SPEC)[keyof typeof API_SPEC], error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code && isApiErrorCode(error.code)) return apiErrorMessages[error.code]
    return error.message
  }

  return `${spec.id} 요청에 실패했습니다. 네트워크 연결을 확인해 주세요.`
}

const noop = () => {}

export function UserMyPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [logoutDialogOpen, setLogoutDialogOpen] = useState(false)
  const [logoutError, setLogoutError] = useState<string | null>(null)

  const profileQuery = useUserProfile()

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
    ? buildErrorMessage(API_SPEC.MY_001, profileQuery.error)
    : null

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto pb-4">
      {profileErrorMessage && (
        <div
          role="alert"
          className="rounded-2xl border border-destructive/30 bg-destructive/5 p-4 text-sm font-medium text-destructive"
        >
          {profileErrorMessage}
        </div>
      )}

      {profileQuery.isLoading ? (
        <div
          aria-hidden
          className="h-[88px] animate-pulse rounded-2xl border border-border bg-muted/40"
        />
      ) : (
        <UserProfileCard profile={profileQuery.data ?? EMPTY_PROFILE} />
      )}

      <section className="mt-6 flex flex-col gap-2">
        <HistoryEntryCard onClick={() => navigate('/mypage/payments')} />
      </section>

      <section className="flex flex-col gap-2">
        <SettingsMenuCard
          items={[
            {
              id: 'accounts',
              label: '계좌 관리',
              icon: <Building2 className="h-6 w-6 text-primary" aria-hidden />,
              onClick: () => navigate('/mypage/accounts'),
            },
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

      {logoutError && (
        <div
          role="alert"
          className="rounded-2xl border border-destructive/30 bg-destructive/5 p-4 text-sm font-medium text-destructive"
        >
          {logoutError}
        </div>
      )}

      <ConfirmDialog
        open={logoutDialogOpen}
        title="로그아웃 하시겠어요?"
        description="다시 이용하시려면 로그인해야 합니다."
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
