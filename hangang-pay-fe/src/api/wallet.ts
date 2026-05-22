import { apiFetch } from './client'

// WALLET-001 잔액 조회 응답
export interface WalletBalanceResult {
  balance: number
}

// WALLET-001 잔액 조회
export function fetchWalletBalance(): Promise<WalletBalanceResult> {
  return apiFetch<WalletBalanceResult>('/wallet/balance')
}
