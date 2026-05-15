import type { ApiErrorCode } from './errorCodes'
import type { ApiSuccessCode } from './successCodes'

export type ApiStatus =
  | 'OK'
  | 'CREATED'
  | 'NO_CONTENT'
  | 'BAD_REQUEST'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'INTERNAL_SERVER_ERROR'
  | (string & {})

export type ApiResponseCode = ApiSuccessCode | ApiErrorCode | (string & {})

export interface ApiSuccessResponse<T = unknown> {
  isSuccess: true
  status: ApiStatus
  code: ApiResponseCode
  message: string
  result?: T
}

export interface ApiFailureResponse<T = unknown> {
  isSuccess: false
  status: ApiStatus
  code: ApiResponseCode
  message: string
  result?: T
}

export type ApiResponse<T = unknown, E = unknown> = ApiSuccessResponse<T> | ApiFailureResponse<E>

export type ValidationErrors = Record<string, string>
