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

const userTabs: BottomNavTab[] = [
  { id: 'home', label: '홈', path: '/home' },
  { id: 'payments', label: '결제내역', path: '/mypage/payments' },
  { id: 'scan', label: 'QR 스캔', path: '/pay/scan', featured: true },
  { id: 'merchant', label: '가맹점', path: '', disabled: true },
  { id: 'mypage', label: '마이페이지', path: '/mypage' },
]

const merchantTabs: BottomNavTab[] = [
  { id: 'home', label: '홈', path: '/merchant/home' },
  { id: 'payments', label: '내역', path: '/merchant/payments' },
  { id: 'mypage', label: '마이', path: '/merchant/mypage' },
]

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
          return (
            <button
              key={tab.id}
              type="button"
              disabled={tab.disabled}
              onClick={() => onNavigate(tab.path)}
              className={cn(
                'flex min-h-12 flex-col items-center justify-center rounded-lg px-1 text-xs font-semibold text-muted-foreground transition-colors',
                isActive && 'text-primary',
                tab.featured && 'bg-primary text-primary-foreground shadow-sm shadow-primary/20',
                tab.disabled && 'opacity-40'
              )}
            >
              <span className="text-lg leading-none">{tab.featured ? '□' : '•'}</span>
              <span className="mt-1 truncate">{tab.label}</span>
            </button>
          )
        })}
      </div>
    </nav>
  )
}
