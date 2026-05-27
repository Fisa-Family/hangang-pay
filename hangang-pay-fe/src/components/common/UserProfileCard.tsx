import type { SVGProps } from 'react'
import { formatPhoneNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { UserProfileResponse } from '@/api/user'

interface UserProfileCardProps {
  profile: UserProfileResponse
  onClick?: () => void
}

// Chevron Right Icon
function ChevronRightIcon({ className, ...props }: SVGProps<SVGSVGElement>) {
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
      {...props}
    >
      <path d="m9 18 6-6-6-6" />
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
        'flex w-full items-center gap-3 rounded-2xl border border-border bg-card p-4 text-left',
        onClick && 'transition-colors active:bg-surface'
      )}
    >
      {/* User Info */}
      <div className="min-w-0 flex-1">
        <p className="text-base font-semibold text-foreground">{profile.username}</p>
        <p className="mt-0.5 text-sm text-muted-foreground">{formattedPhone}</p>
        {profile.region && <p className="mt-0.5 text-sm text-muted-foreground">{profile.region}</p>}
      </div>

      {/* Chevron Icon */}
      <ChevronRightIcon className="h-5 w-5 shrink-0 text-muted-foreground" />
    </Component>
  )
}
