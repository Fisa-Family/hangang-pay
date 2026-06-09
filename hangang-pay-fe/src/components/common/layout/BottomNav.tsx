import { Home, FileText, QrCode, Store, User } from 'lucide-react'
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
  { id: 'scan', label: 'QR 결제', path: '/pay/scan', featured: true },
  { id: 'merchant', label: '가맹점', path: '', disabled: true },
  { id: 'mypage', label: '마이페이지', path: '/mypage' },
]

const merchantTabs: BottomNavTab[] = [
  { id: 'home', label: '홈', path: '/merchant/home' },
  { id: 'payments', label: '내역', path: '/merchant/payments' },
  { id: 'mypage', label: '마이', path: '/merchant/mypage' },
]

const tabIcons: Record<string, (className?: string) => React.ReactNode> = {
  home: (cls) => <Home className={cls} aria-hidden />,
  payments: (cls) => <FileText className={cls} aria-hidden />,
  scan: (cls) => <QrCode className={cls} aria-hidden />,
  merchant: (cls) => <Store className={cls} aria-hidden />,
  mypage: (cls) => <User className={cls} aria-hidden />,
}

export function BottomNav({ type, active, onNavigate, className }: BottomNavProps) {
  const tabs = type === 'user' ? userTabs : merchantTabs

  return (
    <nav
      className={cn(
        'absolute inset-x-0 bottom-0 z-20 bg-card pb-[max(0.75rem,env(safe-area-inset-bottom))] shadow-[0_-1px_0_rgba(0,0,0,0.08)]',
        className
      )}
    >
      <div
        className="grid items-stretch"
        style={{ gridTemplateColumns: `repeat(${tabs.length}, minmax(0, 1fr))` }}
      >
        {tabs.map((tab) => {
          const isActive = active === tab.id
          const icon = tabIcons[tab.id]

          if (tab.featured) {
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => onNavigate(tab.path)}
                className="flex flex-col items-center -mt-4 gap-1.5 pb-1"
              >
                <div className="flex h-12 w-12 items-center justify-center rounded-full bg-primary ring-4 ring-card shadow-lg shadow-primary/25">
                  {icon?.('w-[22px] h-[22px] text-white')}
                </div>
                <span className="text-[10px] font-bold leading-none text-primary">{tab.label}</span>
              </button>
            )
          }

          return (
            <button
              key={tab.id}
              type="button"
              disabled={tab.disabled}
              onClick={() => onNavigate(tab.path)}
              className={cn(
                'flex flex-col items-center pb-1 transition-colors',
                isActive ? 'text-primary' : 'text-muted-foreground',
                tab.disabled && 'opacity-40'
              )}
            >
              <div
                className={cn(
                  'mb-2 h-0.75 w-8 rounded-full transition-all duration-200',
                  isActive ? 'bg-primary' : 'bg-transparent'
                )}
              />
              {icon?.('w-[22px] h-[22px]')}
              <span className="mt-1 text-[10px] font-semibold leading-none">{tab.label}</span>
            </button>
          )
        })}
      </div>
    </nav>
  )
}
