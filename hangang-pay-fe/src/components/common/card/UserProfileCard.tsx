import { formatPhoneNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { UserProfileResponse } from '@/api/user'
import { ChevronRightIcon } from '../icons'

interface UserProfileCardProps {
  profile: UserProfileResponse
  onClick?: () => void
}

// 사용자 실루엣 (얼굴 + 몸통)
function UserAvatarIcon({ className }: { className?: string }) {
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
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
      <circle cx="12" cy="7" r="4" />
    </svg>
  )
}

export function UserProfileCard({ profile, onClick }: UserProfileCardProps) {
  const Component = onClick ? 'button' : 'div'
  const formattedPhone = formatPhoneNumber(profile.phoneNumber)

  return (
    <Component
      type={onClick ? 'button' : undefined}
      onClick={onClick}
      className={cn(
        'flex w-full items-center gap-3 rounded-2xl border border-border/40 bg-card p-4 text-left shadow-sm',
        onClick && 'transition-colors active:bg-surface'
      )}
    >
      {/* Avatar */}
      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground">
        <UserAvatarIcon className="h-7 w-7" />
      </div>

      {/* User Info */}
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold text-foreground">{profile.username}</p>
        <p className="mt-0.5 text-xs text-muted-foreground">{formattedPhone}</p>
        {profile.region && <p className="mt-0.5 text-xs text-muted-foreground">{profile.region}</p>}
      </div>

      {/* Chevron Icon */}
      <ChevronRightIcon className="h-5 w-5 shrink-0 text-muted-foreground" />
    </Component>
  )
}
