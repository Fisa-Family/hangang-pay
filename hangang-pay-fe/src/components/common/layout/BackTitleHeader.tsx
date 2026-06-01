import { cn } from '@/lib/utils'

interface BackTitleHeaderProps {
  title: string
  onBack: () => void
  className?: string
}

export function BackTitleHeader({ title, onBack, className }: BackTitleHeaderProps) {
  return (
    <header className={cn('grid h-12 grid-cols-[44px_1fr_44px] items-center', className)}>
      <button
        type="button"
        aria-label="뒤로가기"
        onClick={onBack}
        className="-ml-2 flex size-9 items-center justify-center rounded-lg text-foreground hover:bg-muted"
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="h-6 w-6"
          aria-hidden
        >
          <path d="m15 18-6-6 6-6" />
        </svg>
      </button>
      <h1 className="truncate text-center text-lg font-semibold">{title}</h1>
    </header>
  )
}
