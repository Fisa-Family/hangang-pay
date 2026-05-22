import { Component } from 'react'
import type { ReactNode } from 'react'

interface Props {
  children: ReactNode
  // 에러 발생 시 표시할 대체 UI, 기본값은 인라인 에러 메시지
  fallback?: ReactNode
}

interface State {
  hasError: boolean
  message: string
}

// useSuspenseQuery 등에서 throw된 에러를 컴포넌트 단위로 포착
export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, message: '' }

  static getDerivedStateFromError(error: unknown): State {
    const message =
      error instanceof Error ? error.message : '알 수 없는 오류가 발생했습니다.'
    return { hasError: true, message }
  }

  render() {
    if (this.state.hasError) {
      return (
        this.props.fallback ?? (
          <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm font-medium text-destructive">
            {this.state.message}
          </div>
        )
      )
    }
    return this.props.children
  }
}
