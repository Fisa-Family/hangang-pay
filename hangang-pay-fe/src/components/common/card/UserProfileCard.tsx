import { ChevronRight, User } from 'lucide-react'
import { formatPhoneNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { UserProfileResponse } from '@/api/user'

interface UserProfileCardProps {
  profile: UserProfileResponse
  onClick?: () => void
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
        <User className="h-7 w-7" aria-hidden />
      </div>

      {/* User Info */}
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold text-foreground">{profile.username}</p>
        <p className="mt-0.5 text-xs text-muted-foreground">{formattedPhone}</p>
        {profile.region && <p className="mt-0.5 text-xs text-muted-foreground">{profile.region}</p>}
      </div>

      {/* Chevron Icon */}
      <ChevronRight className="h-5 w-5 shrink-0 text-muted-foreground" aria-hidden />
    </Component>
  )
}
