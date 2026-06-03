const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '') + '/api/v1'

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

export async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response

  try {
    response = await fetch(`${BASE_URL}${path}`, {
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', ...options?.headers },
      ...options,
    })
  } catch {
    throw new ApiError('서버에 연결할 수 없습니다.', 0)
  }

  const data: { isSuccess: boolean; message?: string; code?: string; result?: T } | null =
    await response.json().catch(() => null)

  const isAppError = data != null && data.isSuccess === false
  if (response.status === 401 && !isAppError) {
    localStorage.removeItem('role')
    window.location.replace('/login')
    throw new ApiError(data?.message ?? '인증이 필요합니다.', 401, data?.code)
  }

  if (data === null) {
    throw new ApiError(`HTTP ${response.status}`, response.status)
  }

  if (!data.isSuccess) {
    throw new ApiError(data.message ?? '요청에 실패했습니다.', response.status, data.code)
  }

  return data.result as T
}
