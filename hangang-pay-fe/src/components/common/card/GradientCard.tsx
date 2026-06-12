import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

interface GradientCardProps {
  children: ReactNode
  className?: string
}

/** 흰색에서 연한 블루로 자연스럽게 이어지는 좌→우 그라데이션 카드 (잔액/대시보드/금액 요약 공통) */
export function GradientCard({ children, className }: GradientCardProps) {
  return (
    <section
      className={cn('relative overflow-hidden rounded-2xl shadow-sm', className)}
      style={{
        background:
          'linear-gradient(90deg, var(--card) 55%, color-mix(in srgb, var(--card) 45%, var(--accent)) 82%, var(--accent) 100%)',
      }}
    >
      {children}
    </section>
  )
}
