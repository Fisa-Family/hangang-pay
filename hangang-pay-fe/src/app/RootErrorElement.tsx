import { useRouteError } from 'react-router-dom'
import { AppShell } from '@/components/common'

// 라우트 레벨 에러 fallback (예상치 못한 에러 전체 포착)
export function RootErrorElement() {
  const error = useRouteError()
  const message = error instanceof Error ? error.message : '알 수 없는 오류가 발생했습니다.'

  return (
    <AppShell>
      <section className="flex h-full flex-col items-center justify-center gap-4 text-center">
        <p className="text-sm font-semibold text-destructive">{message}</p>
        <button
          type="button"
          className="text-sm font-semibold text-primary"
          onClick={() => window.location.replace('/')}
        >
          홈으로 돌아가기
        </button>
      </section>
    </AppShell>
  )
}
