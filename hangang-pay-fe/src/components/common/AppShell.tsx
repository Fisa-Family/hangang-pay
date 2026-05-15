import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

interface AppShellProps {
  children: ReactNode
  bottomNav?: ReactNode
  fullBleed?: boolean
  className?: string
}

export function AppShell({ children, bottomNav, fullBleed = false, className }: AppShellProps) {
  return (
    <div className={cn('relative h-svh overflow-hidden bg-background text-foreground', className)}>
      <main
        className={cn(
          'flex h-full min-h-0 flex-col overflow-hidden',
          bottomNav && 'pb-24',
          fullBleed ? 'p-0' : 'px-5 py-4'
        )}
      >
        {children}
      </main>
      {bottomNav}
    </div>
  )
}
