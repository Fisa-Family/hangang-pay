import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  ConfirmDialog,
  HistoryEntryCard,
  SettingsMenuCard,
  UserProfileCard,
} from '@/components/common'
import { fetchUserProfile, type UserProfileResponse } from '@/api/user'
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

// 빌딩 아이콘 (계좌 관리)
function BuildingIcon({ className }: { className?: string }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden
    >
      <rect width="16" height="20" x="4" y="2" rx="2" />
      <path d="M9 22v-4h6v4" />
      <path d="M8 6h.01" />
      <path d="M16 6h.01" />
      <path d="M12 6h.01" />
      <path d="M12 10h.01" />
      <path d="M12 14h.01" />
      <path d="M16 10h.01" />
      <path d="M16 14h.01" />
      <path d="M8 10h.01" />
      <path d="M8 14h.01" />
    </svg>
  )
}

// 로그아웃 아이콘 (door-exit)
function LogoutIcon({ className }: { className?: string }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden
    >
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <polyline points="16 17 21 12 16 7" />
      <line x1="21" x2="9" y1="12" y2="12" />
    </svg>
  )
}

export function UserMyPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [logoutDialogOpen, setLogoutDialogOpen] = useState(false)
  const [logoutError, setLogoutError] = useState<string | null>(null)

  const profileQuery = useQuery({
    queryKey: ['user', 'profile'],
    queryFn: fetchUserProfile,
    retry: false,
  })

  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: () => {
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
    <div className="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto pb-4">
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

      <section className="flex flex-col gap-2">
        <p className="text-sm font-semibold text-muted-foreground">내역 확인</p>
        <HistoryEntryCard onClick={() => navigate('/mypage/payments')} />
      </section>

      <section className="flex flex-col gap-2">
        <p className="text-sm font-semibold text-muted-foreground">설정</p>
        <SettingsMenuCard
          items={[
            {
              id: 'accounts',
              label: '계좌 관리',
              description: '출금/충전 계좌 관리',
              icon: <BuildingIcon className="h-6 w-6 text-primary" />,
              onClick: () => navigate('/mypage/accounts'),
            },
          ]}
        />
        <SettingsMenuCard
          items={[
            {
              id: 'logout',
              label: '로그아웃',
              icon: <LogoutIcon className="h-6 w-6 text-destructive" />,
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
