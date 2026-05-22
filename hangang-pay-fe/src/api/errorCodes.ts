export const ApiErrorCode = {
  COMMON_BAD_REQUEST: 'COMMON_BAD_REQUEST',
  COMMON_UNAUTHORIZED: 'COMMON_UNAUTHORIZED',
  COMMON_FORBIDDEN: 'COMMON_FORBIDDEN',
  COMMON_NOT_FOUND: 'COMMON_NOT_FOUND',
  COMMON_INTERNAL_SERVER_ERROR: 'COMMON_INTERNAL_SERVER_ERROR',
  COMMON_VALIDATION_FAILED: 'COMMON_VALIDATION_FAILED',

  MAX_ACCOUNT_EXCEEDED: 'MAX_ACCOUNT_EXCEEDED',
  DUPLICATE_ACCOUNT: 'DUPLICATE_ACCOUNT',
  BANK_ACCOUNT_NOT_FOUND: 'BANK_ACCOUNT_NOT_FOUND',
  PRIMARY_ACCOUNT_DELETE: 'PRIMARY_ACCOUNT_DELETE',
  LAST_ACCOUNT_DELETE: 'LAST_ACCOUNT_DELETE',
  ACCOUNT_NOT_FOUND: 'ACCOUNT_NOT_FOUND',

  USER_NOT_FOUND: 'USER_NOT_FOUND',
} as const

export type ApiErrorCode = (typeof ApiErrorCode)[keyof typeof ApiErrorCode]

export const apiErrorMessages: Record<ApiErrorCode, string> = {
  [ApiErrorCode.COMMON_BAD_REQUEST]: '잘못된 요청입니다',
  [ApiErrorCode.COMMON_UNAUTHORIZED]: '인증이 필요합니다',
  [ApiErrorCode.COMMON_FORBIDDEN]: '접근이 금지되었습니다',
  [ApiErrorCode.COMMON_NOT_FOUND]: '요청한 자원을 찾을 수 없습니다',
  [ApiErrorCode.COMMON_INTERNAL_SERVER_ERROR]: '서버 내부 오류가 발생했습니다',
  [ApiErrorCode.COMMON_VALIDATION_FAILED]: '잘못된 파라미터 입니다.',

  [ApiErrorCode.MAX_ACCOUNT_EXCEEDED]: '계좌는 최대 3개까지 등록 가능합니다.',
  [ApiErrorCode.DUPLICATE_ACCOUNT]: '이미 등록된 계좌입니다.',
  [ApiErrorCode.BANK_ACCOUNT_NOT_FOUND]: '존재하지 않는 계좌입니다.',
  [ApiErrorCode.PRIMARY_ACCOUNT_DELETE]: '주거래 계좌는 삭제할 수 없습니다.',
  [ApiErrorCode.LAST_ACCOUNT_DELETE]: '계좌는 최소 1개 이상 유지해야 합니다.',
  [ApiErrorCode.ACCOUNT_NOT_FOUND]: '계좌를 찾을 수 없습니다.',

  [ApiErrorCode.USER_NOT_FOUND]: '사용자를 찾을 수 없습니다',
}

const knownApiErrorCodes = new Set<string>(Object.values(ApiErrorCode))

export function isApiErrorCode(code: string): code is ApiErrorCode {
  return knownApiErrorCodes.has(code)
}

// 화면별 HTTP 상태 코드에서 사용자 메시지로 매핑
export const apiUserErrorMessages: Record<string, Record<number, string>> = {
  'WALLET-001': {
    0:   '잔액 정보를 불러올 수 없습니다. 네트워크 연결을 확인해 주세요.',
    400: '잔액 조회 요청에 문제가 있습니다.',
    401: '잔액을 조회하려면 로그인이 필요합니다.',
    403: '잔액 조회 권한이 없습니다.',
    404: '잔액 조회 서비스를 현재 이용할 수 없습니다.',
    500: '잠시 후 다시 시도해 주세요.',
  },
  'MY-002': {
    0:   '거래 내역을 불러올 수 없습니다. 네트워크 연결을 확인해 주세요.',
    400: '거래 내역 조회 요청에 문제가 있습니다.',
    401: '거래 내역을 조회하려면 로그인이 필요합니다.',
    403: '거래 내역 조회 권한이 없습니다.',
    404: '거래 내역 서비스를 현재 이용할 수 없습니다.',
    500: '잠시 후 다시 시도해 주세요.',
  },
}
