import { formatWon } from '@/lib/format'

interface ProcessingViewProps {
  title: string
  amount?: number
  caption?: string
}

export function ProcessingView({ title, amount, caption }: ProcessingViewProps) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-6">
      <div
        className="h-18 w-18 animate-spin rounded-full border-4 border-gray-200 border-t-blue-500"
        style={{ animationDuration: '0.9s' }}
      />

      <div className="flex flex-col items-center gap-2 text-center">
        <p className="text-base font-medium text-muted-foreground">{title}</p>
        {amount != null && (
          <p className="text-[28px] font-bold tabular-nums text-foreground">{formatWon(amount)}</p>
        )}
        {caption && <p className="text-sm text-muted-foreground">{caption}</p>}
      </div>
    </div>
  )
}
