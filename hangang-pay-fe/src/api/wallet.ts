import { apiFetch } from './client'

// 잔액 조회 응답
export interface WalletBalanceResult {
  balance: number
}

// 잔액 조회
export function fetchWalletBalance(): Promise<WalletBalanceResult> {
  return apiFetch<WalletBalanceResult>('/wallet/balance')
}
