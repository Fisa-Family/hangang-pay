export const ApiSuccessCode = {
  COMMON_OK: 'COMMON_OK',
  COMMON_CREATED: 'COMMON_CREATED',
  COMMON_NO_CONTENT: 'COMMON_NO_CONTENT',

  CHARGE_HISTORIES_RETRIEVED: 'CHARGE_HISTORIES_RETRIEVED',
} as const

export type ApiSuccessCode = (typeof ApiSuccessCode)[keyof typeof ApiSuccessCode]

const knownApiSuccessCodes = new Set<string>(Object.values(ApiSuccessCode))

export function isApiSuccessCode(code: string): code is ApiSuccessCode {
  return knownApiSuccessCodes.has(code)
}
