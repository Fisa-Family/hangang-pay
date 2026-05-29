import { cn } from '@/lib/utils'

type BottomNavType = 'user' | 'merchant'

interface BottomNavProps {
  type: BottomNavType
  active: string
  onNavigate: (path: string) => void
  className?: string
}

interface BottomNavTab {
  id: string
  label: string
  path: string
  featured?: boolean
  disabled?: boolean
}

// 소비자 탭 (미구현 탭은 disabled)
const userTabs: BottomNavTab[] = [
  { id: 'home', label: '홈', path: '/home' },
  { id: 'payments', label: '결제내역', path: '/mypage/payments' },
  { id: 'scan', label: 'QR 스캔', path: '/pay/scan', featured: true },
  { id: 'merchant', label: '가맹점', path: '', disabled: true },
  { id: 'mypage', label: '마이페이지', path: '/mypage' },
]

// 가맹점 탭
const merchantTabs: BottomNavTab[] = [
  { id: 'home', label: '홈', path: '/merchant/home' },
  { id: 'payments', label: '내역', path: '/merchant/payments' },
  { id: 'mypage', label: '마이', path: '/merchant/mypage' },
]

// SVG 아이콘 (Lucide 계열 라인 스타일)
function HomeIcon({ className }: { className?: string }) {
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
      <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
      <polyline points="9 22 9 12 15 12 15 22" />
    </svg>
  )
}

function FileTextIcon({ className }: { className?: string }) {
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
      <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z" />
      <path d="M14 2v4a2 2 0 0 0 2 2h4" />
      <path d="M10 9H8" />
      <path d="M16 13H8" />
      <path d="M16 17H8" />
    </svg>
  )
}

function QrCodeIcon({ className }: { className?: string }) {
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
      <rect width="5" height="5" x="3" y="3" rx="1" />
      <rect width="5" height="5" x="16" y="3" rx="1" />
      <rect width="5" height="5" x="3" y="16" rx="1" />
      <path d="M21 16V21H16" />
      <path d="M9 9h.01" />
      <path d="M15 9h.01" />
      <path d="M9 15h.01" />
      <path d="M14 14h.01" />
      <path d="M18 18h.01" />
    </svg>
  )
}

function StoreIcon({ className }: { className?: string }) {
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
      <path d="m2 7 4.41-4.41A2 2 0 0 1 7.83 2h8.34a2 2 0 0 1 1.42.59L22 7" />
      <path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8" />
      <path d="M15 22v-4a2 2 0 0 0-2-2h-2a2 2 0 0 0-2 2v4" />
      <path d="M2 7h20" />
      <path d="M22 7a4 4 0 0 1-8 0 4 4 0 0 1-8 0 4 4 0 0 1-6 0" />
    </svg>
  )
}

function UserIcon({ className }: { className?: string }) {
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
      <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" />
      <circle cx="12" cy="7" r="4" />
    </svg>
  )
}

// 탭 아이디별 아이콘 매핑
const tabIcons: Record<string, (className?: string) => React.ReactNode> = {
  home: (cls) => <HomeIcon className={cls} />,
  payments: (cls) => <FileTextIcon className={cls} />,
  scan: (cls) => <QrCodeIcon className={cls} />,
  merchant: (cls) => <StoreIcon className={cls} />,
  mypage: (cls) => <UserIcon className={cls} />,
}

export function BottomNav({ type, active, onNavigate, className }: BottomNavProps) {
  const tabs = type === 'user' ? userTabs : merchantTabs

  return (
    <nav
      className={cn(
        'absolute inset-x-0 bottom-0 z-20 border-t border-border bg-card px-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-2',
        className
      )}
    >
      <div
        className="grid items-end gap-1"
        style={{ gridTemplateColumns: `repeat(${tabs.length}, minmax(0, 1fr))` }}
      >
        {tabs.map((tab) => {
          const isActive = active === tab.id
          const icon = tabIcons[tab.id]

          return (
            <button
              key={tab.id}
              type="button"
              disabled={tab.disabled}
              onClick={() => onNavigate(tab.path)}
              className={cn(
                'flex min-h-12 flex-col items-center justify-center gap-1 rounded-lg px-1 text-xs font-semibold text-muted-foreground transition-colors',
                isActive && !tab.featured && 'text-primary',
                tab.featured && 'bg-primary text-primary-foreground shadow-sm shadow-primary/20',
                tab.disabled && 'opacity-40'
              )}
            >
              {icon?.('w-[22px] h-[22px]')}
              <span className="truncate">{tab.label}</span>
            </button>
          )
        })}
      </div>
    </nav>
  )
}
