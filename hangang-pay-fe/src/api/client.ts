// 베이스 URL, env 미설정 시 로컬 기본값
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'

// HTTP 상태 코드와 BE 에러 코드를 담는 커스텀 에러
export class ApiError extends Error {
  public readonly status: number
  public readonly code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

// 세션 쿠키 포함 공통 fetch 래퍼
export async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response

  // 네트워크 단절
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', ...options?.headers },
      ...options,
    })
  } catch {
    throw new ApiError('서버에 연결할 수 없습니다.', 0)
  }

  // JSON 파싱 실패
  let data: { isSuccess: boolean; message?: string; code?: string; result?: T }
  try {
    data = await response.json()
  } catch {
    throw new ApiError(`HTTP ${response.status}`, response.status)
  }

  if (!data.isSuccess) {
    throw new ApiError(data.message ?? '요청에 실패했습니다.', response.status, data.code)
  }

  return data.result as T
}
